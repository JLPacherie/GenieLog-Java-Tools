package com.genielog.tools;

import java.util.Vector;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StringUtils {

	
	protected static Logger logger = LogManager.getLogger(StringUtils.class);

	
	public static String[] getAllReferences(String srcText, String tagPREFIX, String tagSUFFIX) {
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
	
	/**
	 * Replaces the parameters names reference by their value in the given string.
	 * 
	 * @param srcStr
	 *          : The string with the parameters references
	 * @return The srcStr string bu with the parameter substitutions.
	 */
	public static String resolve(final String srcStr,String prefix, String suffix, Function<String,String> getter) {
		
		String result = srcStr;
		boolean again = true;

		while (again) {
			String[] paramNames = getAllReferences(result, prefix, suffix);
			again = false;
			int index = 0;
			while (index < paramNames.length) {
				String name = paramNames[index];
				String refName = prefix + name + suffix;
				String value = getter.apply(name);
				if (value != null) {
					if (value.indexOf(refName) == -1) {
						result = result.replace(refName, value);
						again = true;
					} else {
						again = false;
						index = paramNames.length + 1;
						logger.error("While processing '{}' auto reference detected for parameter '{}'.", srcStr, name);
					}
				}
				index++;
			}
		}

		return result;
	}

}
