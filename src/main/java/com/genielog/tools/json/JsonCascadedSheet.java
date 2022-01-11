package com.genielog.tools.json;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.genielog.tools.JsonUtils;

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

	public JsonCascadedSheet(String path, JsonCascadedSheet child) {
		_logger = LogManager.getLogger(this.getClass());
		_logger.debug("Initializing a Cascaded Json Sheet from a path and a child sheet");
		childSheet = child;
		if (!load(path)) {
			_logger.error("Unable to load Json Cascaded Sheet {}",path);
			clear();
		}
	}

	public JsonCascadedSheet(String path, String... libs) {
		System.out.println("XXXX");

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
			_logger.error("Unable to load Json Cascaded Sheet {}",path);
			clear();
		}
	}
	
	public String message() {
		return "Hello";
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

	private List<File> getLibraries() {
		if (_allLibrariesDir != null) {
			return _allLibrariesDir;
		}
		return childSheet.getLibraries();
	}

	protected File includeLookUp(String path) {
		File result = new File(path);

		if (!result.exists() && (_masterFile != null)) {
			result = new File(_masterFile.getParent() + File.separator + path);
		}

		if (!result.exists()) {
			_logger.debug("Json Cascaded Sheet not found at {}",result.getAbsolutePath());
			System.out.println("Json Cascaded Sheet not found at" + result.getAbsolutePath());
			result = getLibraries().stream()
					.map(dir -> new File(dir.getAbsolutePath() + File.separator + path))
					.filter(File::exists)
					.findFirst()
					.orElse(null);
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

	public Stream<File> getIncludedFiles() {
		return _allSheetFiles.stream();
	}

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

						_logger.debug("Resolving include {}", incNode.asText());

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

	//
	// ******************************************************************************************************************
	//

	private Map<String, Object> getCache() {
		if (_cachedEntries != null) {
			return _cachedEntries;
		}
		return childSheet.getCache();
	}

	public void setValue(String path, Object value) {
		if (getCache().containsKey(path)) {
			_logger.debug("Override a cached value : {} was cached '{}'", path, getCache().get(path));
		}

	}
	//
	// ******************************************************************************************************************
	//

	public Stream<Map.Entry<String, Object>> getAllResolved() {
		return getCache().entrySet().stream();
	}

	/** Retrieve the definition of value, if not defined, returns the default value. */
	public Object get(String path, Object dflt) {
		Object node = get(path);
		if (node == null) {
			return dflt;
		}
		return node;
	}

	public Object get(String path) {

		Object result = null;

		if ((path == null) || (path.isEmpty())) {
			throw new IllegalArgumentException("Invalid undefined path");
		}

		// First look into the cached entries.
		result = getCache().get(path);

		// If not found in the cache, then search it in the current and parent sheets
		if (result == null) {
			result = getDefinition(path);
			// If found, then udpate the cache
			if (result != null) {
				if (result instanceof String) {
					result = resolve((String) result);
				}
				getCache().put(path, result);
			}
		}

		return result;
	}

	public String resolve(String value) {
		String result = value;
		Pattern regex = Pattern.compile("\\$\\{.*\\}");
		Matcher matcher = regex.matcher(result);
		while (matcher.find()) {
			String reference = matcher.group().substring(2, matcher.group().length() - 1);
			Object refValue = get(reference);
			if (refValue instanceof String) {
				result = result.replace(matcher.group(), (String) refValue);
			}
			matcher = regex.matcher(result);
		}
		return result;
	}

	//
	// ******************************************************************************************************************
	//

	public Object getDefinition(String path) {

		if (!isValid()) {
			throw new IllegalStateException("Cascaded Json Sheet not initialized?");
		}

		JsonCascadedSheet ownerSheet = getDefinitionLocation(path);

		if (ownerSheet != null) {
			JsonNode node = JsonUtils.getJsonByPath(ownerSheet._masterSheet, path);

			// If a node is found in the current Json sheet then return the converted
			// values for basic types, or return a JsonNode
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
				return node;
			}
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
