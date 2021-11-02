package com.genielog.tools.parameters;

import java.util.stream.Stream;

import com.genielog.tools.functional.SerializableConsumer;
import com.genielog.tools.functional.SerializableSupplier;

public class AttributeWrapper {
	
	protected transient  ParameterSet _parameters;
	
	public AttributeWrapper() {
		_parameters = new ParameterSet();
	}

	public void add(String paramName, SerializableSupplier<String> getter, SerializableConsumer<String> setter) {
		_parameters.add(new AttributeParam<>(paramName,getter,setter));
	}
	
	public String get(String paramName) {
		return _parameters.process(_parameters.get(paramName));
	}

	public void set(String paramName, String value) {
		_parameters.set(paramName,value);
	}

	public boolean hasAttribute(String paramName) {
		return _parameters.has(paramName);
	}
	
	public Stream<String> attrNames() {
		return _parameters.names(false);
	}
	
}
