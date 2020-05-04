// The open qualifier is required to allow code inspection for JUnit.
open module com.genielog.tools {
	exports com.genielog.tools.json;
	exports com.genielog.tools.parameters;
	exports com.genielog.tools;

	requires com.fasterxml.jackson.core;
	requires transitive com.fasterxml.jackson.databind;
	//requires log4j.core;
	requires org.apache.logging.log4j;
	requires org.apache.logging.log4j.core;

}