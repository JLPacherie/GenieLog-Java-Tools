package com.genielog.tools.parameters;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.stream.Stream;

import com.genielog.tools.functional.SerializableConsumer;
import com.genielog.tools.functional.SerializableSupplier;

public class AttributeWrapper {

	protected transient AttrParameterSet _parameters;

	public AttributeWrapper() {
		_parameters = new AttrParameterSet();
	}

	private Stream<Field> getFieldsFromClass(Class<?> clazz) {
		return (clazz == null) ? Stream.empty()
				: Stream.concat(
						Arrays.stream(clazz.getDeclaredFields()),
						getFieldsFromClass(clazz.getSuperclass()));
	}

	public void addFieldAsParameter(String... fieldNames) {
		getFieldsFromClass(this.getClass())
				.filter(field -> Arrays.stream(fieldNames).anyMatch(name -> field.getName().matches(name)))
				.forEach(field -> {
					add(field.getName(),
							() -> {
								try {
									return field.get(this);
								} catch (IllegalAccessException | IllegalArgumentException e) {

								}
								return null;
							},
							v -> {
								Class<?> type = field.getType();
								try {
									field.setAccessible(true);
									if (type.isAssignableFrom(v.getClass())) {
										field.set(this, v);
									} else if (v instanceof String) {
										try {
											if (type.isAssignableFrom(Integer.class) || "int".equals(type.getTypeName())) {
												field.set(this, Integer.parseInt((String) v));
											} else if (type.isAssignableFrom(Long.class) || "long".equals(type.getTypeName())) {
												field.set(this, Long.parseLong((String) v));
											} else if (type.isAssignableFrom(Double.class) || "double".equals(type.getTypeName())) {
												field.set(this, Long.parseLong((String) v));
											}
										} catch (NumberFormatException e) {
											_parameters._logger.error("Unable to parse {} to configure parameter {} in {}",
													v, field.getName(), _parameters.owner);
										}
									}
								} catch (IllegalAccessException | IllegalArgumentException e) {
									String msg = String.format("value %s of type %s incompatible with declared type %s",
											v.toString(), v.getClass().getCanonicalName(), type.getCanonicalName());

									throw new IllegalArgumentException(msg);

								}
							});
				});
	}

	public void add(String paramName, SerializableSupplier<Object> getter, SerializableConsumer<Object> setter) {
		_parameters.add(new AttributeParam<>(paramName, getter, setter));
	}

	public Object get(String paramName) {
		return _parameters.get(paramName);
	}

	public void set(String paramName, Object value) {
		_parameters.set(paramName, value);
	}

	public boolean hasAttribute(String paramName) {
		return _parameters.has(paramName);
	}

	public Stream<String> attrNames() {
		return _parameters.names(false);
	}

}
