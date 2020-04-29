module com.genielog.tools {
	exports com.genielog.tools.json;
	exports com.genielog.tools.parameters;
	exports com.genielog.tools;

	requires com.fasterxml.jackson.core;
	requires com.fasterxml.jackson.databind;
	requires log4j.api;
}