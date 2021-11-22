package com.genielog.auditor;

public class TestDefect extends ADefect<TestChecker,Object> {

	protected TestDefect(TestChecker checker, Object subject) {
		super(checker, subject);
	}

}
