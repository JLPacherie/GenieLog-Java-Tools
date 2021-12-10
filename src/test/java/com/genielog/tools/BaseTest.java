package com.genielog.tools;

import java.io.FileInputStream;
import java.io.IOException;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class BaseTest {

  protected static Logger _logger;
  protected Chrono _testDuration;
  
  @BeforeAll
  static protected void setUpBeforeClass() throws Exception {
    String log4jConfigFile = "./tests/log4j2.xml";

    try (FileInputStream cfgFile = new FileInputStream(log4jConfigFile)) {
      ConfigurationSource source = new ConfigurationSource(cfgFile);
      Configurator.initialize(null, source);
      _logger = LogManager.getLogger(BaseTest.class);
    } catch (IOException e) {
    }
  }

  @AfterAll
  static void tearDownAfterClass() throws Exception {
  }

  public String makeBoxedHeader(String head) {
  	int maxLength = head.lines().mapToInt(String::length).max().orElse(0);
  	StringBuffer sb = new StringBuffer();
  	sb.append("*".repeat(maxLength+6));
  	sb.append(System.lineSeparator());
  	head.lines()
  		.map( line -> line + " ".repeat(maxLength - line.length()))
  		.forEach( line -> {
  	  	sb.append("** ");
  	  	sb.append(line);
  	  	sb.append(" **");
  	  	sb.append(System.lineSeparator());
  		});
  	sb.append("*".repeat(maxLength+6));
  	sb.append(System.lineSeparator());
  	return sb.toString();
  }
  
  public void mlLogger(Logger logger, Level level, String prefix, String message) {
  	logger.log(level,message.lines().findFirst().orElse(""));
  	message.lines()
  	.skip(1)
  	.forEach( line -> {
  			logger.log(level,prefix + line);
  	});
  }
  
  @BeforeEach
  void setUp(TestInfo testInfo) throws Exception {
    _logger.info("");
    mlLogger(_logger,Level.INFO,"",makeBoxedHeader("Starting new test " + testInfo.getDisplayName()));
    _testDuration = new Chrono();
    _testDuration.start();
  }

  @AfterEach
  void tearDown(TestInfo testInfo) throws Exception {
    _testDuration.stop();
    mlLogger(_logger,Level.INFO,"",makeBoxedHeader(String.format("Test executed in %s ",_testDuration.toString())));
  }

}
