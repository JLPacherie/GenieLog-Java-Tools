package com.genielog.tools;

import com.genielog.tools.parameters.Parameter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

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

    /**
     * Rigorous Test :-)
     */
    @Test
    public void shouldAnswerWithTrue()
    {
        assertTrue( true );
    }


    @Test
    public void testParameters() {

        Parameter p1 = new Parameter("p1","value of p1",Parameter.READ_WRITE);
        _logger.info("Value of p1 : '{}'",p1.getValue());
        assertEquals(p1.getValue(),"value of p1");

        Parameter p2 = new Parameter("p2","value of p2",Parameter.READ_ONLY);
        _logger.info("Value of p2 : '{}'",p2.getValue());
        assertEquals(p2.getValue(),"value of p2");

        assertThrows(Exception.class,() -> p2.setValue("new value for p2"));
        assertEquals("value of p2",p2.getValue());
    }
}
