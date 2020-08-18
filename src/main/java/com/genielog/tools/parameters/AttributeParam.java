package com.genielog.tools.parameters;

import com.genielog.tools.functional.SerializableConsumer;
import com.genielog.tools.functional.SerializableSupplier;

public class AttributeParam<T> extends AParameter<T> {

	private static final long serialVersionUID = -1850243469246367740L;
	private SerializableSupplier<T> getter;
	private SerializableConsumer<T> setter;

	public AttributeParam(String name, SerializableSupplier<T> getter, SerializableConsumer<T> setter) {
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
