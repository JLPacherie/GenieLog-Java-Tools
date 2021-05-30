// The open qualifier is required to allow code inspection for JUnit.
open module com.genielog.tools {
  exports com.genielog.tools.json;
  exports com.genielog.tools.parameters;
  exports com.genielog.tools.functional;
  exports com.genielog.tools;

  requires com.fasterxml.jackson.core;
  requires com.fasterxml.jackson.databind;
  requires org.apache.logging.log4j;
  requires org.apache.logging.log4j.core;
	requires org.apache.commons.lang3;

}