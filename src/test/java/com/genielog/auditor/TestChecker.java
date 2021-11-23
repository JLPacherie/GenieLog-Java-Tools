package com.genielog.auditor;

import java.util.function.Predicate;
import java.util.stream.Stream;

public class TestChecker extends AChecker<Object,TestDefect> {

	private static final long serialVersionUID = 4392800091874741761L;
	private transient Predicate<Object> _predicate;
	
	private String aParam1;
	public String aParam2;

	public TestChecker() {
		aParam1 = "Default Value";
		aParam2 = "Default Value";
		addFieldAsParameter("aParam1","aParam2");
	}

	public Predicate<Object> setPredicate(Predicate<Object> predicate) {
		_predicate = predicate;
		return _predicate;
	}
	
	@Override
	public boolean isValidSubject(Object subject) {
		return true;
	}
	
	@Override
	protected TestDefect doCheck(Object subject) {
		_logger.info("Checker {} is testing subject '{}'",getName(),subject);
		return (_predicate.test(subject)) ? new TestDefect(this,subject) : null;
	}

	@Override
	public boolean isValid() {
		return super.isValid() && _predicate != null;
	}

	@Override
	public boolean setUp() {
		return true;
	}

	@Override
	public boolean tearDown() {
		return true;
	}

	@Override
	public Stream<? extends Object> getSubjects() {
		// TODO Auto-generated method stub
		return null;
	}

}
