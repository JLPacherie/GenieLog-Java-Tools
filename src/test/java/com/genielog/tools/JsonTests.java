package com.genielog.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.genielog.tools.json.JsonCascadedSheet;

class JsonTests extends BaseTest {

	@Test
	void test_JsonUtils() {

		String jsonText = ""
				+ "{"
				+ "  \"name1\": \"value1\","
				+ "  \"child1\": {"
				+ "    \"name1\": \"value2\""
				+ "  }"
				+ "}";

		JsonNode node = JsonUtils.getJsonNodeFromText(jsonText);
		assertNotNull(node, "Unbale to parse");
		assertEquals("value1", JsonUtils.getFieldAsText(node, "name1", "", null));

		assertEquals(null, JsonUtils.getFieldAsText(node, "missing", null, null));
		assertEquals(null, JsonUtils.getFieldAsText(node, ".missing", null, null));

		assertEquals("value1", JsonUtils.getFieldAsText(node, ".name1", "", null));
		assertEquals("value2", JsonUtils.getFieldAsText(node, ".child1.name1", "", null));
		assertEquals(null, JsonUtils.getFieldAsText(node, ".child1.missing", null, null));
	}

	@Test
	void test_CascadedJsonSheet1() {

		String jsonMasterSheetPath = "tests/CascadedJsonSheet/cjs_1.json";

		JsonCascadedSheet sheet = new JsonCascadedSheet(jsonMasterSheetPath);

		assertTrue(sheet.isValid());

		testPaths(sheet, new String[] { ".field1", ".field2", ".field3", ".field4", ".field5", ".field6" });

	}

	String[] _basicPaths = new String[] {
			".project.name",
			".project.version",
			".coverity.work"
	};

	void testPaths(JsonCascadedSheet sheet, String... paths) {
		Map<String, Object> dictionary = new HashMap<>();

		for (String path : paths) {
			Object value = sheet.get(path);
			assertNotNull(value, "Undefined path " + path);
			_logger.info(" [{}] = {}", path, value);
			_logger.info("   as defined by  : {}", sheet.getDefinition(path));
			_logger.info("         in sheet : {}", sheet.getDefinitionLocation(path).getFile().getName());
			dictionary.put(path, value);
		}

		JsonNode compiledNode = null;
		try {

			List<File> allFiles = sheet.getAllIncludedFiles().collect(Collectors.toList());

			_logger.info("List of loaded files to compile");
			allFiles.forEach(file -> {
				_logger.info("{}", file.getAbsoluteFile());
			});

			compiledNode = JsonUtils.getJsonNodeFromFile(allFiles.get(0).getAbsolutePath());
			_logger.info("Initial {}: \n{}",
					allFiles.get(0).getName(),
					JsonUtils.getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(compiledNode));

			for (int i = 1; i < allFiles.size(); i++) {
				JsonNode n = JsonUtils.getJsonNodeFromFile(allFiles.get(i).getAbsolutePath());
				JsonUtils.merge(n, compiledNode);
				_logger.info("Merged {}: \n{}",
						allFiles.get(i).getName(),
						JsonUtils.getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(compiledNode));
			}
		} catch (JsonProcessingException e) {

		}

		for (Map.Entry<String, Object> entry : dictionary.entrySet()) {
			String compiledValue = JsonUtils.getFieldAsText(compiledNode, entry.getKey(), null, null);
			_logger.info(" {} = {} ?= {} ", entry.getKey(), entry.getValue(), compiledValue);
		}
	}

	@Test
	void test_CascadedJsonSheet2() {

		String jsonMasterSheetPath = "/opt/Data/kubernetes/storage/jenkins/lts/workspace/GenieLog Java Tools@4/project/jenkins-config.json";
		File jsonFile = new File(jsonMasterSheetPath);
		if (jsonFile.exists()) {
			JsonCascadedSheet sheet = new JsonCascadedSheet(jsonMasterSheetPath,
					"/opt/synopsys/snps-extpack/data/configs/pipeline/conf");
			assertTrue(sheet.isValid());

			assertEquals("1.0", sheet.get(".version"), "Mismatched version");

			Object[] allConfigs = (Object[]) sheet.get(".coverity.analysis.configs", null);

			Object initCmd = sheet.get(".project.build.init_command");

			testPaths(sheet, _basicPaths);

			_logger.info("");
			_logger.info(" -- List of Resolved Definitions --");
			sheet.getAllCached().forEach(entry -> {
				JsonCascadedSheet ownerSheet = sheet.getDefinitionLocation(entry.getKey());
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
	}

	@Test
	void test_JenkinsConfigs() {

		String jenkinsConfigDir = "/opt/synopsys/snps-extpack/data/configs/pipeline";
		File rootConfigDir = new File(jenkinsConfigDir + "/examples");

		Collection<File> allFiles = FileUtils.listFiles(rootConfigDir, new String[] { "json" }, true);
		for (File jsonFile : allFiles) {

			JsonCascadedSheet sheet = null;
			try {
				sheet = new JsonCascadedSheet(jsonFile.getAbsolutePath(), jenkinsConfigDir + "/conf");
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
