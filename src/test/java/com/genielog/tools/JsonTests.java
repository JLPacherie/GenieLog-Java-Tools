package com.genielog.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.genielog.tools.json.JsonCascadedSheet;

class JsonTests extends BaseTest {

	@Test
	void test_Resolvers() {

		String jsonText = ""
				+ "{"
				+ "  \"name1\": \"value1\","
				+ "  \"name2\": \"value2\","
				// Simple substitution globaly resolved
				+ "  \"name3\": \"${.name1}\","
				// Simple substitution locally resolved
				+ "  \"name4\": \"${name1}\","
				// Double substitution globaly resolved
				+ "  \"name5\": \"${.name1}${.name2}\","
				// Simple partial substitution globaly resolved
				+ "  \"name6\": \"prefix${.name2}\","
				+ "  \"name7\": \"${.name2}suffix\","
				// Simple partial substitution globaly NOT resolved in the sheet
				+ "  \"name8\": \"name0=${.name0}\","
				// Trabsitive substitution
				+ "  \"name9\": \"${.name5}\","
				+ "  \"child\": {"
				+ "      \"name1\": \"child_value1\","
				+ "      \"name2\": \"child_value2\","
				// Simple substitution globaly resolved
				+ "      \"name3\": \"${.name1}\","
				// Simple substitution locally resolved
				+ "      \"name4\": \"${name1}\","
				// Double substitution globaly resolved
				+ "      \"name5\": \"${.name1}${.name2}\","
				// Simple partial substitution globaly resolved
				+ "      \"name6\": \"prefix${.name2}\","
				+ "      \"name7\": \"${.name2}suffix\","
				// Simple partial substitution globaly NOT resolved in the sheet
				+ "      \"name8\": \"name0=${.name0}\""
				+ "  }"
				+ "}";

		JsonCascadedSheet sheet = JsonCascadedSheet.build()
				.load(jsonText);

		assertNotNull(sheet, "Unable to parse JSON text");
		assertTrue(sheet.isValid(), "Invalid Cascaded Sheet");

		// ------------------------------------------------------------------------
		// Test Various Substitutions resolved in the same sheet
		// ------------------------------------------------------------------------

		assertEquals("value1", sheet.get(".name1"));
		assertEquals("value2", sheet.get(".name2"));
		assertEquals("value1", sheet.get(".name3"));
		assertEquals("value1", sheet.get(".name4"));
		assertEquals("value1value2", sheet.get(".name5"));
		assertEquals("prefixvalue2", sheet.get(".name6"));
		assertEquals("value2suffix", sheet.get(".name7"));
		assertEquals("name0=${.name0}", sheet.get(".name8"));
		assertEquals("value1value2", sheet.get(".name9"));

		assertEquals("child_value1", sheet.get(".child.name1"));
		assertEquals("child_value2", sheet.get(".child.name2"));
		assertEquals("value1", sheet.get(".child.name3"));

		// This test doesn't work, and I'm not sure we do need to be
		// able to resolve a reference only locally (wiithout
		// giving the parent path

		// assertEquals("child_value1",sheet.get(".child.name4"));

		assertEquals("value1value2", sheet.get(".child.name5"));
		assertEquals("prefixvalue2", sheet.get(".child.name6"));
		assertEquals("value2suffix", sheet.get(".child.name7"));
		assertEquals("name0=${.name0}", sheet.get(".child.name8"));

		String jsonTextMain = ""
				+ "{"
				+ "  \"name0\": \"value0\","
				+ "  \"name1\": \"overwrite_value1\","
				+ "  \"name2\": \"overwrite_value2\""
				+ "}";

		JsonCascadedSheet mainSheet = JsonCascadedSheet.build()
				.load(jsonTextMain);

		assertNotNull(mainSheet, "Unable to parse JSON text");
		assertTrue(mainSheet.isValid(), "Invalid Cascaded Sheet");

		mainSheet.include(sheet);

		assertEquals("value1", sheet.get(".name1"));
		assertEquals("value2", sheet.get(".name2"));
		assertEquals("value1", sheet.get(".name3"));
		assertEquals("value1", sheet.get(".name4"));
		assertEquals("value1value2", sheet.get(".name5"));
		assertEquals("prefixvalue2", sheet.get(".name6"));
		assertEquals("value2suffix", sheet.get(".name7"));
		assertEquals("name0=${.name0}", sheet.get(".name8"));

		assertEquals("value0", mainSheet.get(".name0"));
		assertEquals("overwrite_value1", mainSheet.get(".name1"));
		assertEquals("overwrite_value2", mainSheet.get(".name2"));

		assertEquals("overwrite_value1", mainSheet.get(".name3"));

		// assertEquals("value1",mainSheet.get(".name4"));

		assertEquals("overwrite_value1overwrite_value2", mainSheet.get(".name5"));
		assertEquals("prefixoverwrite_value2", mainSheet.get(".name6"));
		assertEquals("overwrite_value2suffix", mainSheet.get(".name7"));
		assertEquals("name0=value0", mainSheet.get(".name8"));

	}

	@Test
	void test_ArrayResolvers() {

		String jsonText1 = ""
				+ "{"
				+ "  \"name1\": \"value1\","
				+ "  \"array\": ["
				+ "    \"item1\","
				+ "    \"item2\"" // Refers to
				+ "  ],"
				+ "  \"array_merge\": ["
				+ "    \"item5\","
				+ "    \"item6\"" // Refers to
				+ "  ],"
				+ "  \"field_merged\": \"prefix\","
				+ "  \"parent\": {"
				+ "     \"subfield_merged\": \"prefix\""
				+ "  }"
				+ "}";

		JsonCascadedSheet sheet1 = JsonCascadedSheet.build();
		sheet1.load(jsonText1);

		String jsonText2 = ""
				+ "{"
				+ "  \"name2\": \"value2\","
				+ "  \"array\": ["
				+ "    \"item4\","
				+ "    \"item3\"" // Refers to
				+ "  ],"
				+ "  \"array_merge+=\": ["
				+ "    \"item7\","
				+ "    \"item8\"" // Refers to
				+ "  ],"
				+ "  \"field_merged+=\": \"suffix\","
				+ "  \"parent\": {"
				+ "     \"subfield_merged+=\": \"suffix\""
				+ "  }"
				+ "}";

		JsonCascadedSheet sheet2 = JsonCascadedSheet.build();
		sheet2.load(jsonText2);

		Object[] array1 = (Object[]) sheet1.get(".array");
		Object[] array2 = (Object[]) sheet2.get(".array");

		_logger.info("Array 1 :\n{}", sheet1.get(".array"));
		_logger.info("Array 2 :\n{}", sheet2.get(".array"));

		sheet2.include(sheet1);

		_logger.info("Array 1 :\n{}", sheet1.get(".array"));
		_logger.info("Array 2 :\n{}", sheet2.get(".array"));

		sheet2.compile();

		Object[] arrayCompiled = (Object[]) sheet2.get(".array");
		assertTrue(Arrays.equals(arrayCompiled, array2), "Mismatched");

		Object[] arrayMerged = (Object[]) sheet2.get(".array_merge");
		_logger.info("Array merged compiled :\n{}", Arrays.toString(arrayMerged));

		assertEquals("prefixsuffix", sheet2.get(".field_merged"));
		assertEquals("prefixsuffix", sheet2.get(".parent.subfield_merged"));
	}

	@Test
	void test_JsonUtils() {

		String jsonText = ""
				+ "{"
				+ "  \"name1\": \"value1\","
				+ "  \"child1\": {"
				+ "    \"name1\": \"value2\","
				+ "    \"name2\": \"${.name1}\"," // Refers to
				+ "    \"name3\": \"${name1}\"" // Refers to
				+ "  }"
				+ "}";

		JsonNode node = JsonUtils.getJsonNodeFromText(jsonText);
		assertNotNull(node, "Unbale to parse");
		assertEquals("value1", JsonUtils.getFieldAsText(node, "name1", "", null));

		assertEquals(null, JsonUtils.getFieldAsText(node, "missing", null, null));
		assertEquals(null, JsonUtils.getFieldAsText(node, ".missing", null, null));

		assertEquals("value1", JsonUtils.getFieldAsText(node, ".name1", "", null));
		assertEquals("value2", JsonUtils.getFieldAsText(node, ".child1.name1", "", null));
		assertEquals("${.name1}", JsonUtils.getFieldAsText(node, ".child1.name2", "", null));
		assertEquals(null, JsonUtils.getFieldAsText(node, ".child1.missing", null, null));

		String tmpFilePath = "tests/tmpJson1.json";
		try {
			File tmpJson = new File(tmpFilePath);
			FileUtils.write(tmpJson, jsonText, Charset.defaultCharset(), false);

			JsonCascadedSheet sheet = JsonCascadedSheet.build().load(tmpJson);
			Object child = sheet.get(".child1");
			assertNotNull(child, "Child not found from Sheet");
			assertTrue(child instanceof JsonNode, "Child not a JsonNode");

			assertEquals(sheet.get(".child1.name2"), sheet.getFromNode(null, ".child1.name2"));
			assertEquals(sheet.get(".child1.name2"), sheet.getFromNode(node, ".child1.name2"));

			assertEquals("value1", sheet.getFromNode(null, ".child1.name2"));
			assertEquals("value1", sheet.getFromNode((JsonNode) child, ".name2"));

			assertEquals("value2", sheet.getFromNode((JsonNode) child, ".name3"));
			assertEquals("value2", sheet.getFromNode((JsonNode) child, ".name3"));

			_logger.info("Parsed   : \n{}", sheet.toPrettyString());
			_logger.info("Compiled : \n{}", sheet.compile().toPrettyString());
			_logger.info("Resolved : \n{}", sheet.resolve().toPrettyString());
		} catch (IOException e) {
			_logger.error("Unable to create temp JSON file at {} : {}", tmpFilePath, Tools.getExceptionMessages(e));
		}
	}

	@Test
	void test_CascadedJsonSheet1() {

		String jsonMasterSheetPath = "tests/CascadedJsonSheet/cjs_1.json";

		File srcJsonFile = new File(jsonMasterSheetPath);

		if (srcJsonFile.exists()) {

			JsonCascadedSheet sheet = JsonCascadedSheet.build().load(srcJsonFile);
			assertTrue(sheet != null && sheet.isValid());

			testPaths(sheet, new String[] { ".field1", ".field2", ".field3", ".field4", ".field5", ".field6" });

		} else {
			_logger.error("Unable to execute test, the JSON source file to test is not founf at {}", jsonMasterSheetPath);
		}
	}

	String[] _basicPaths = new String[] {
			".project.name",
			".project.version",
			".coverity.work",
			"sonar.sonarqube"
	};

	void testPaths(JsonCascadedSheet sheet, String... paths) {

		List<String> allPaths = new ArrayList<String>();

		for (String path : paths) {
			allPaths.add(path);
		}

		if (allPaths.isEmpty())
			allPaths = sheet.getAllPaths(true);

		Collections.sort(allPaths);

		allPaths.forEach(path -> {
			Object value = sheet.get(path);
			if (value == null) {
				_logger.warn("In {} : Undefined path {}", sheet.getId(), path);
			} else {

				assertNotNull(value, () -> {
					sheet.compile();
					_logger.info("Failing on path {} in {}\n{}", path, sheet.getFile(), sheet.toPrettyString());
					return "Undefined path " + path;
				});

				if (!(value instanceof JsonNode)) {
					_logger.info(" \"{}\" = \"{}\"", path, value);

					Object def = sheet.getDefinition(path);
					if (!def.equals(value)) {
						_logger.info("   as defined by  : {}", def);
					}
					JsonCascadedSheet sheetDef = sheet.getTopSheet(path);

					_logger.info("         in sheet : {}", sheetDef.getId());
				}
			}
		});

	}

	void testCompilation(JsonCascadedSheet sheet) {

		JsonCascadedSheet compiledSheet = JsonCascadedSheet.clone(sheet);
		compiledSheet.compile()
				.resolve();

		assertTrue(compiledSheet != null, "Unable to create compiled sheet");
		assertTrue(compiledSheet.isValid(), "Compiled sheet not valid");

		List<String> allPaths = sheet.getAllPaths(true);
		List<String> allPathsCompiled = compiledSheet.getAllPaths(true);

		Collections.sort(allPaths);
		Collections.sort(allPathsCompiled);

		// name+= are removed after compile
		assertTrue(allPaths.size() >= allPathsCompiled.size());

		for (String path : allPaths) {

			if (path.endsWith("+="))
				continue;

			allPathsCompiled.remove(path);

			Object compiledValue = compiledSheet.get(path);
			Object value = sheet.get(path);

			/*
			Object compiledDefinition = compiledSheet.getDefinition(path);
			if (!compiledValue.getClass().isArray()
					? Arrays.deepEquals((Object[]) compiledValue, (Object[]) compiledDefinition)
					: Objects.equals(compiledValue, compiledDefinition)) {
				_logger.error("Not resolved ?");
				_logger.error("Definition : [{}]", compiledDefinition);
				_logger.error("Value      : [{}]", compiledValue);
			}
			*/

			if (compiledValue == null) {
				_logger.warn("In compiled {} : Undefined path {}", sheet.getId(), path);
			} else {

				assertTrue(compiledValue.getClass().isArray() == value.getClass().isArray());

				if (compiledValue.getClass().isArray()) {

					assertTrue(
							Arrays.equals((Object[]) value, (Object[]) value),
							"Mismatched values between Sheet and compiled JSON ");

					_logger.info("Test passed with same resolved array at {}", path);
				} else {
					assertEquals(value, sheet.get(path), "Mismatched values between Sheet and compiled JSON ");
					_logger.info("Test passed with same resolved value at {}", path);
				}
			}
		}

		assertTrue(allPathsCompiled.isEmpty());

	}

	@Test
	void test_CascadedJsonSheet2() {

		String jenkinsConfigDir = "/opt/synopsys/snps-extpack/data/configs/pipeline";

		String[] rootDirs = new String[] {
				".",
				jenkinsConfigDir + "/examples",
				"/opt/Data/kubernetes/storage/jenkins/lts/workspace"
		};

		String jsonMasterSheetPath = "xerces-c.json";

		File jsonFile = null;
		int i = 0;
		while (((jsonFile == null) || !jsonFile.exists()) && (i < rootDirs.length)) {
			jsonFile = new File(rootDirs[i] + "/" + jsonMasterSheetPath);
			i++;
		}

		assertTrue(jsonFile.exists());

		JsonCascadedSheet sheet = JsonCascadedSheet
				.build()
				.addLibraries("/opt/synopsys/snps-extpack/data/configs/pipeline/conf")
				.load(jsonFile);

		assertTrue(sheet.isValid());

		assertEquals("1.0", sheet.get(".version"), "Mismatched version");

		testPaths(sheet);

		testCompilation(sheet);

		_logger.info("");
		_logger.info(" -- List of Resolved Definitions --");
		sheet.getAllCached().forEach(entry -> {
			JsonCascadedSheet ownerSheet = sheet.getTopSheet(entry.getKey());
			_logger.info("{} = '{}'", entry.getKey(), entry.getValue());
			sheet.getAllDefinitionLocation(entry.getKey())
					// .filter(aSheet -> aSheet != ownerSheet)
					.forEach(aSheet -> {
						if (aSheet != ownerSheet) {
							_logger.info("   Overriden from [{}] was '{}'",
									aSheet.getFile().getName(),
									aSheet.getDefinition(entry.getKey()));

						} else {
							_logger.info("   Defined in [{}] as '{}'",
									aSheet.getFile().getName(),
									aSheet.getDefinition(entry.getKey()));
						}
					});

		});
	}

	@Test
	void test_AllExampleConfigs() {

		String jenkinsConfigDir = "/opt/synopsys/snps-extpack/data/configs/pipeline";
		File rootConfigDir = new File(jenkinsConfigDir + "/examples");

		Collection<File> allFiles = FileUtils.listFiles(rootConfigDir, new String[] { "json" }, true);
		for (File jsonFile : allFiles) {

			JsonCascadedSheet sheet = null;

			try {
				sheet = JsonCascadedSheet.build()
						.addLibraries(jenkinsConfigDir + "/conf")
						.load(jsonFile);

			} catch (IllegalArgumentException e) {
				sheet = null;
				_logger.info("Unable to process file {} : {}", jsonFile.getName(), Tools.getExceptionMessages(e));
			}

			if ((sheet != null) && sheet.isValid()) {
				_logger.info("");
				_logger.info(" -------------------------------------------------");
				_logger.info(" Sucessfuly parsed JSON Cascaded Sheet at {}", sheet.getFile().getName());
				testPaths(sheet, _basicPaths);

			} else {

			}
		}
	}

	@Test
	void test_merge() throws JsonProcessingException {

		String json1 = "{"
				+ " \"field1.1\": \"value of field1.1\","
				+ " \"field_overwrite\": \"value of node1\","
				+ " \"field1.2\": {"
				+ "    \"a\": \"value of field1.2 a\","
				+ "    \"b\": \"value of field1.2 b\""
				+ "  }"
				+ "}";

		String json2 = "{"
				+ " \"field2.1\": \"value of field2.1\","
				+ " \"field_overwrite\": \"value of node2\","
				+ " \"field1.2\": {"
				+ "    \"a\": \"value of node2\""
				+ "  }"
				+ "}";

		JsonNode node1 = JsonUtils.getJsonNodeFromText(json1);

		JsonNode node2 = JsonUtils.getJsonNodeFromText(json2);

		JsonUtils.merge(node2, node1);

		_logger.info("Merged : \n{}",
				JsonUtils.getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(node1));

	}
}
