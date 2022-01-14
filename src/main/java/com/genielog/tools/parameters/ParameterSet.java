package com.genielog.tools.parameters;

import java.io.Serializable;
import java.util.Map;

import com.genielog.tools.StringUtils;

public class ParameterSet extends AParameterSet<String> implements Serializable {

	private static final long serialVersionUID = -2713284450963955130L;

	//
	// ******************************************************************************************************************
	//

	public ParameterSet() {
		super();
	}

	public ParameterSet(Map<String, String> params) {
		for (Map.Entry<String, String> item : params.entrySet()) {
			add(item.getKey(), item.getValue(), AParameter.READ_ONLY);
		}
	}

	//
	// ******************************************************************************************************************
	//

	public static ParameterSet makeParameterSetFromEnv() {
		ParameterSet result = new ParameterSet(System.getenv());
		result.setOwner("Operating System");
		return result;
	}

	protected AParameter<String> makeParameter() {
		return new StrParameter();
	}
	
	//
	// ******************************************************************************************************************
	//

	public String getDefinition(String name, String defaultValue) {
		AParameter<String> p = search(name, true);
		return (p != null) ? p.getValue() : defaultValue;
	}

	//
	// ******************************************************************************************************************
	//

	public boolean getAsBoolean(String name, boolean defaultValue) {
		String strValue = get(name, null);
		return (strValue == null) ? defaultValue : Boolean.parseBoolean(strValue);
	}

	public int getAsInteger(String name, int defaultValue) {
		String strValue = get(name, null);
		return (strValue == null) ? defaultValue : Integer.parseInt(strValue);
	}

	public long getAsLong(String name, int defaultValue) {
		String strValue = get(name, null);
		return (strValue == null) ? defaultValue : Long.parseLong(strValue);
	}

	//
	// ******************************************************************************************************************
	//

	@Override
	public String get(String name, String defaultValue, boolean incParent) {
		String result = super.get(name,defaultValue,incParent);
		if (result != null) {
			result = process(result);
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
		if (result != null) {
			result = StringUtils.resolve(result, "${", "}", name -> {
				Object rawValue = get(name, null, true);
				return (rawValue == null) ? null : rawValue.toString();
			});
		}
		return result;
	}

}
