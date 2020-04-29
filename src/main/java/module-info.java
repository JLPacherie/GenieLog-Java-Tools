module com.genielog.tools {
	exports com.genielog.tools.json;
	exports com.genielog.tools.parameters;
	exports com.genielog.tools;

	requires com.fasterxml.jackson.core;
	requires transitive com.fasterxml.jackson.databind;
	requires org.apache.logging.log4j;
	requires org.apache.logging.log4j.core;
}