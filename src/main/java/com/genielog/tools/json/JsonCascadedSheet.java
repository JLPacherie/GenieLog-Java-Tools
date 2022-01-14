package com.genielog.tools.json;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.genielog.tools.JsonUtils;
import com.genielog.tools.StringUtils;

/**
 * 
 * The definition of a path is the JsonNode value for the given path in the first sheet denifing it.
 * The resolved value is the value obtained by replacing ocurrences of ${path} with the value of that path 
 * 
 */
public class JsonCascadedSheet {

	protected Logger _logger;

	// Shared with parent sheets
	private Map<String, Object> _cachedEntries = null;

	// Shared with parent sheets
	private List<File> _allLibrariesDir = null;

	private List<JsonCascadedSheet> _allSheets = new ArrayList<>();
	private List<File> _allSheetFiles = new ArrayList<>();

	private JsonCascadedSheet childSheet = null;

	private JsonNode _masterSheet = null;
	private File _masterFile = null;

	// ******************************************************************************************************************
	//
	// ******************************************************************************************************************

	/** Create a Sheet imported from another one (the child sheet). */
	public JsonCascadedSheet(String path, JsonCascadedSheet child) {
		_logger = LogManager.getLogger(this.getClass());

		_logger.debug("Initializing a Cascaded Json Sheet import {} from sheet {}",
				path,
				child.getFile().getName());

		childSheet = child;
		if (!load(path)) {
			_logger.error("Unable to load Json Cascaded Sheet {}", path);
			clear();
		}
	}

	/** Create a root Sheet imported from a path with a list of library dirs for includes. */
	public JsonCascadedSheet(String path, String... libs) {

		_logger = LogManager.getLogger(this.getClass());
		_logger.debug("Initializing a Cascaded Json Sheet from a path and a list of libs");

		_cachedEntries = new HashMap<>();
		_allLibrariesDir = new ArrayList<>();

		for (String lib : libs) {
			File dir = new File(lib);
			if (dir.isDirectory()) {
				_allLibrariesDir.add(dir);
			}
		}

		if (!load(path)) {
			_logger.error("Unable to load Json Cascaded Sheet {}", path);
			clear();
		}
	}

	// ******************************************************************************************************************
	//
	// ******************************************************************************************************************

	public void clear() {
		if (_cachedEntries != null) {
			_cachedEntries.clear();
		}

		if (_allLibrariesDir != null) {
			_allLibrariesDir.clear();
		}

		_allSheets.clear();
		_allSheetFiles.clear();

		_masterFile = null;
		_masterSheet = null;
	}

	public boolean isValid() {
		return (_masterFile != null) && (_masterSheet != null);
	}

	public File getFile() {
		return _masterFile;
	}
	//
	// ******************************************************************************************************************
	//

	/** Returns the list of libraries for this sheet or the one of its child sheet) */
	private List<File> getLibraries() {
		if (_allLibrariesDir != null) {
			return _allLibrariesDir;
		}
		return childSheet.getLibraries();
	}

	public Stream<File> getAllIncludedFiles() {
		return Stream.concat(
				_allSheets.stream().flatMap(JsonCascadedSheet::getAllIncludedFiles),
				Stream.of(getFile()));
	}

	/** Returns a stream of the Included files in this sheet. */
	public Stream<File> getIncludedFiles() {
		return _allSheetFiles.stream();
	}

	/** Seach for the file referenced by a path in library folders (as in an include section). */
	protected File includeLookUp(String path) {
		File result = new File(path);

		if (!result.exists() && (_masterFile != null)) {
			result = new File(_masterFile.getParent() + File.separator + path);
		}

		if (!result.exists()) {
			// _logger.debug("Json Cascaded Sheet not found at {}", result.getAbsolutePath());
			result = getLibraries().stream()
					.map(dir -> new File(dir.getAbsolutePath() + File.separator + path))
					.filter(File::exists)
					.findFirst()
					.orElse(null);
		}

		if (result != null) {
			_logger.debug("Json Cascaded Sheet found at {}", result.getAbsolutePath());
		} else {
			_logger.error("Json Cascaded Sheet not found {}", path);
		}
		return result;
	}

	//
	// ******************************************************************************************************************
	//

	public boolean load(String pathname) {
		File srcFile = includeLookUp(pathname);
		boolean result = srcFile != null;
		if (srcFile != null) {
			_masterSheet = JsonUtils.getJsonNodeFromFile(srcFile.getAbsolutePath());
			if (_masterSheet != null) {
				_masterFile = srcFile;
				result = resolveIncludes();
				if (!result) {
					throw new IllegalArgumentException("Unable to resolve includes from " + srcFile.getPath());
				}
			} else {
				throw new IllegalArgumentException("Unable to parse JSON from " + srcFile.getPath());
			}
		} else {
			throw new IllegalArgumentException("Unable to find JSON file at " + pathname);
		}
		return result;
	}

	//
	// ******************************************************************************************************************
	//

	/** Loads all referenced parent sheets from the include section of the JSON source. */
	private boolean resolveIncludes() {

		boolean result = true;

		if (_masterSheet == null) {
			throw new IllegalArgumentException("Bad (null) master sheet.");
		}

		_allSheets.clear();
		_allSheetFiles.clear();

		JsonNode includes = JsonUtils.getJsonByPath(_masterSheet, ".includes");
		if (includes != null) {
			if (includes.isArray()) {
				Iterator<JsonNode> incIter = includes.iterator();
				while (incIter.hasNext()) {
					JsonNode incNode = incIter.next();
					if (incNode.isTextual()) {

						String incPath = incNode.asText();
						File incFile = includeLookUp(incPath);

						if (incFile != null) {

							_logger.debug("Included file found at {}", incFile.getPath());

							JsonCascadedSheet incSheet = new JsonCascadedSheet(incFile.getPath(), this);

							if (incSheet.isValid()) {
								_allSheets.add(incSheet);
								_allSheetFiles.add(incFile);
							} else {
								result = false;
								_logger.error("Unable to read JSON sheet from file {}", incFile.getAbsolutePath());
							}
						} else {
							result = false;
							_logger.error("Included file not found {}", incPath);
						}
					} else {
						result = false;
						_logger.error("Bad format, __includes should contain only strings which are included JSON file paths");
					}
				}
			} else {
				result = false;
				_logger.error("Bad format, __includes is a reserved keyword for the array of included JSON files");
			}
		} else {
			_logger.debug("No included sheets found in {}", _masterFile.getPath());
		}

		return result;
	}

	// ******************************************************************************************************************
	// Cache management.
	//
	// The cache allows both to manually overwrite value defined in the JSON files (using the setter) and to cache the
	// resolved values once find in the JSON file or its included JSON files.
	// ******************************************************************************************************************

	private Map<String, Object> getCache() {
		if (_cachedEntries != null) {
			return _cachedEntries;
		}
		return childSheet.getCache();
	}

	/** Returns the cached values (eg. all those requested so far) */
	public Stream<Map.Entry<String, Object>> getAllCached() {
		return getCache().entrySet().stream();
	}

	// ******************************************************************************************************************
	// Getters and Setters for JSON paths and their values.
	// ******************************************************************************************************************

	/** Overwrite any value defined in the JSON files. */
	public void set(String path, Object value) {
		if (getCache().containsKey(path) && !Objects.equals(getCache().get(path), value)) {
			_logger.debug("Override a cached value : {} with {} while was cached '{}'", path, value, getCache().get(path));
		}
		getCache().put(path, value);
	}

	//
	// ******************************************************************************************************************
	//

	/** Retrieve the resolved definition of a path, if not defined, returns the default value. */
	public Object get(String path, Object dflt) {
		Object node = get(path);
		if (node == null) {
			return dflt;
		}
		return node;
	}

	public Object get(String path) {
		return getFromNode(null, path);
	}

	//
	// ******************************************************************************************************************
	//

	public String getAsText(String path) {
		Object result = get(path);
		if (result instanceof String) {
			return (String) result;
		} else if (result instanceof Number) {
			return ((Number) result).toString();
		} else if (result instanceof JsonNode) {
			return JsonUtils.getJsonElement((JsonNode) result, "");
		}
		return null;
	}

	//
	// ******************************************************************************************************************
	//

	/** The value of the given path as an Integer (even if a string parsed as a Integer, or null if not possible. */
	public Integer getAsInteger(String path) {
		Object result = get(path);
		if (result instanceof String) {
			try {
				result = Integer.parseInt((String) result);
			} catch (NumberFormatException e) {
				_logger.error("Path value not an integer {}", result);
			}
		}
		return (result instanceof Integer) ? (Integer) result : null;
	}

	//
	// ******************************************************************************************************************
	//

	public Double getAsDouble(String path) {
		Object result = get(path);
		if (result instanceof String) {
			try {
				result = Double.parseDouble((String) result);
			} catch (NumberFormatException e) {
				_logger.error("Path value not a double {}", result);
			}
		}
		return (result instanceof Double) ? (Double) result : null;
	}

	//
	// ******************************************************************************************************************
	//

	/** Returns the resolved deinition of a path from a given root. */
	public Object getFromNode(JsonNode from, String path) {
		Object result = null;

		if ((path == null) || (path.isEmpty())) {
			throw new IllegalArgumentException("Invalid undefined path");
		}

		// First look into the cached entries.
		result = getCache().get(path);

		// If not found in the cache, then search it in the current and parent sheets
		if (result == null) {
			result = getDefinition(from, path);

			// If found, then udpate the cache
			if (result != null) {
				result = resolve(result);
				getCache().put(path, result);
			}
		}

		return result;

	}

	/** Attempt to resolve any object (a string, an array, ...) */
	public Object resolve(Object value) {
		Object result = value;
		if (value instanceof String) {
			result = resolve((String) value);
		} else if (value instanceof Object[]) {
			result = new Object[((Object[]) value).length];
			for (int i = 0; i < ((Object[]) value).length; i++) {
				Object obj = ((Object[]) value)[i];
				((Object[]) result)[i] = resolve(obj);
			}
		}
		return result;
	}

	/** Resolve references ${path} in string. */
	public String resolve(String value) {
		String result = value;
		if (result != null) {
			result = StringUtils.resolve(value, "${", "}", this::getAsText);
		}
		return result;
	}

	public boolean isResolved(String value) {
		return StringUtils.getAllReferences(value, "${", "}").length == 0;
	}

	//
	// ******************************************************************************************************************
	//

	/** Convert a JsonNode into a plain Java Object if possible */
	public Object getObjectFromNode(JsonNode node) {
		Object result = node;
		if (node != null) {
			if (node.isTextual()) {
				return node.asText();
			}
			if (node.isInt()) {
				return node.asInt();
			}
			if (node.isDouble()) {
				return node.asDouble();
			}
			if (node.isBoolean()) {
				return node.asBoolean();
			}
			if (node.isArray()) {
				List<Object> list = new ArrayList<>();
				node.forEach(listNode -> {
					list.add(getObjectFromNode(listNode));
				});
				return list.toArray();
			}
		}
		return result;
	}

	//
	// ******************************************************************************************************************
	//

	public Object getDefinition(String path) {
		return getDefinition(null, path);
	}

	public Object getDefinition(JsonNode root, String path) {

		if (!isValid()) {
			throw new IllegalStateException("Cascaded Json Sheet not initialized?");
		}

		Object result = null;

		if (root == null) {
			JsonCascadedSheet ownerSheet = getDefinitionLocation(path);
			if (ownerSheet != null) {
				root = ownerSheet._masterSheet;
			}
		}

		if (root != null) {
			JsonNode node = JsonUtils.getJsonByPath(root, path);
			// If a node is found in the current Json sheet then return the converted
			// values for basic types, or return a JsonNode
			return getObjectFromNode(node);
		}

		return null;
	}

	/** Returns the first Sheet defining the given path.
	 *  <p>The lookup starts from from this one, then each included Json sheet starting from
	 *  the last included one up to the first.
	 */
	public JsonCascadedSheet getDefinitionLocation(String path) {
		JsonCascadedSheet result = JsonUtils.getJsonByPath(_masterSheet, path) != null ? this : null;

		int iSheet = _allSheets.size() - 1;
		while ((result == null) && (iSheet >= 0)) {
			result = _allSheets.get(iSheet).getDefinitionLocation(path);
			iSheet--;
		}

		return result;
	}

	public Stream<JsonCascadedSheet> getAllDefinitionLocation(String path) {

		return Stream.concat(
				_allSheets.stream().flatMap(sheet -> sheet.getAllDefinitionLocation(path)),
				JsonUtils.getJsonByPath(_masterSheet, path) != null ? Stream.of(this) : Stream.empty());
	}

}
