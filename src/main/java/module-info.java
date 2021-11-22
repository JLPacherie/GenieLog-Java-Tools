// The open qualifier is required to allow code inspection for JUnit.
open module com.genielog.tools {
	
	exports com.genielog.auditor;
	exports com.genielog.tools.parameters;
	exports com.genielog.tools.functional;
	exports com.genielog.tools;

	requires com.fasterxml.jackson.core;
	requires transitive com.fasterxml.jackson.databind;
	requires org.apache.logging.log4j;
	requires org.apache.logging.log4j.core;
	requires org.apache.commons.lang3;
	requires javatuples;
	requires transitive commons.cli;
	requires org.apache.commons.io;


}