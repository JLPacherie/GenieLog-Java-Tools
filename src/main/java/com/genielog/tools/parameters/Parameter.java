package com.genielog.tools.parameters;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
import java.security.InvalidParameterException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class Parameter implements Serializable {

	private static final long serialVersionUID = 6883767837453193621L;

	protected transient Logger _logger = LogManager.getLogger(this.getClass());

	public static final transient String READ_ONLY = "READ_ONLY";
	public static final transient String READ_WRITE = "READ_WRITE";

	/**
	 * A list of predicate on submitted values for the parameter. To be able to set a value to a parameter at least one of
	 * the validators must return true.
	 */
	private transient Map<String, Predicate<String>> validators = new HashMap<>();

	private transient List<String> _authorizedValues = new ArrayList<>();

	private String name;
	private String mode;
	private String value;

	//
	// ******************************************************************************************************************
	//

	public Parameter() {
		name = null;
		mode = null;
		value = null;
	}

	public Parameter(String n, String v, String m) {
		name = n;
		mode = m;
		value = v;
	}

	public Parameter(Parameter other) {
		this.name = other.name;
		this.mode = other.mode;
		this.value = other.value;
		validators.putAll(other.validators);
		_authorizedValues.addAll(other._authorizedValues);
	}

	//
	// ******************************************************************************************************************
	// Each parameter can be associated to a Owner which is the one of the holding ParameterSet. The owner object is
	// something (eg. a User for example) which may have specific READ / WRITE permission.
	// ******************************************************************************************************************
	//

	/** Returns the owner of the parent ParameterSet. */
	public Object getOwner() {
		return (getSet() != null) ? getSet().getOwner() : null;
	}

	//
	// ******************************************************************************************************************
	// Each Parameter has a READ/WRITE status which allows to create read only (constant) parameters with a value defined
	// at creation time and cannot be changed later, without explicitly changing the Mode value before.
	// ******************************************************************************************************************
	//

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	//
	// ******************************************************************************************************************
	//

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		if (isAuthorizedValue(value)) {
			this.value = value;
		} else {
			throw new InvalidParameterException("Invalid value for parameter " + name + " : '" + value + "'");
		}
	}

	public boolean isAuthorizedValue(String value) {

		if (READ_ONLY.equals(mode)) {
			_logger.error("Attempting to redefine a red only parameter {} with '{}'",this,value);
			return false;
		}
		//
		// If at least one Validator matches, then it's fine.
		//
		for (Map.Entry<String, Predicate<String>> entry : validators.entrySet()) {
			if (entry.getValue().test(value)) {
				return true;
			}
		}

		if (_authorizedValues.isEmpty())
			return true;

		//
		// If the proposed value has an exact match in the authorized value, then it's fine
		//
		if (!_authorizedValues.isEmpty() && (_authorizedValues.contains(value))) {
			return true;
		}

		//
		// Last chance, if the at least one authorized value is a regex matching proposed value...
		//
		if (value != null) {
			for (String pattern : _authorizedValues) {
				if (value.matches(pattern)) {
					return true;
				}
			}
		}

		return false;
	}
	//
	// ******************************************************************************************************************
	// Auhtorized values are String values to make sure parameter value is in a specific subset
	// ******************************************************************************************************************
	//

	public Stream<String> values() {
		return _authorizedValues.stream();
	}

	public Parameter addAutorizedValues(Set<String> values) {
		for (String v : values) {
			addAutorizedValue(v);
		}
		return this;
	}

	public Parameter addAutorizedValue(String v) {
		if (!_authorizedValues.contains(v)) {

			if (validators.isEmpty()) {
				_authorizedValues.add(v);
				return this;
			}

			for (Map.Entry<String, Predicate<String>> entry : validators.entrySet()) {
				if (entry.getValue().test(v)) {
					_authorizedValues.add(v);
					return this;
				}
			}
		} else {
			_logger.warn("Authorized value '{}' already defined in this parameter.", v);
			return this;
		}

		_logger.error("bad authorized value '{}' because it's not matched by any of the existing validator...", v);
		return this;
	}

	//
	// ******************************************************************************************************************
	// Validator are Predicate on String value to ensure the proposed value for the parameter complies with its purpose
	// ******************************************************************************************************************
	//

	/** Add a validator checking submitted value against a list of valid values. */
	public Parameter addStringValidator(Stream<String> stream) {
		stream.forEach(v -> addStringValidator(v));
		return this;
	}

	/** Add a validator checking submitted value against a string value. */
	public Parameter addStringValidator(String validValue) {
		addValidator("Value should equal '" + validValue + "'", v -> (validValue != null) && (validValue.equals(v)));
		return this;
	}

	/** Add a validator checking allowing Integer representation. */
	public Parameter addIntegerValidator() {
		addValidator("Value should represent an Integer ", v -> (v != null) && (v.matches("[0-9]+")));
		return this;
	}

	public Parameter addValidator(String name, Predicate<String> validator) {
		if (!_authorizedValues.isEmpty()) {
			throw new IllegalArgumentException(
					"Validators should be defined BEFORE valid values to make sure they match at least one of them");
		}
		validators.put(name, validator);
		return this;
	}

	// **************************************************************************
	// Accessors
	// **************************************************************************

	protected transient ParameterSet _set;

	public ParameterSet getSet() {
		return _set;
	}

	public void setSet(ParameterSet set) {
		_set = set;
	}

	public boolean remove() {
		if (getSet() != null) {
			return getSet().remove(this);
		}
		return false;
	}

	/** Returns a list of parameters defined in parents and hidden by this parameter. */
	public Stream<Parameter> hides() {
		if ((getSet() != null)) {
			return getSet().getParents() //
					.flatMap(parent -> parent.parameters(true)) //
					.filter(p -> getName().equals(p.getName()));
		}
		return Stream.empty();
	}
	
	// ************************************************************************************

	/**
	 * Returns the array of referenced parameter names found in the given string. Nested parameters such as
	 * '%(dd%(bb)%ddd)%' are not processed.
	 */
	public static String[] getReferencedParameterNames(	final String srcText,
																											final String tagPREFIX,
																											final String tagSUFFIX) {

		Vector<String> result = new Vector<>();
		if ((srcText != null) && (srcText.length() > 0)) {

			int posPrefix = 0;
			int posSuffix = 0;
			int pos = 0;

			String name = null;
			while (pos < srcText.length()) {

				// Recherche de la prochaine position on l'on trouve le préfixe
				posPrefix = srcText.indexOf(tagPREFIX, pos);

				// Si on trouve un préfixe
				if (posPrefix != -1) {

					// Recherche de la position du suffixe correspondant.
					posSuffix = srcText.indexOf(tagSUFFIX, posPrefix + tagPREFIX.length());
					if (posSuffix == -1) {
						// Found an open %( without the matching )%
						pos = srcText.length() + 1;
					} else {
						int nextPosPrefix = srcText.indexOf(tagPREFIX, posPrefix + tagPREFIX.length());
						boolean hasNestedRef = (nextPosPrefix != -1) && (nextPosPrefix < posSuffix);

						if (!hasNestedRef) {
							name = srcText.substring(posPrefix + tagPREFIX.length(), posSuffix);

							// On ajoute une seule fois chaque nom de paramètre trouvé
							if (!result.contains(name))
								result.add(name);

							pos = posSuffix + tagSUFFIX.length();

						} else {
							pos = nextPosPrefix;
						}
					}
				}

				else {
					pos = srcText.length() + 1;
				}
			}
		}
		String[] model = {};
		return result.toArray(model);
	}

	// **************************************************************************
	//
	// **************************************************************************

	/** Returns the name of this parameter. */
	public String getName() {
		return this.name;
	}

	/** Rename this parameter only if the new name is not already associated to a parameter in the same set. */
	public void setName(String newName) {
		if ((newName != null) && !newName.equals(name)) {
			if ((getSet() == null) || !getSet().isLocked(newName)) {
				this.name = newName;
			} else {
				_logger.error("Unable to set the parameter name '{}' in the set {}\n", name, _set);
				hides().forEach(p -> _logger.error("Parameter is hiden from {}\n", p.getSet()));
			}
		}
	}

	/** Returns true if the parameter can change its value (not locked). */
	public boolean isWritable() {
		return READ_WRITE.equals(getMode());
	}

	// --------------------------------------------------------------------------

	public String toString() {
		String owner = "";
		if ((getSet() != null) && (getSet().getOwner() != null)) {
			owner = getSet().getOwner().getClass().getSimpleName() + " ";
		}
		return owner + "[" + getMode() + "] '" + getName() + "' = '" + getValue() + "'";
	}

}
