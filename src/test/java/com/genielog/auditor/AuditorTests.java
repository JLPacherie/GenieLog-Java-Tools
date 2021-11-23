package com.genielog.auditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Objects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.genielog.tools.BaseTest;

class AuditorTests extends BaseTest {

	@Test
	@Order(1)
	@DisplayName("Parameter substitution")
	void test_ParamSubstition() {

		_logger.info("Testing parameter substitution with a description of the checker");
		_logger.info("refering to the value of a parameter.");

		TestChecker checker = new TestChecker();
		checker.setName("My Checker");
		checker.setDescription("Test checker configured with aParam1=${aParam1}");
		checker.set("aParam1", "Hello");
		checker.set("aParam2", "Copy of aParam1=${aParam1}");

		assertEquals("My Checker", checker.getName());
		
		assertEquals("Test checker configured with aParam1=Hello", 
				checker.getDescription(),
				"Invalid subsitution of an attribute refering to a parameter");
		
		assertEquals("Copy of aParam1=${aParam1}", 
				checker.aParam2,
				"Invalid definition of a parameter refering to another parameter");
		
		assertEquals("Copy of aParam1=Hello", 
				checker.get("aParam2"),
				"Invalid subsitution of a parameter refering to another parameter");

	}

	@Test
	@Order(2)
	@DisplayName("Checker Serialization")
	void test_ParamSerialization() {

		TestChecker checker1 = new TestChecker();
		checker1.setName("My Checker");
		checker1.setDescription("No description");
		checker1.set("aParam1", "Hello1");
		checker1.set("aParam2", "Hello2");

		JsonNode jsonChecker = checker1.saveAsJson();

		TestChecker checker2 = new TestChecker();
		checker2.load(jsonChecker);

		assertEquals(checker1.getName(), checker2.getName());
		assertEquals(checker1.getDescription(), checker2.getDescription());
		assertEquals(checker1.getName(), checker2.getName());
		assertEquals(checker1.get("aParam1"), checker2.get("aParam1"), "Invalid serialization of Parameter");
		assertEquals(checker1.get("aParam2"), checker2.get("aParam2"), "Invalid serialization of Parameter");

	}

	@Test
	@Order(3)
	@DisplayName("Sample IS_NULL Checker")
	void test_IsNullChecker() {

		TestChecker checkerNotNull = new TestChecker();
		checkerNotNull.setName("IS_NULL");
		checkerNotNull.setDescription("Trigger a defect for subject not null.");

		// When TRUE, the predicate triggers a defect
		checkerNotNull.setPredicate(Objects::nonNull);

		TestDefect defect = null;

		// True Negative : 
		_logger.info("Testing True Negative for IS_NULL ...");
		defect = checkerNotNull.doCheck(null);
		assertEquals(null, defect,"Null subject not detected ?");

		// True Positive :
		_logger.info("Testing True Positive for IS_NULL ...");
		defect = checkerNotNull.doCheck("Hello");
		
		assertNotEquals(null, defect,"False positive: Not null subject triggered a defect");
		assertEquals(checkerNotNull, defect.checker(),"Defect's check is not the right one ?");
		assertEquals("Hello", defect.subject,"Defect's subject is not the right one ?");

	}
}
