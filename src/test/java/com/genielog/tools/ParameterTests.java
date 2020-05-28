package com.genielog.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.genielog.tools.parameters.Parameter;
import com.genielog.tools.parameters.StrParameter;

/**
 * Unit test for simple App.
 */
public class ParameterTests
{
    protected static Logger _logger;

    @BeforeAll
    static void initAll() throws IOException {
        String log4jConfigFile = "./tests/log4j2.xml";
        InputStream streamConfig = new FileInputStream(log4jConfigFile);
        ConfigurationSource source = new ConfigurationSource(streamConfig);
        Configurator.initialize(null, source);
        _logger = LogManager.getLogger(ParameterTests.class);
        streamConfig.close();
    }

    @Test
    public void testParameters() {

        Parameter p1 = new StrParameter("p1","value of p1",Parameter.READ_WRITE);
        _logger.info("Value of p1 : '{}'",p1.getValue());
        assertEquals(p1.getValue(),"value of p1");

        Parameter p2 = new StrParameter("p2","value of p2",Parameter.READ_ONLY);
        _logger.info("Value of p2 : '{}'",p2.getValue());
        assertEquals(p2.getValue(),"value of p2");

        assertThrows(Exception.class,() -> p2.setValue("new value for p2"));
        assertEquals("value of p2",p2.getValue());
    }
}
