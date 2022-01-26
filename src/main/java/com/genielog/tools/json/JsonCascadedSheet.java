package com.genielog.tools.json;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javatuples.Pair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
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

	// If this sheet is loaded from another one, that other one is the CHILD sheet
	private JsonCascadedSheet childSheet = null;

	private JsonNode _masterSheet = null;
	private File _masterFile = null;

	// ******************************************************************************************************************
	//
	// ******************************************************************************************************************

	public static JsonCascadedSheet build() {
		JsonCascadedSheet result = new JsonCascadedSheet();
		return result;
	}

	public static JsonCascadedSheet clone(JsonCascadedSheet sheet) {
		JsonNode node = sheet.getRootNode().deepCopy();
		JsonCascadedSheet result = JsonCascadedSheet.build();
		sheet.getLibraries().forEach(file -> result.addLibraries(file.getPath()));
		result.addLibraries(sheet.getFile().getParent());
		result.load(node);
		return result;
	}
	// ******************************************************************************************************************
	//
	// ******************************************************************************************************************

	/** Create a root Sheet imported from a path with a list of library dirs for includes. */
	public JsonCascadedSheet() {

		_logger = LogManager.getLogger(this.getClass());

		_cachedEntries = new HashMap<>();
		_allLibrariesDir = new ArrayList<>();

	}

	public String getId() {
		String result = ""; // (String) get(".name", "");
		if (getFile() != null) {
			result += getFile().getName();
		}
		if (childSheet != null) {
			result += ", imported from " + childSheet.getId();
		}
		return result;
	}

	// ******************************************************************************************************************
	//
	// *****************************************************************************************************inc*************

	public void clear() {
		if (_cachedEntries != null) {
			_cachedEntries.clear();
		}

		if (_allLibrariesDir != null) {
			_allLibrariesDir.clear();
		}

		_allSheets.clear();

		_masterFile = null;
		_masterSheet = null;

		// TODO Should we also disconnect from includedFrom node ?

	}

	public boolean isValid() {
		return (_masterSheet != null);
	}

	public File getFile() {
		return _masterFile;
	}

	public JsonNode getRootNode() {
		return _masterSheet;
	}

	/** Creates a new JSON root node with the aggregation of all included files. */
	public JsonCascadedSheet compile() {

		JsonNode compiledNode = null;

		List<JsonCascadedSheet> allSheets = getAllIncludedSheets().collect(Collectors.toList());

		_logger.debug("List of loaded files to compile");
		for (JsonCascadedSheet sheet : allSheets) {
			_logger.info("{}", sheet.getFile() == null ? "raw" : sheet.getFile().getAbsoluteFile());
		}

		//
		// 1) Merge all the Sheets starting from the in depth dependencies
		//
		compiledNode = allSheets.get(0)._masterSheet.deepCopy();

		for (int i = 1; i < allSheets.size(); i++) {
			JsonNode n = allSheets.get(i)._masterSheet;
			JsonUtils.merge(n, compiledNode);

			//
			// Merge each field suffixed with '+=' with the field with the same suffix
			//
			// Eg. 'fieldA+=' will be merged with 'fieldA'
			List<String> allPaths = new ArrayList<>();
			getAllPaths(compiledNode, "", allPaths);

			for (String path : allPaths) {
				if (path.endsWith("+=")) {
					// For 'name+=', refname is 'name'
					String refName = path.substring(0, path.length() - 2);
					// For '.parent.parent.name' parent is '.parent.parent'
					String parentName = refName.substring(0, path.lastIndexOf("."));

					if (allPaths.contains(refName)) {
						JsonNode parentNode = parentName.isEmpty() ? compiledNode
								: JsonUtils.getJsonByPath(compiledNode, parentName);
						JsonNode refValue = JsonUtils.getJsonByPath(compiledNode, refName);
						JsonNode addValue = JsonUtils.getJsonByPath(compiledNode, path);

						if (refValue.isArray() && addValue.isArray()) {
							((ArrayNode) refValue).addAll((ArrayNode) addValue);
							String localName = path.substring(path.lastIndexOf(".") + 1);
							JsonNode removed = ((ObjectNode) parentNode).remove(localName);
							if (removed != addValue) {
								_logger.error("Can't remove merged array node ?");
							}
						} else if (refValue.isTextual() && addValue.isTextual()) {
							String mergedValue = refValue.asText() + addValue.asText();
							String localName = refName.substring(path.lastIndexOf(".") + 1);
							JsonNode updated = ((ObjectNode) parentNode).replace(localName, new TextNode(mergedValue));
							JsonNode removed = ((ObjectNode) parentNode).remove(localName + "+=");

							String verif = getAsText(refName);

							if (!Objects.equals(mergedValue, verif)) {
								_logger.error("Can't remove merged text node ?");
								_logger.error("  updated node is {}", updated.toPrettyString());
								_logger.error("  removed node is {}", removed.toPrettyString());
							}

						}

					} else {

					}
				}
			}
		}

		//
		// 2) Remove any references to includes (to allow building a Sheet from the compiled node)
		//
		JsonNode includes = compiledNode.get("includes");
		if (includes != null)
			((ObjectNode) compiledNode).putArray("includes");

		_masterSheet = compiledNode;
		_allSheets.clear();

		return this;

	}

	public List<String> getAllPaths(boolean incIncluded) {
		List<String> result = new ArrayList<>();
		getAllPaths(_masterSheet, "", result);
		if (incIncluded) {
			for (JsonCascadedSheet incSheet : _allSheets) {
				List<String> incPaths = incSheet.getAllPaths(true);
				for (String incPath : incPaths) {
					if (!result.contains(incPath)) {
						result.add(incPath);
					}
				}
			}
		}
		return result;
	}

	public void getAllPaths(JsonNode root, String prefix, List<String> result) {

		Iterator<Entry<String, JsonNode>> fields = root.fields();
		while (fields.hasNext()) {
			Entry<String, JsonNode> entry = fields.next();
			result.add(prefix + "." + entry.getKey());
			JsonNode value = entry.getValue();
			if (value.isObject()) {
				getAllPaths(value, prefix + "." + entry.getKey(), result);
			} else if (value.isArray()) {

			} else {

			}
		}

	}
	// ******************************************************************************************************************
	// Included Dependencies Management
	// ******************************************************************************************************************

	public JsonCascadedSheet addLibraries(String... libs) {
		if (libs != null) {
			for (String lib : libs) {
				if (lib != null) {
					File dir = new File(lib);
					if (dir.isDirectory() && _allLibrariesDir.stream().noneMatch( folder -> lib.equals(folder.getPath()))) {
						_allLibrariesDir.add(dir);
					}
				}
			}
		}
		return this;
	}
	
	public JsonCascadedSheet addLibraries(Collection<File> dirs) {
		dirs.forEach( dir -> {
			if (_allLibrariesDir.stream().noneMatch( folder -> folder.getPath().equals(dir.getPath()))) {
				_allLibrariesDir.add(dir);
			}
		});
		return this;
	}

	/** Get the list of available included file locations for this sheet or the one of its child sheet. */
	public List<File> getLibraries() {
		if (_allLibrariesDir != null) {
			return _allLibrariesDir;
		}
		return childSheet.getLibraries();
	}

	public Stream<JsonCascadedSheet> getAllIncludedSheets() {
		return Stream.concat(
				_allSheets.stream().flatMap(JsonCascadedSheet::getAllIncludedSheets),
				Stream.of(this));
	}

	/** Get the list of included files in this sheet. */
	public Stream<JsonCascadedSheet> getIncludedSheets() {
		return _allSheets.stream();
	}

	/** Get the list of all included files, with transitive dependencies */
	public Stream<File> getAllIncludedFiles() {
		return getAllIncludedSheets().map(JsonCascadedSheet::getFile).filter(Objects::nonNull);
	}

	/** Get the list of included files in this sheet. */
	public Stream<File> getIncludedFiles() {
		return getIncludedSheets().map(JsonCascadedSheet::getFile).filter(Objects::nonNull);
	}

	/** Seach in library location for the file included from a path (as in an include section). */
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
			// _logger.debug("Json Cascaded Sheet found at {}", result.getAbsolutePath());
		} else {
			_logger.error("Json Cascaded Sheet not found {}", path);
			if (_masterFile == null) {
				_logger.error("There's no src file known to look for {} in the same path", path);
			} else {
				_logger.error("There's no match in the same location {}", _masterFile.getParent());
			}
			getLibraries().forEach(dir -> _logger.error("There's no match in lib path {}", dir.getPath()));
		}
		return result;
	}

	// ******************************************************************************************************************
	// Loading the Cascaded Json Sheet from different source
	// ******************************************************************************************************************

	public JsonCascadedSheet load(String jsonText) {
		boolean result = (jsonText != null) && !jsonText.isEmpty();
		if (result) {
			JsonNode root = JsonUtils.getJsonNodeFromText(jsonText);
			if (root != null) {
				result = load(root) != null;
			} else {
				_logger.error("Unable to parse JSON from {}", jsonText);
				result = false;
			}
		}
		return result ? this : null;
	}

	public JsonCascadedSheet load(JsonNode root) {
		boolean result = (root != null) && root.isContainerNode();
		if (result) {
			_masterSheet = root;
			result = resolveIncludes();
		}

		if (!result) {
			clear();
		}

		return result ? this : null;
	}

	public JsonCascadedSheet load(File srcFile) {
		boolean result = (srcFile != null) && (srcFile.canRead());
		if (result) {
			_masterFile = srcFile;
			JsonNode root = JsonUtils.getJsonNodeFromFile(srcFile.getAbsolutePath());
			if (root != null) {
				result = load(root) != null;
				if (!result) {
					_masterFile = null;
					throw new IllegalArgumentException(
							"Unable to load a Cascaded JSON Sheet {} from parsed JSON " + srcFile.getPath());
				}
			} else {
				throw new IllegalArgumentException("Unable to parse JSON from " + srcFile.getPath());
			}
		} else {
			throw new IllegalArgumentException("Unable to find JSON file at " + srcFile);
		}
		return result ? this : null;
	}

	/** Load an included file referenced by a path to be resolved in the list of Libraries.*/
	public JsonCascadedSheet load(Path pathname) {
		File srcFile = includeLookUp(pathname.toString());
		if (srcFile != null) {
			return load(srcFile);
		}
		return null;
	}

	public JsonCascadedSheet include(JsonNode rootSheet) {
		JsonCascadedSheet incSheet = JsonCascadedSheet
				.build()
				.addLibraries(getLibraries().stream().map(File::getPath).collect(Collectors.toList()).toArray(new String [0]))
				.load(rootSheet);

		if (incSheet.isValid()) {
			include(incSheet);
		}

		return this;
	}

	public JsonCascadedSheet include(File incFile) {
		JsonCascadedSheet includedSheet = JsonCascadedSheet
				.build()
				.addLibraries(getLibraries().stream().map(File::getPath).collect(Collectors.toList()).toArray(new String [0]))
				.load(incFile);

		if (includedSheet.isValid()) {
			include(includedSheet);
			_logger.debug("Including sheet from {}", incFile.getName());
		}

		return this;
	}

	public JsonCascadedSheet include(JsonCascadedSheet sheet) {

		if ((sheet == null) || !sheet.isValid()) {
			throw new IllegalArgumentException(
					"Can't include an invalid sheet " + ((sheet == null) ? "null" : sheet.getId()));
		}

		if (_allSheets.contains(sheet)) {
			throw new IllegalArgumentException("Sheet already included " + sheet.getId());
		}

		sheet.childSheet = this;
		_allSheets.add(sheet);

		if (sheet._allLibrariesDir != null) {
			addLibraries(sheet._allLibrariesDir);
			sheet._allLibrariesDir = null;
		}
		
		sheet._cachedEntries = null;

		return this;
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

		JsonNode includes = JsonUtils.getJsonByPath(_masterSheet, ".includes");
		if (includes != null) {
			if (!includes.isArray()) {
				result = false;
				_logger.error("Bad format, __includes is a reserved keyword for the array of included JSON files");

			} else {
				Iterator<JsonNode> incIter = includes.iterator();
				while (incIter.hasNext()) {

					JsonNode incNode = incIter.next();

					if (!incNode.isTextual()) {
						result = false;
						_logger.error("Bad format, __includes should contain only strings which are included JSON file paths");
						continue;
					}

					String incPath = incNode.asText();
					File incFile = includeLookUp(incPath);

					if (incFile == null) {
						result = false;
						String message = String.format("In '%s', included file not found '%s'",
								getId(),
								incPath);

						_logger.error(message);
						throw new IllegalArgumentException(message);
					}

					JsonCascadedSheet incSheet = include(incFile);

					if (incSheet == null) {
						result = false;
						_logger.error("Unable to read JSON sheet from file {}", incFile.getAbsolutePath());
					}

				}
			}
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

		// First look into the cached entries if the path refers to the root node
		result = (from == null) ? getCache().get(path) : null;

		// If not found in the cache, then search it in the current and parent sheets
		if (result == null) {
			result = getDefinition(from, path);

			// If found, then udpate the cache
			if (result != null) {
				result = resolve(from, result);
				if (from == null)
					getCache().put(path, result);
			}
		}

		return result;
	}

	// ******************************************************************************************************************
	// Resolvers
	//
	// A resolver aims at replacing in String references to other node. A reference is found each time the pattern ${.*}
	// is found in String field. The referenced value is a path resolved from the context of a Root JsonNode. When no
	// root context is provided, the default one is the root node of the Sheet.
	// ******************************************************************************************************************

	public JsonCascadedSheet resolve() {
		String jsonText = _masterSheet.toPrettyString();
		String jsonResolved = (String) resolve(jsonText);
		JsonNode resolvedRoot = JsonUtils.getJsonNodeFromText(jsonResolved);
		if (resolvedRoot != null) {
			_masterSheet = resolvedRoot;
		}
		return this;
	}

	public Object resolve(Object value) {
		return resolve(_masterSheet, value);
	}

	/** Resolve any value (a string, entries of an array, JsonNode, ...) within the given root context*/
	public Object resolve(JsonNode root, Object value) {
		Object result = value;
		if (value instanceof ContainerNode) {
			JsonNode node = ((JsonNode) value).deepCopy();
			Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> entry = fields.next();

				// The value of the field is a String
				if (entry.getValue().isTextual()) {
					String definition = entry.getValue().asText();
					String resolved = resolve(node, definition);
					((ObjectNode) node).put(entry.getKey(), resolved);
				}

				// The value of the field is an Array
				else if (entry.getValue().isArray()) {
					JsonNode arrayNode = entry.getValue();

					List<JsonNode> nodes = new ArrayList<>();
					int nb = entry.getValue().size();
					for (int i = 0; i < nb; i++) {
						JsonNode item = entry.getValue().get(i);
						// The array item is a String
						if (item.isTextual()) {
							nodes.add(new TextNode(resolve(arrayNode, item.asText())));
						}
						// The array item is something else
						else {
							resolve(arrayNode, item);
							nodes.add(item);
						}
					}

					((ObjectNode) node).putArray(entry.getKey()).addAll(nodes);

				}

				// Other type of field value
				else {
					JsonNode resolvedNode = (JsonNode) resolve(node, entry.getValue());
					((ObjectNode) node).replace(entry.getKey(), resolvedNode);
				}
			}

			result = node;
		} else if (value instanceof String) {
			result = resolve(root, (String) value);
		} else if (value instanceof Object[]) {
			result = new Object[((Object[]) value).length];
			for (int i = 0; i < ((Object[]) value).length; i++) {
				Object obj = ((Object[]) value)[i];
				((Object[]) result)[i] = resolve(root, obj);
			}
		}

		String strResult = (result instanceof JsonNode) ? ((JsonNode) result).toPrettyString() : result.toString();
		String[] unresolved = StringUtils.getAllReferences(strResult, "${", "}");
		for (String ref : unresolved) {
			_logger.error("Unable to resolved all references in {}", ref);
		}
		if (unresolved.length > 0) {
			_logger.error("In \n{}", strResult);
		}
		return result;
	}

	/** Resolve references in string in the context of 'from'. If 'from' is null then use Sheet as the context */
	public String resolve(JsonNode from, String value) {
		String result = value;
		if (result != null) {

			result = StringUtils.resolve(value, "${", "}", name -> {

				JsonNode root = null;
				if (name.startsWith(".")) {
					root = null;
				} else {
					root = from;
				}

				Object valueNode = getFromNode(root, name);
				if (valueNode != null)
					return valueNode.toString();
				return null;
			});

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
				node.forEach(listNode -> list.add(getObjectFromNode(listNode)));
				return list.toArray();
			}
		}
		return result;
	}

	// ******************************************************************************************************************
	// A Definition of a node is it's value before any references it may include are resolved.
	//
	// For example "name": "${.other_field}" as a definition of '${.other_field}'
	// ******************************************************************************************************************

	/** Get the definition (unresolved) value of a path in the context of the root node of this Sheet. */
	public Object getDefinition(String path) {
		return getDefinition(null, path);
	}

	/** Get the definition (unresolved) value of a path in the context of the given root JsonNode */
	public Object getDefinition(JsonNode root, String path) {

		if (!isValid()) {
			throw new IllegalStateException("Cascaded Json Sheet not initialized?");
		}

		Object result = null;

		if (root == null) {
			JsonCascadedSheet ownerSheet = getTopSheet(path);
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

	/** Returns the top level Sheet and the node where is defined the given path */
	public Pair<JsonCascadedSheet, JsonNode> getDefLocation(JsonNode from, String path) {
		Pair<JsonCascadedSheet, JsonNode> result = null;

		return result;
	}

	/** Returns the first Sheet defining the given path.
	 *  <p>The lookup starts from from this one, then each included Json sheet starting from
	 *  the last included one up to the first.
	 */
	public JsonCascadedSheet getTopSheet(String path) {
		JsonCascadedSheet result = JsonUtils.getJsonByPath(_masterSheet, path) != null ? this : null;

		int iSheet = _allSheets.size() - 1;
		while ((result == null) && (iSheet >= 0)) {
			result = _allSheets.get(iSheet).getTopSheet(path);
			iSheet--;
		}

		return result;
	}

	public Stream<JsonCascadedSheet> getAllDefinitionLocation(String path) {

		return Stream.concat(
				_allSheets.stream().flatMap(sheet -> sheet.getAllDefinitionLocation(path)),
				JsonUtils.getJsonByPath(_masterSheet, path) != null ? Stream.of(this) : Stream.empty());
	}

	public String toPrettyString() {
		return _masterSheet.toPrettyString();
	}
}
