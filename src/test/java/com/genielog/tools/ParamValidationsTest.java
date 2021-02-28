package com.genielog.tools;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import com.genielog.tools.parameters.Parameter;
import com.genielog.tools.parameters.StrParameter;

class ParamValidationsTest extends BaseTest {

	@Test
	@Order(1)
	void testCreate_Update_Read_Write() {
		Parameter p1 = new StrParameter("p1", "value1", Parameter.READ_WRITE);

		assertNotNull(p1, "Parameter not created ?");
		assertEquals("p1", p1.getName(), "Bad name.");
		assertEquals("value1", p1.getValue(), "Bad value.");

		p1.setValue("value2");
		assertEquals("p1", p1.getName(), "Bad name.");
		assertEquals("value2", p1.getValue(), "Bad changed value.");

	}

	@Test
	@Order(2)
	void testCreate_Update_Read_Only() {
		Parameter p1 = new StrParameter("p1", "value1", Parameter.READ_ONLY);

		assertNotNull(p1, "Parameter not created ?");
		assertEquals("p1", p1.getName(), "Bad name.");
		assertEquals("value1", p1.getValue(), "Bad value.");

		assertThrows(IllegalStateException.class, () -> p1.setValue("value2"), "Shouldn't be able to do that.");
		assertEquals("p1", p1.getName(), "Bad name.");
		assertEquals("value1", p1.getValue(), "Bad changed value.");

	}

	@Test
	@Order(3)
	void testCreate_Update_With_AuthorizedValues() {
		Parameter p1 = new StrParameter("p1", "value1", Parameter.READ_WRITE);

		assertNotNull(p1 != null, "Parameter not created ?");
		assertEquals("p1", p1.getName(), "Bad name.");
		assertEquals("value1", p1.getValue(), "Bad value.");

		//
		// Setting authorized values
		//
		assertThrows(IllegalArgumentException.class, () -> p1.addAuthorizedValue("value2"),
				"The first authorized value should be the current one");
		p1.addAuthorizedValue("value1");
		p1.addAuthorizedValue("value2");
		p1.addAuthorizedValue("value3");
		p1.addAuthorizedValue("value4");

		//
		// Trying to set a valid value
		//
		assertDoesNotThrow(() -> p1.setValue("value2"), "Submitting a valid value is refused.");
		assertEquals("value2", p1.getValue(), "Bad changed value.");

		//
		// Trying to set an invalid valid value
		//
		assertThrows(IllegalArgumentException.class, () -> p1.setValue("unauthorized value"),
				"The first authorized value should be the current one");
		assertEquals("value2", p1.getValue(), "Invalid value for the parameter.");

	}

	@Test
	@Order(3)
	void testCreate_Update_With_Validators() {
		Parameter p1 = new StrParameter("p1", "any thing", Parameter.READ_WRITE);

		assertNotNull(p1 != null, "Parameter not created ?");
		assertEquals("p1", p1.getName(), "Bad name.");
		assertEquals("any thing", p1.getValue(), "Bad value.");

		//
		// Setting validators values
		//
		assertThrows(
				IllegalArgumentException.class,
				() -> p1.addValidator("Starts with Value", v -> v.startsWith("value")),
				"The first validator should apply to the current value");

		p1.setValue("value1");
		
		assertDoesNotThrow(
				() -> p1.addValidator("Starts with Value", v -> v.startsWith("value")),
				"The first validator should apply to the current value");
		
		assertDoesNotThrow(
				() -> p1.addValidator("Ends with numer", v -> v.matches(".*[0-9]+")),
				"The first validator should apply to the current value");

		//
		// Trying to set a valid value
		//
		assertDoesNotThrow(() -> p1.setValue("value2"), "Submitting a valid value is refused.");
		assertEquals("value2", p1.getValue(), "Bad changed value.");

		//
		// Trying to set an invalid valid value
		//
		assertThrows(IllegalArgumentException.class, () -> p1.setValue("unauthorized value"),
				"The first authorized value should be the current one");
		
		assertEquals("value2", p1.getValue(), "Invalid value for the parameter.");

		//
		// Adding a new bad validator
		//
		assertDoesNotThrow(
				() -> p1.addValidator("Bad one", v -> v.startsWith("XXX")),
				"The second validator should apply to the current value");
		
		//
		// Adding a new bad validator
		//
		assertDoesNotThrow(() -> p1.setValue("value2"),  "Submitting a valid value starting with 'value' is refused.");
		assertDoesNotThrow(() -> p1.setValue("blabla2"), "Submitting a valid value ending with a number is refused.");
		assertDoesNotThrow(() -> p1.setValue("XXXhdgf"), "Submitting a valid value starting with XXX is refused.");
		assertThrows(IllegalArgumentException.class,() -> p1.setValue("blabla"), "Submitting a valid value is refused.");


	}

	@Test
	@Order(4)
	void testCreate_Update_With_Authorized_And_Validators() {
		Parameter p1 = new StrParameter("p1", "any thing", Parameter.READ_WRITE);

		assertNotNull(p1 != null, "Parameter not created ?");
		assertEquals("p1", p1.getName(), "Bad name.");
		assertEquals("any thing", p1.getValue(), "Bad value.");

		//
		// Setting 2 validators one saying the value should start with 'value' 
		// and one saying the value should ends with a number
		//

		// Initialize the parameter with a validated value. Otherwise the validators won't
		// be added.
		//
		p1.setValue("value1");
		
		assertDoesNotThrow(
				() -> p1.addValidator("Starts with 'value'", v -> v.startsWith("value")),
				"The first validator should apply to the current value");
		
		assertDoesNotThrow(
				() -> p1.addValidator("Ends with number", v -> v.matches(".*[0-9]+")),
				"The first validator should apply to the current value");

		
		//
		// Setting authorized values
		//
		assertDoesNotThrow(() -> p1.addAuthorizedValue("value1"),"Can't add a valid authorized value");
		assertDoesNotThrow(() -> p1.addAuthorizedValue("value2"),"Can't add a valid authorized value");
		assertDoesNotThrow(() -> p1.addAuthorizedValue("value3"),"Can't add a valid authorized value");
		assertDoesNotThrow(() -> p1.addAuthorizedValue("value4"),"Can't add a valid authorized value");


		//
		// Trying to set a valid value
		//
		assertDoesNotThrow(() -> p1.setValue("value1"), "Submitting a valid value is refused.");
		assertDoesNotThrow(() -> p1.setValue("value2"), "Submitting a valid value is refused.");
		assertDoesNotThrow(() -> p1.setValue("value3"), "Submitting a valid value is refused.");
		assertDoesNotThrow(() -> p1.setValue("value4"), "Submitting a valid value is refused.");

		//
		// Trying to set a invalid value
		//
		assertThrows(IllegalArgumentException.class,() -> p1.setValue("XXX"), "Submitting a valid value is refused.");
		assertThrows(IllegalArgumentException.class,() -> p1.setValue("valueabc"), "Submitting a valid value is refused.");
		assertThrows(IllegalArgumentException.class,() -> p1.setValue("abc3"), "Submitting a valid value is refused.");


	}

	@Test
	@Order(4)
	void testCreate_Update_With_Authorized_And_Validators_Inverted() {
		Parameter p1 = new StrParameter("p1", "any thing", Parameter.READ_WRITE);

		assertNotNull(p1 != null, "Parameter not created ?");
		assertEquals("p1", p1.getName(), "Bad name.");
		assertEquals("any thing", p1.getValue(), "Bad value.");


		// Initialize the parameter with a validated value. Otherwise the validators won't
		// be added.
		//
		p1.setValue("value1");
		
		//
		// Setting authorized values
		//
		assertDoesNotThrow(() -> p1.addAuthorizedValue("value1"),"Can't add a valid authorized value");
		assertDoesNotThrow(() -> p1.addAuthorizedValue("value2"),"Can't add a valid authorized value");
		assertDoesNotThrow(() -> p1.addAuthorizedValue("value3"),"Can't add a valid authorized value");
		assertDoesNotThrow(() -> p1.addAuthorizedValue("value4"),"Can't add a valid authorized value");

		//
		// Setting 2 validators one saying the value should start with 'value' 
		// and one saying the value should ends with a number
		//

		assertDoesNotThrow(
				() -> p1.addValidator("Starts with 'value'", v -> v.startsWith("value")),
				"The first validator should apply to the current value");
		
		assertDoesNotThrow(
				() -> p1.addValidator("Ends with number", v -> v.matches(".*[0-9]+")),
				"The first validator should apply to the current value");

		


		//
		// Trying to set a valid value
		//
		assertDoesNotThrow(() -> p1.setValue("value1"), "Submitting a valid value is refused.");
		assertDoesNotThrow(() -> p1.setValue("value2"), "Submitting a valid value is refused.");
		assertDoesNotThrow(() -> p1.setValue("value3"), "Submitting a valid value is refused.");
		assertDoesNotThrow(() -> p1.setValue("value4"), "Submitting a valid value is refused.");

		//
		// Trying to set a invalid value
		//
		assertThrows(IllegalArgumentException.class,() -> p1.setValue("XXX"), "Submitting a valid value is refused.");
		assertThrows(IllegalArgumentException.class,() -> p1.setValue("valueabc"), "Submitting a valid value is refused.");
		assertThrows(IllegalArgumentException.class,() -> p1.setValue("abc3"), "Submitting a valid value is refused.");


	}

}
