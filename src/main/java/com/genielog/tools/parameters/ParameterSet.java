package com.genielog.tools.parameters;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ParameterSet implements Serializable {

	private static final long serialVersionUID = -2713284450963955130L;
	protected transient Logger _logger = null;
	protected transient List<ParameterSet> _parents = new ArrayList<>();
	protected transient Object owner = null;
	protected transient List<BiConsumer<String, String>> _changeHandlers = new ArrayList<>();

	private HashMap<String, Parameter> _allParams = new HashMap<>();

	//
	// ******************************************************************************************************************
	//

	public ParameterSet() {
		_logger = LogManager.getLogger(this.getClass());
	}

	public ParameterSet(Map<String, String> params) {
		for (Map.Entry<String, String> item : params.entrySet()) {
			add(item.getKey(), item.getValue(), Parameter.READ_ONLY);
		}
	}

	//
	// ******************************************************************************************************************
	//

	public Map<String, String> mapExport() {
		HashMap<String, String> result = new HashMap<>();
		for (Parameter param : _allParams.values()) {
			result.put(param.getName(), param.getValue());
		}
		return result;
	}

	public void mapImport(Map<String, String> map) {
		for (Map.Entry<String, String> entry : map.entrySet()) {
			add(entry.getKey(), entry.getValue(), Parameter.READ_WRITE);
		}
	}

	public static ParameterSet makeParameterSetFromEnv() {
		ParameterSet result = new ParameterSet(System.getenv());
		result.setOwner("Operating System");
		return result;
	}

	//
	// ******************************************************************************************************************
	//
	public Stream<Parameter> parameters(boolean incParents) {
		List<Parameter> result = new ArrayList<>();
		result.addAll(_allParams.values());
		if (!_parents.isEmpty() && incParents) {

			_parents.stream()//
					.flatMap(parent -> parent.parameters(true)).forEach(parentParameter -> {
						//
						// Search if the parent parameter can be overwritten
						//
						Parameter finalParameter = result.stream()//
								.filter(p -> p.getName().equals(parentParameter.getName()))//
								.findFirst()//
								.orElse(null);

						if (finalParameter == null) {
							// No problem, the parent parameter is unknown
							result.add(parentParameter);
						} else {
							if (parentParameter.isWritable()) {
								// The parent parameter can be redefine, just ignore it
								// _logger.warn("In Set for " + this.getOwner() + " the unlocked parameter " + parentParameter
								// + " is ignored and redefined by " + finalParameter);
							} else {
								if (finalParameter.isWritable()) {
									// _logger.warn("In Set for " + this.getOwner() + " the unlocked parameter " + parentParameter
									// + " is replaced by " + finalParameter);
									result.remove(finalParameter);
									result.add(parentParameter);
								} else {
									String finalValue = finalParameter.getValue();
									String parentValue = parentParameter.getValue();
									if (finalValue.equals(parentValue)) {
										// _logger.warn("In Set for " + this.getOwner() + " the locked parameters " + parentParameter
										// + " and " + finalParameter + " conflict but have same value");
									} else {
										// Ouch, there a parameter in the children that overwrite a locked parent parameter
										_logger.error("In Set for {} the locked parameter {} is redefined by {}", getOwner(),
												parentParameter, finalParameter);
									}
								}
							}
						}

					});
		}
		return result.stream();
	}

	public Stream<String> names(boolean incParents) {
		return parameters(incParents).map(Parameter::getName);
	}

	//
	// ******************************************************************************************************************
	//

	public Parameter getParameterByName(String name, boolean incParent) {
		return parameters(incParent).filter(p -> p.getName().equals(name)).findFirst().orElse(null);
	}

	public boolean hasName(String name) {
		return getParameterByName(name, false) != null;
	}

	/** Returns true if the given name is one of a READ ONLY parameter in the parents. */
	public boolean isLockedByParents(String name) {
		return getParents() // Look into each ParameterSet in the parents
				.map(parent -> parent.getParameterByName(name, false)) // For each Parent, search for a synonym
				.anyMatch(synonym -> {
					if (synonym != null) {
						if (synonym.isWritable()) {
							return false;
						} else {
							_logger.debug("name '{}' is locked by {}", name, synonym.getSet().getOwner());
							return true;
						}
					}
					return false;
				});
	}

	/** Returns true if the given name is already in use or locked by parents. */
	public boolean isLocked(String name) {
		return hasName(name) || isLockedByParents(name);
	}
	//
	// ******************************************************************************************************************
	//

	/** Change the value of an existing read/write parameter. Returns true if set occurred. */
	public boolean set(String name, String value) {
		Parameter p = search(name, false);
		if (p != null) {
			if (p.getMode().equals(Parameter.READ_WRITE)) {
				if ((p.getValue() != null) && (!p.getValue().equals(value))) {
					applyListeners(name, value);
					p.setValue(value);
				}
				return true;
			} else {
				_logger.error("Attempt to write READ ONLY parameter: {}", p.getName());
				throw new IllegalArgumentException("Can't write read only parameter " + name);
			}
		} else {
			_logger.error("Attempt to write undefined parameter: {}", name);
			throw new IllegalArgumentException("Can't write missing parameter " + name);
		}
		// return false;
	}

	//
	// ******************************************************************************************************************
	//

	/**
	 * Add or update a parameter with a new value. Returns true if change occurred.
	 * 
	 * @param name
	 *          The name of the parameter to add/update
	 * @param value
	 *          The value of the parameter
	 * @param mode
	 *          Is it a read/write or read only parameter.
	 * @return True if the parameter is added or updated. False if a parameter already exists and is read only
	 */
	public Parameter add(String name, String value, String mode) {
		Parameter p = search(name, false);
		if (p != null) {
			if (mode.equals(p.getMode()) && p.isWritable()) {
				p.setValue(value);
				applyListeners(name, value);
			} else {
				_logger.debug("Can't add twice the same parameter {}, with different mode {}.", name, mode);
				p = null;
				throw new IllegalArgumentException(
						"Can't create twice the same parameter, check param name colision for " + name);
			}
		} else {
			p = new Parameter(name, value, mode);
			p.setSet(this);
			_allParams.put(name, p);
			applyListeners(name, value);
		}
		return p;
	}

	//
	// ******************************************************************************************************************
	//

	public void add(ParameterSet other) {
		if (other != null) {
			other.parameters(false).forEach(p -> add(p));
			_parents.addAll(other._parents);
		}
	}

	//
	// ******************************************************************************************************************
	//

	public Parameter add(Parameter p) {
		Parameter copy = new Parameter(p);
		_allParams.put(p.getName(), copy);
		return copy;
	}

	//
	// ******************************************************************************************************************
	//

	public int removeByPattern(String pattern) {
		List<Parameter> matches = parameters(false)//
				.filter(p -> p.getName().matches(pattern))//
				.collect(Collectors.toList());

		matches.forEach(p -> remove(p));

		return matches.size();
	}

	public boolean remove(String name) {
		Parameter p = search(name, false);
		return remove(p);
	}

	public boolean remove(Parameter p) {
		if (p != null) {
			_allParams.remove(p.getName());
			return true;
		}
		return false;
	}

	public void clear() {
		_allParams.clear();
	}

	//
	// ******************************************************************************************************************
	//

	public String getDefinition(String name, String defaultValue) {
		Parameter p = search(name, true);
		return (p != null) ? p.getValue() : defaultValue;
	}

	//
	// ******************************************************************************************************************
	//

	public String get(String name) {
		return get(name, null, true);
	}

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

	public String get(String name, String defaultValue) {
		return get(name, defaultValue, true);
	}

	//
	// ******************************************************************************************************************
	//

	public String get(String name, String defaultValue, boolean incParent) {
		Parameter p = search(name, incParent);
		String result = (p != null) ? p.getValue() : defaultValue;
		if (result != null) {
			result = process(result);
		}
		return result;
	}

	//
	// ******************************************************************************************************************
	//

	public int getParamCount() {
		return (int) parameters(true).count();
	}

	//
	// ******************************************************************************************************************
	//

	public boolean has(String name) {
		return search(name, true) != null;
	}

	public boolean hasParent(ParameterSet other) {
		return _parents.stream().anyMatch(parent -> (parent == other) || (parent.hasParent(other)));
	}

	public Stream<ParameterSet> getParents() {
		return _parents.stream();
	}

	public void addParent(ParameterSet other) {
		if (hasParent(other)) {
			_logger.error("Duplicate parent detected");
			return;
		}
		if ((other == this) || other.hasParent(this)) {
			_logger.error("Cyclic dependency detected in Parameter hierarchy");
			return;
		}

		_parents.add(other);
	}

	public void setOwner(Object obj) {
		owner = obj;
	}

	public Object getOwner() {
		return owner;
	}

	// ******************************************************************************************************************

	public Parameter search(String name, boolean incParent) {

		Parameter result = _allParams.get(name);

		int iParent = 0;
		while ((result == null) && (incParent) && (iParent < _parents.size())) {
			result = _parents.get(iParent).search(name, true);
			if (result != null)
				return result;
			iParent++;
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
			String[] paramNames = Parameter.getReferencedParameterNames(result, "${", "}");
			again = false;
			int index = 0;
			while (index < paramNames.length) {
				String name = paramNames[index];
				String refName = "${" + name + "}";
				String value = get(name, null, true);
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

	public String toString() {
		String ownership = "";
		if (getOwner() != null) {
			ownership = "for " + getOwner();
		}

		StringBuilder result = new StringBuilder();
		result.append(Integer.toString(getParamCount()));
		result.append(" parameters ");
		result.append(ownership);
		List<String> names = List.copyOf(_allParams.keySet());
		names.sort(Comparator.naturalOrder());
		for (String name : names) {
			Parameter param = _allParams.get(name);
			if (param != null) {
				result.append("\n");
				result.append(param.getName());
				result.append("=");
				result.append(param.getValue());
			} else {
				result.append("Missing Parameter for entry " + name);
			}
		}

		return result.toString();
	}

	//
	// ******************************************************************************************************************
	//

	public void resetChangeListeners() {
		_changeHandlers.clear();
	}

	public void addChangeListener(BiConsumer<String, String> listener) {
		_changeHandlers.add(listener);
	}

	protected void applyListeners(String paramName, String newValue) {
		for (BiConsumer<String, String> handler : _changeHandlers) {
			handler.accept(paramName, newValue);
		}
	}

}
