package com.genielog.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.genielog.tools.parameters.Parameter;
import com.genielog.tools.parameters.StrParameter;

/**
 * Unit test for simple App.
 */
class ParameterTests extends BaseTest {

	@Test
	void testParameters() {

		Parameter p1 = new StrParameter("p1", "value of p1", Parameter.READ_WRITE);
		_logger.info("Value of p1 : '{}'", p1.getValue());
		assertEquals(p1.getValue(), "value of p1");

		Parameter p2 = new StrParameter("p2", "value of p2", Parameter.READ_ONLY);
		_logger.info("Value of p2 : '{}'", p2.getValue());
		assertEquals(p2.getValue(), "value of p2");

		assertThrows(Exception.class, () -> p2.setValue("new value for p2"));
		assertEquals("value of p2", p2.getValue());
	}
}
