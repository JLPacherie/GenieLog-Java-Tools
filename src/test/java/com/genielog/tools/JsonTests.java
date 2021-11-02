package com.genielog.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.genielog.tools.json.Utils;

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
		
		JsonNode node = Utils.getJsonNodeFromText(jsonText);
		assertNotNull(node,"Unbale to parse");
		assertEquals("value1",Utils.getFieldAsText(node,"name1","",null));

		assertEquals(null,Utils.getFieldAsText(node,"missing",null,null));
		assertEquals(null,Utils.getFieldAsText(node,".missing",null,null));
		
		assertEquals("value1",Utils.getFieldAsText(node,".name1","",null));
		assertEquals("value2",Utils.getFieldAsText(node,".child1.name1","",null));
		assertEquals(null,Utils.getFieldAsText(node,".child1.missing",null,null));
	}
}
