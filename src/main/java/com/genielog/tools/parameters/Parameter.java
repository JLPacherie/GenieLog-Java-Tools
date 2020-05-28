package com.genielog.tools.parameters;

public abstract class Parameter extends AParameter<String> {

	private static final long serialVersionUID = -6985550602573254032L;

	//
	// ******************************************************************************************************************
	//


	public Parameter() {
		super();
	}

	public Parameter(String n, String v, String m) {
		super(n,v,m);
	}

	public Parameter(Parameter other) {
		super(other);
	}

}
