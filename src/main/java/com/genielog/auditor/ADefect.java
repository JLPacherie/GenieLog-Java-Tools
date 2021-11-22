package com.genielog.auditor;

import java.io.Serializable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder( { "checker_name","checker_description","shortMessage","longMessage" })
public abstract class ADefect<C extends AChecker<S,? extends ADefect<C,S>>, S > implements Serializable {

	private static final long serialVersionUID = 7236397752439750703L;

	protected transient Logger _logger;

	protected C checker;
	protected S subject;
	
	protected ADefect(C checker, S subject) {
		_logger = LogManager.getLogger(this.getClass());
		this.checker = checker;
		this.subject = subject;
	}
	
	public C checker() {
		return this.checker;
	}
	
	public S subject() {
		return this.subject;
	}
	
}
