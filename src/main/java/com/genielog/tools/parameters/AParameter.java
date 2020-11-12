package com.genielog.tools.parameters;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.genielog.tools.functional.SerializablePredicate;

public abstract class AParameter<T> implements Serializable {

	private static final long serialVersionUID = 6883767837453193621L;

	protected transient Logger _logger = LogManager.getLogger(this.getClass());

	public static final transient String READ_ONLY = "READ_ONLY";
	public static final transient String READ_WRITE = "READ_WRITE";

	/**
	 * A list of predicate on submitted values for the parameter. To be able to set a value to a parameter at least one of
	 * the validators must return true.
	 */
	private transient Map<String, SerializablePredicate<T>> validators = new HashMap<>();

	private transient List<T> _authorizedValues;

	private String name;
	private String mode;

	//
	// ******************************************************************************************************************
	//

	public AParameter() {
		this(null, null, READ_WRITE);
	}

	public AParameter(String n, T v, String m) {
		name = n;
		setValue(v);
		mode = m;
	}

	public AParameter(AParameter<T> other) {
		this(other.getName(), other.getValue(), other.getMode());
		validators.putAll(other.validators);
		if (other._authorizedValues != null) {
			_authorizedValues = new ArrayList<>();
			_authorizedValues.addAll(other._authorizedValues);
		}
	}

	public boolean isValid() {
		boolean result = true;
		if (getName() == null) {
			_logger.warn("Parameter {} has no name", toString());
			result = false;
		}
		// The test hasValidations is used to avoid calculation of getValue which for
		// BridgedParameter can cause init issues
		if (hasValidations() && !isAuthorizedValue(getValue())) {
			_logger.warn("Parameter {} has invalid value", toString());
			result = false;
		}
		return result;
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

	public abstract T getValue();

	public final void setValue(T value) {
		if (READ_ONLY.equals(mode)) {
			throw new IllegalStateException("Can't change the value of a read parameter : " + getName());
		}
		if (isAuthorizedValue(value)) {
			doSetValue(value);
		} else {
			throw new IllegalArgumentException("Unauthorized value '" + value + "' submitted to parameter " + getName());
		}
	}

	protected abstract void doSetValue(T value);

	public boolean hasValidations() {
		return READ_WRITE.equals(mode) ||
				((_authorizedValues != null) && !_authorizedValues.isEmpty()) ||
				((validators != null) && !validators.isEmpty());
	}

	public boolean isAuthorizedValue(T value) {

		if (READ_ONLY.equals(mode) && !Objects.equals(getValue(), value)) {
			_logger.error("Attempting to redefine a read only parameter {} with '{}'", this, value);
			return false;
		}

		//
		// If there's no validations attached to this parameter, then it's fine
		//
		if (((_authorizedValues == null) || _authorizedValues.isEmpty()) && validators.isEmpty())
			return true;

		//
		// If at least one Validator matches, then it's fine.
		//
		boolean validated = validators.isEmpty() || (validators.values()
				.stream()
				.filter(p -> p.test(value))
				.findFirst().orElse(null) != null);

		//
		// If the proposed value has an exact match in the authorized value, then it's fine
		//
		boolean authorized = (_authorizedValues == null) || (_authorizedValues.isEmpty()) ||
				_authorizedValues.stream().anyMatch(v -> v.equals(value));

		return validated && authorized;
	}
	//
	// ******************************************************************************************************************
	// Authorized values are String values to make sure parameter value is in a specific subset.
	//
	// If there's already a value, the first authorized value must be the current value of the parameter.
	// If there's already validators, each submitted authorized value should match at least on of the validator.
	// ******************************************************************************************************************
	//

	public Stream<T> values() {
		return (_authorizedValues == null) ? Stream.empty() : _authorizedValues.stream();
	}

	public AParameter<T> addAutorizedValues(Set<T> values) {
		for (T v : values) {
			addAuthorizedValue(v);
		}
		return this;
	}

	/** Add the given string to the authorized values for the parameter. */
	public AParameter<T> addAuthorizedValue(T v) {

		//
		// If there's already validators defined for the parameter, at least one of them
		// should validate the submitted authorized value.
		//
		if (!validators.isEmpty()) {

			String validatorID = null;
			for (Map.Entry<String, SerializablePredicate<T>> entry : validators.entrySet()) {
				if (entry.getValue().test(v)) {
					validatorID = entry.getKey();
					continue;
				}
			}

			if (validatorID == null) {
				throw new IllegalArgumentException(
						"None of the current validators accepts the submitted authorized value '" + v + "'");
			}

		}

		//
		// Making sure that authorized values are adding just once.
		//
		if (_authorizedValues == null) {
			_authorizedValues = new ArrayList<>();
		}
		if (!_authorizedValues.contains(v)) {
			_authorizedValues.add(v);
		} else {
			_logger.warn("Submitting twice the same authrized value '{}'", v);
		}

		//
		// Making sure that the current value of the parameter is authorized?
		//
		if (!isAuthorizedValue(getValue())) {
			_authorizedValues.remove(v);
			throw new IllegalArgumentException(
					"The parameter defines authorized values that doesn't match current parameter's value.");
		}

		return this;
	}

	//
	// ******************************************************************************************************************
	// Validators are Predicates on String value to ensure the proposed value for the parameter complies with its purpose
	//
	// When validator are defined, they express a OR operation on the submitted value for the setter.
	// ******************************************************************************************************************
	//

	public AParameter<T> addValidator(String name, SerializablePredicate<T> validator) {

		if (_authorizedValues != null && !_authorizedValues.stream().allMatch(validator)) {
			throw new IllegalArgumentException(
					"The validator named '" + name + "' doesn't match the already defined auhtorized values.");
		}

		validators.put(name, validator);

		if (!isAuthorizedValue(getValue())) {
			validators.remove(name);
			throw new IllegalArgumentException(
					"The submitted validator named '" + name + "' doesn't match the current value.");
		}

		return this;
	}

	// **************************************************************************
	// Accessors
	// **************************************************************************

	protected transient AParameterSet<T> _set;

	public AParameterSet<T> getSet() {
		return _set;
	}

	public void setSet(AParameterSet<T> set) {
		_set = set;
	}

	public boolean remove() {
		if (getSet() != null) {
			return getSet().remove(this);
		}
		return false;
	}

	/** Returns a list of parameters defined in parents and hidden by this parameter. */
	public Stream<AParameter<T>> hides() {
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
		if ((newName != null) && !newName.equals(name) && isWritable()) {
			if (getSet() != null) {
				getSet().rename(getName(), newName);
			}
			this.name = newName;
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
			owner = "(" + getSet().getOwner().getClass().getSimpleName() + ") ";
		}
		return owner + "[" + getMode() + "] '" + getName() + "' = '" + getValue() + "'";
	}

}
