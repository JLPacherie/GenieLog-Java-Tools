package com.genielog.tools.parameters;

public class StrParameter extends Parameter {

	private String value;

	//
	// ******************************************************************************************************************
	//

	public StrParameter() {
		super();
	}

	public StrParameter(String n, String v, String m) {
		super(n, v, m);
	}

	public StrParameter(StrParameter other) {
		super(other);
	}

	//
	// ******************************************************************************************************************
	//

	public String getValue() {
		return value;
	}

	protected void doSetValue(String value) {
		this.value = value;
	}


}
