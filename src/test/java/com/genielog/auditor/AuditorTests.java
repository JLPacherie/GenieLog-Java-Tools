package com.genielog.auditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

class AuditorTests  {

	@Test
	void test_ParamSubstition() {
		
		TestChecker checker = new TestChecker();
		checker.setName("My Checker");
		checker.setDescription("No description for ${name}");
		
		assertEquals("My Checker",checker.getName());
		assertEquals("No description for My Checker",checker.getDescription());
		
	}

	@Test
	void test_ParamSerialization() {
		
		TestChecker checker1 = new TestChecker();
		checker1.setName("My Checker");
		checker1.setDescription("No description for ${name}");
		
		JsonNode jsonChecker = checker1.saveAsJson();
		
		TestChecker checker2 = new TestChecker();
		checker2.load(jsonChecker);
		
		assertEquals(checker1.getName(),checker2.getName());
		assertEquals(checker1.getDescription(),checker2.getDescription());
		assertEquals("My Checker",checker2.getName());
		assertEquals("No description for My Checker",checker2.getDescription());
		
	}
	
	@Test 
	void test_NotNullChecker() {

		TestChecker checkerNotNull = new TestChecker();
		checkerNotNull.setName("NOT_NULL");
		checkerNotNull.setDescription("The subject cannot be null.");
		checkerNotNull.setPredicate(Objects::nonNull);

		JsonNode jsonChecker = checkerNotNull.saveAsJson();
		
		TestDefect defect = null;
		
		//_logger.info("Testing True Positive NOT_NULL ...");
		defect = checkerNotNull.doCheck(null);
		assertEquals(null,defect);
		
		defect = checkerNotNull.doCheck("Hello");
		assertNotEquals(null,defect);
		assertEquals(checkerNotNull,defect.checker());
		assertEquals("Hello",defect.subject);
		
	}
}
