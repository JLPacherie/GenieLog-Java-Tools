package com.genielog.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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

		assertTrue(sheet.load(jsonMasterSheetPath));

	}

	@Test
	void test_CascadedJsonSheet2() {

		String jsonMasterSheetPath = "/opt/Data/kubernetes/storage/jenkins/lts/workspace/GenieLog Java Tools@2/project/jenkins-config.json";

		JsonCascadedSheet sheet = new JsonCascadedSheet(jsonMasterSheetPath, "/opt/synopsys/snps-extpack/data/configs");
		assertTrue(sheet.isValid());

		assertEquals("1.0", sheet.get(".version"), "Mismatched version");

		// Test a single raw entry (string)
		_logger.info("Project           : {}", sheet.get(".project.name"));
		assertTrue(sheet.get(".project.name") instanceof String, "Bad type");

		_logger.info("Coverity Platform : {}", sheet.get(".coverity.platform.path"));
		_logger.info("   as defined by  : {}", sheet.getDefinition(".coverity.platform.path"));
		_logger.info("         in sheet : {}", sheet.getDefinitionLocation(".coverity.platform.path").getFile().getName());
		assertTrue(sheet.get(".project.name") instanceof String, "Bad type");

		_logger.info("SNPS Extension Pack is : {}", sheet.get(".snps-extpack.path"));
		_logger.info("   as defined by  : {}", sheet.getDefinition(".snps-extpack.path"));
		_logger.info("         in sheet : {}", sheet.getDefinitionLocation(".snps-extpack.path").getFile().getName());
		assertTrue(sheet.get(".snps-extpack.path") instanceof String, "Bad type");

		_logger.info("Coverity Analysis Type : {}", sheet.get(".coverity.analysis.type"));
		_logger.info("        as defined by  : {}", sheet.getDefinition(".coverity.analysis.type"));
		_logger.info("              in sheet : {}",
				sheet.getDefinitionLocation(".coverity.analysis.type").getFile().getName());
		assertTrue(sheet.get(".coverity.analysis.type") instanceof String, "Bad type");

		_logger.info("Project build enabled ? : {}", sheet.get(".project.build.enabled"));
		assertTrue(sheet.get(".project.build.enabled") instanceof Boolean, "Bad type");

		_logger.info("");
		_logger.info(" -- List of Resolved Definitions --");
		sheet.getAllResolved().forEach(entry -> {
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
