package com.genielog.tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class JsonUtils {

	private static ObjectMapper sMapper = null;

	private JsonUtils() {

	}

	public static ObjectMapper getObjectMapper() {
		if (sMapper == null) {
			sMapper = new ObjectMapper();
			sMapper.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
			sMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
			sMapper.enable(SerializationFeature.INDENT_OUTPUT);
		}
		return sMapper;
	}

	/** Returns the child node identified by a path like .parent1.paretn2.node */
	public static JsonNode getJsonByPath(JsonNode parent, String path) {

		int s = 0;
		if (path.startsWith(".")) {
			s = 1;
		}

		int pos = path.indexOf('.', 1);
		if (pos > 1) {
			String prefixPath = path.substring(s, pos);
			JsonNode prefixNode = parent.get(prefixPath);
			if (prefixNode != null) {
				return getJsonByPath(prefixNode, path.substring(pos));
			} else {
				return null;
			}
		}

		return parent.get(path.substring(s));
	}

	public static String getJsonElement(JsonNode node, String defaultValue) {
		if (node != null) {
			try {
				return getObjectMapper().writeValueAsString(node);
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
		}
		return defaultValue;
	}

	public static List<String> getFieldAsStrArray(JsonNode node,
																								String tag,
																								String defaultValue,
																								Consumer<String> setter) {
		ArrayList<String> result = new ArrayList<>();
		if ((node != null) && node.hasNonNull(tag) && node.get(tag).isArray()) {
			for (JsonNode itemNode : node.get(tag)) {
				String value = itemNode.asText(defaultValue);
				result.add(value);
				if (setter != null) {
					setter.accept(value);
				}
			}
		}
		return result;
	}

	public static double getFieldAsDouble(JsonNode node, String tag, double defaultValue, Consumer<Double> setter) {
		double result = defaultValue;
		if ((node != null) && node.hasNonNull(tag) && node.get(tag).isNumber()) {
			result = node.get(tag).asDouble(defaultValue);
			if (setter != null)
				setter.accept(result);
		}
		return result;
	}

	/** Automatically apply a setter to the value of a JSON attribute or its default value. */
	public static String getFieldAsText(JsonNode parent, String tag, String defaultValue, Consumer<String> setter) {

		JsonNode node = getJsonByPath(parent, tag);

		String result = defaultValue;
		if (node != null) {
			result = node.asText(defaultValue);
			if (setter != null)
				setter.accept(result);
		}
		return result;
	}

	public static int getFieldAsInt(JsonNode node, String tag, int defaultValue, Consumer<Integer> setter) {
		int result = defaultValue;
		if ((node != null) && node.hasNonNull(tag) && node.get(tag).isInt()) {
			result = node.get(tag).asInt(defaultValue);
			if (setter != null)
				setter.accept(result);
		}
		return result;
	}

	public static JsonNode getJsonNodeFromFile(String pathname) {
		try {
			File file = new File(pathname);
			if (file.isFile()) {
				return getObjectMapper().readTree(file);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static JsonNode getJsonNodeFromText(String text) {
		try {
			return getObjectMapper().readTree(text);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

}
