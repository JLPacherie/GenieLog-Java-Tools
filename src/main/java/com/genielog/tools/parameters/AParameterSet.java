package com.genielog.tools.parameters;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.genielog.tools.functional.SerializableBiConsumer;

public abstract class AParameterSet<T> implements Serializable {

	private static final long serialVersionUID = 989649135915978317L;
	protected transient Logger _logger = null;
	protected transient List<AParameterSet<T>> _parents;
	protected transient Object owner = null;
	protected transient List<SerializableBiConsumer<AParameter<T>, T>> _changeHandlers;

	private Map<String, AParameter<T>> _allParams;

	//
	// ******************************************************************************************************************
	//

	public AParameterSet() {
		_logger = LogManager.getLogger(this.getClass());
		_changeHandlers = new ArrayList<>();
		_parents = new ArrayList<>();
		_allParams = new HashMap<>();
	}

	public AParameterSet(Map<String, T> params) {
		for (Map.Entry<String, T> item : params.entrySet()) {
			add(item.getKey(), item.getValue(), AParameter.READ_ONLY);
		}
	}

	protected abstract AParameter<T> makeParameter();

	//
	// ******************************************************************************************************************
	//

	public Map<String, T> mapExport() {
		HashMap<String, T> result = new HashMap<>();
		for (AParameter<T> param : _allParams.values()) {
			result.put(param.getName(), param.getValue());
		}
		return result;
	}

	public void mapImport(Map<String, T> map) {
		for (Map.Entry<String, T> entry : map.entrySet()) {
			add(entry.getKey(), entry.getValue(), AParameter.READ_WRITE);
		}
	}

	//
	// ******************************************************************************************************************
	//
	public Stream<AParameter<T>> parameters(boolean incParents) {
		List<AParameter<T>> result = new ArrayList<>();
		result.addAll(_allParams.values());
		if (!_parents.isEmpty() && incParents) {

			_parents.stream()//
					.flatMap(parent -> parent.parameters(true)).forEach(parentParameter -> {
						//
						// Search if the parent parameter can be overwritten
						//
						AParameter<T> finalParameter = result.stream()//
								.filter(p -> {
									if ((p.getName() == null)) {
										_logger.error("Here !");
									}
									return p.getName().equals(parentParameter.getName());
								})//
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
									T finalValue = finalParameter.getValue();
									T parentValue = parentParameter.getValue();
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
		return parameters(incParents).map(AParameter::getName).distinct().sorted();
	}

	//
	// ******************************************************************************************************************
	//

	public AParameter<T> getParameterByName(String name, boolean incParent) {
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
	public void set(String name, T value) {
		AParameter<T> p = search(name, false);
		if (p != null) {
			if (!Objects.equals(p.getValue(), value)) {
				if (p.getMode().equals(Parameter.READ_WRITE)) {
					applyListeners(p, value);
					p.setValue(value);
				} else {
					_logger.error("Attempt to write READ ONLY parameter: {}", p.getName());
					throw new IllegalArgumentException("Can't write read only parameter " + name);
				}
			}
		} else {
			_logger.error("Attempt to write undefined parameter: {}", name);
			throw new IllegalArgumentException("Can't write missing parameter " + name);
		}
	}

	//
	// ******************************************************************************************************************
	//

	/**
	 * Add or update a parameter with a new value.
	 * 
	 * @param name
	 *          The name of the parameter to add/update
	 * @param value
	 *          The value of the parameter
	 * @param mode
	 *          Is it a read/write or read only parameter.
	 * @return True if the parameter is added or updated. False if a parameter already exists and is read only
	 */
	public AParameter<T> add(String name, T value, String mode) {
		AParameter<T> p = search(name, false);
		if (p != null) {
			if (mode.equals(p.getMode()) && p.isWritable()) {
				if (((value == null) && (p.getValue() == null)) || ((value != null) && !value.equals(p.getValue()))) {
					applyListeners(p, value);
				}
				p.setValue(value);
			} else {
				_logger.debug("Can't add twice the same parameter {}, with different mode {}.", name, mode);
				throw new IllegalArgumentException(
						"Can't create twice the same parameter, check param name colision for " + name);
			}
		} else {
			p = makeParameter();
			p.setValue(value);
			p.setName(name);
			p.setMode(mode);
			p.setSet(this);
			_allParams.put(name, p);
			applyListeners(p, value);
		}
		return p;
	}

	//
	// ******************************************************************************************************************
	//

	public void add(AParameterSet<T> other) {
		if (other != null) {
			other.parameters(false).forEach(this::add);
			_parents.addAll(other._parents);
		}
	}

	//
	// ******************************************************************************************************************
	//

	public AParameter<T> add(AParameter<T> p) {
		if ((p.getSet() != null) && (p.getSet() != this)) {
			throw new IllegalArgumentException("Can't add the same parameter to 2 sets.");
		}
		p.setSet(this);
		_allParams.put(p.getName(), p);
		return p;
	}

	public void rename(String prevName, String newName) {

		AParameter<T> p = _allParams.get(prevName);

		if (p == null) {
			throw new IllegalArgumentException("The parameter to rename is not in this set?");
		}

		if (isLocked(newName)) {
			throw new IllegalArgumentException("The new name for the parameter is already defined or locked in this set.");
		}

		_allParams.remove(p.getName());
		_allParams.put(newName, p);
	}
	//
	// ******************************************************************************************************************
	//

	public int removeByPattern(String pattern) {
		List<AParameter<T>> matches = parameters(false)//
				.filter(p -> p.getName().matches(pattern))//
				.collect(Collectors.toList());

		matches.forEach(AParameter::remove);

		return matches.size();
	}

	public boolean remove(String name) {
		AParameter<T> p = search(name, false);
		return remove(p);
	}

	public boolean remove(AParameter<T> p) {
		if (p != null) {
			return _allParams.remove(p.getName()) == p;
		}
		return false;
	}

	public void clear() {
		_allParams.clear();
	}

	//
	// ******************************************************************************************************************
	//

	/** Returns the value of the parameter with the given name or null, lookup parents. */
	public T get(String name) {
		return get(name, null, true);
	}

	public T get(String name, T defaultValue) {
		return get(name, defaultValue, true);
	}

	public T get(String name, T defaultValue, boolean incParent) {
		AParameter<T> p = search(name, incParent);
		return (p != null) ? p.getValue() : defaultValue;
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

	public boolean hasParent(AParameterSet<T> other) {
		return _parents.stream().anyMatch(parent -> (parent == other) || (parent.hasParent(other)));
	}

	public Stream<AParameterSet<T>> getParents() {
		return _parents.stream();
	}

	public void addParent(AParameterSet<T> other) {
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

	public AParameter<T> search(String name, boolean incParent) {

		AParameter<T> result = _allParams.get(name);

		int iParent = 0;
		while ((result == null) && (incParent) && (iParent < _parents.size())) {
			result = _parents.get(iParent).search(name, true);
			if (result != null)
				return result;
			iParent++;
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
		names(true).forEach(name -> {
			AParameter<T> param = search(name, true);
			if (param != null) {
				result.append("\n");
				AParameterSet<T> set = param.getSet();
				if (set == null) {
					result.append("\nMissing Parameter's owner for entry '" + name + "'");
				} else if (set != this) {
					result.append("[" + set.getOwner() + "] ");
				}
				result.append(param.toString());
			} else {
				result.append("\nMissing Parameter for entry '" + name + "'");
			}
		});

		return result.toString();
	}

	//
	// ******************************************************************************************************************
	//

	public void resetChangeListeners() {
		_changeHandlers.clear();
	}

	public void addChangeListener(SerializableBiConsumer<AParameter<T>, T> listener) {
		_changeHandlers.add(listener);
	}

	protected void applyListeners(AParameter<T> param, T newValue) {
		for (SerializableBiConsumer<AParameter<T>, T> handler : _changeHandlers) {
			handler.accept(param, newValue);
		}
	}

	public boolean isValid() {
		boolean result = true;

		boolean paramsAreValid = _allParams.values().stream().allMatch(AParameter::isValid);
		if (!paramsAreValid) {
			_logger.error("ParameterSet is not valid because some of its parameter are not.");
			result = false;
		}

		List<Map.Entry<String, AParameter<T>>> toFix = new ArrayList<>();
		for (Map.Entry<String, AParameter<T>> entry : _allParams.entrySet()) {
			String id = entry.getKey();
			String name = entry.getValue().getName();
			if (!id.equals(name)) {
				toFix.add(entry);
			}
		}

		for (Map.Entry<String, AParameter<T>> entry : toFix) {
			if (_allParams.get(entry.getValue().getName()) == null) {
				_allParams.remove(entry.getKey());
				_allParams.put(entry.getValue().getName(), entry.getValue());
			} else {
				_logger.error("There's multiple parameter with the same name : {}", entry);
				result = false;
			}
		}
		return result;
	}
}
