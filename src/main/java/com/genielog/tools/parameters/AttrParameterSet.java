package com.genielog.tools.parameters;

import java.io.Serializable;

public class AttrParameterSet extends AParameterSet<Object> implements Serializable {

	private static final long serialVersionUID = -2713284450963955130L;

	//
	// ******************************************************************************************************************
	//

	public AttrParameterSet() {
		super();
	}

	//
	// ******************************************************************************************************************
	//

	protected AttributeParam<Object> makeParameter() {
		return null;
	}

	//
	// ******************************************************************************************************************
	//

	public Object getDefinition(String name, String defaultValue) {
		AParameter<Object> p = search(name, true);
		return (p != null) ? p.getValue() : defaultValue;
	}

	//
	// ******************************************************************************************************************
	//

	public boolean getAsBoolean(String name, boolean defaultValue) {
		Object value = get(name, null);

		if (value == null)
			return defaultValue;

		if (value instanceof String) {
			return Boolean.parseBoolean(process((String) value));
		}
		
		if (value instanceof Boolean) {
			return (boolean) value;
		}

		return defaultValue;
	}

	public int getAsInteger(String name, int defaultValue) {
		Object value = get(name, null);

		if (value == null)
			return defaultValue;

		// If the value refers to another field '${other_field}' which can be an int
		if (value instanceof String) {
			value = process((String) value);
			return Integer.parseInt((String) value);
		}

		if (value instanceof Integer) {
			return (int) value;
		}

		// Any other type return by get() ?
		return defaultValue;
	}

	public long getAsLong(String name, int defaultValue) {
		Object value = get(name, null);

		if (value == null)
			return defaultValue;

		if (value instanceof Long) {
			return (Long) value;
		}

		// If the value refers to another field '${other_field}' which can be an int
		if (value instanceof String) {
			value = process((String) value);
			return Long.parseLong((String) value);
		}


		return defaultValue;
	}

	public String getAsString(String name, String defaultValue) {
		Object value = get(name, null);

		if (value == null)
			return defaultValue;

		if (value instanceof String) {
			return process((String) value);
		}

		return value.toString();
	}

	//
	// ******************************************************************************************************************
	//

	@Override
	public Object get(String name, Object defaultValue, boolean incParent) {
		Object result = super.get(name, defaultValue, incParent);
		if (result instanceof String) {
			result = process((String) result);
		}
		return result;
	}

	/**
	 * Replaces the parameters names reference by their value in the given string.
	 * 
	 * @param srcStr
	 *          : The string with the parameters references
	 * @return The srcStr string bu with the parameter substitutions.
	 */
	public String process(final String srcStr) {
		String result = srcStr;
		boolean again = true;

		while (again) {
			String[] paramNames = AParameter.getReferencedParameterNames(result, "${", "}");
			again = false;
			int index = 0;
			while (index < paramNames.length) {
				String name = paramNames[index];
				String refName = "${" + name + "}";
				Object rawValue = get(name, null, true);
				String value = (rawValue == null) ? null : rawValue.toString();
				if (value != null) {
					if (value.indexOf(refName) == -1) {
						result = result.replace(refName, value);
						again = true;
					} else {
						again = false;
						index = paramNames.length + 1;
						_logger.error("While processing '{}' auto reference detected for parameter '{}'.", srcStr, name);
					}
				}
				index++;
			}
		}

		return result;
	}

}
