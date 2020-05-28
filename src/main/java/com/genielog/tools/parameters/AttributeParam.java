package com.genielog.tools.parameters;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class AttributeParam<T> extends AParameter<T> {

	private static final long serialVersionUID = -1850243469246367740L;
	private Supplier<T> getter;
	private Consumer<T> setter;

	public AttributeParam(String name, Supplier<T> getter, Consumer<T> setter) {
		super(name, null, (setter == null) ? READ_ONLY : READ_WRITE);
		this.getter = getter;
		this.setter = setter;
	}

	@Override
	public T getValue() {
		return (getter != null) ? getter.get() : null;
	}

	@Override
	protected void doSetValue(T value) {
		if (setter != null)
			setter.accept(value);
	}

}
