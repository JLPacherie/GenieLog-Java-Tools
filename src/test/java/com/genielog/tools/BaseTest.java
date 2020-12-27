package com.genielog.tools;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public class BaseTest {
	protected static Logger _logger;

	@BeforeAll
	static void setUpBeforeClass() throws Exception {

		// ----------------------------------------------------------------------------------------------------------------
		// Log4J Initialization
		// ----------------------------------------------------------------------------------------------------------------

		String log4jConfigFile = "tests/log4j2.xml";
		try (InputStream streamConfig = new FileInputStream(log4jConfigFile)) {
			ConfigurationSource source = new ConfigurationSource(streamConfig);
			Configurator.initialize(null, source);
		} catch (IOException e) {
			System.err.println("Unable to initialize Log4J from " + log4jConfigFile);
			System.err.println(e.getLocalizedMessage());
		}
	}

	@BeforeEach
	void setUp() throws Exception {
		_logger = LogManager.getLogger(this.getClass());
	}

}
