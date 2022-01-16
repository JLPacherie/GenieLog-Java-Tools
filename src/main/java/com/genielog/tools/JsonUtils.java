package com.genielog.tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.ValueNode;

public class JsonUtils {

	protected static Logger logger = LogManager.getLogger(JsonUtils.class);

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
	
	public static String getPrettyJsonString(JsonNode node) {
		String result = null;
		try {
			result = getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(node);
		} catch (JsonProcessingException e) {
			result = node.toString();
		}
		return result;
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
			} else {
				logger.warn("File not found {}", pathname);
			}
		} catch (IOException e) {
			logger.error("Unable to parse JSON at {} : {}", pathname, Tools.getExceptionMessages(e));
			e.printStackTrace();
		}
		return null;
	}

	public static JsonNode getJsonNodeFromText(String text) {
		try {
			return getObjectMapper().readTree(text);
		} catch (IOException e) {
			logger.error("Unable to parse JSON from text {} : {}", text, Tools.getExceptionMessages(e));
			e.printStackTrace();
		}
		return null;
	}

	//
	// ******************************************************************************************************************
	//

	/**
	 * Merge two JSON tree into one i.e mergedInTo.
	 *
	 * @param toBeMerged
	 * @param mergedInTo
	 */
	public static void merge(JsonNode toBeMerged, JsonNode mergedInTo) {
		Iterator<Map.Entry<String, JsonNode>> incomingFieldsIterator = toBeMerged.fields();
		Iterator<Map.Entry<String, JsonNode>> mergedIterator = mergedInTo.fields();

		//
		// Iterates over each of the fields in the source JsonNode
		//
		while (incomingFieldsIterator.hasNext()) {
			
			Map.Entry<String, JsonNode> incomingEntry = incomingFieldsIterator.next();

			JsonNode subNode = incomingEntry.getValue();

			if (subNode.getNodeType().equals(JsonNodeType.OBJECT)) {
				boolean isNewBlock = true;
				mergedIterator = mergedInTo.fields();
				while (mergedIterator.hasNext()) {
					Map.Entry<String, JsonNode> entry = mergedIterator.next();
					if (entry.getKey().equals(incomingEntry.getKey())) {
						merge(incomingEntry.getValue(), entry.getValue());
						isNewBlock = false;
					}
				}
				if (isNewBlock) {
					((ObjectNode) mergedInTo).replace(incomingEntry.getKey(), incomingEntry.getValue());
				}
			} 
			
			else if (subNode.getNodeType().equals(JsonNodeType.ARRAY)) {
				boolean newEntry = true;
				mergedIterator = mergedInTo.fields();
				while (mergedIterator.hasNext()) {
					Map.Entry<String, JsonNode> entry = mergedIterator.next();
					if (entry.getKey().equals(incomingEntry.getKey())) {
						updateArray(incomingEntry.getValue(), entry);
						newEntry = false;
					}
				}
				if (newEntry) {
					((ObjectNode) mergedInTo).replace(incomingEntry.getKey(), incomingEntry.getValue());
				}
			}
			
			ValueNode valueNode = null;
			JsonNode incomingValueNode = incomingEntry.getValue();
			switch (subNode.getNodeType()) {
			case STRING:
				valueNode = new TextNode(incomingValueNode.textValue());
				break;
			case NUMBER:
				valueNode = new IntNode(incomingValueNode.intValue());
				break;
			case BOOLEAN:
				valueNode = BooleanNode.valueOf(incomingValueNode.booleanValue());
			}
			if (valueNode != null) {
				updateObject(mergedInTo, valueNode, incomingEntry);
			}
		}
	}

	private static void updateArray(JsonNode valueToBePlaced, Map.Entry<String, JsonNode> toBeMerged) {
		toBeMerged.setValue(valueToBePlaced);
	}

	private static void updateObject(	JsonNode mergeInTo,
																		ValueNode valueToBePlaced,
																		Map.Entry<String, JsonNode> toBeMerged) {
		boolean newEntry = true;
		Iterator<Map.Entry<String, JsonNode>> mergedIterator = mergeInTo.fields();
		while (mergedIterator.hasNext()) {
			Map.Entry<String, JsonNode> entry = mergedIterator.next();
			if (entry.getKey().equals(toBeMerged.getKey())) {
				newEntry = false;
				entry.setValue(valueToBePlaced);
			}
		}
		if (newEntry) {
			((ObjectNode) mergeInTo).replace(toBeMerged.getKey(), toBeMerged.getValue());
		}
	}
}
