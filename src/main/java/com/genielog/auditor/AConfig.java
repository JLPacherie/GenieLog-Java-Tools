package com.genielog.auditor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Filter.Result;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.ThresholdFilter;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genielog.tools.JsonUtils;
import com.genielog.tools.RelaxedOptionsParser;
import com.genielog.tools.ResUtils;
import com.genielog.tools.Tools;
import com.genielog.tools.parameters.AttributeWrapper;

public abstract class AConfig<C extends AChecker> extends AttributeWrapper {

	protected Logger _logger;

	protected String _configDir = null;
	protected String _name = "";
	protected String _description = "";
	protected transient List<String> _checkerPackages = new ArrayList<>();
	private static transient ObjectMapper sMapper = null;

	// The list of enabled checkers by the current configuration.
	protected List<C> _enabledCheckers = new ArrayList<>();

	// The list of all available checkers (and not yet enabled).
	protected List<C> _availableCheckers = new ArrayList<>();

	protected Options _options = null;

	protected AConfig() {
		_logger = LogManager.getLogger(this.getClass());

		add("name", () -> _name, v -> _name = (String) v);
		add("description", () -> _description, v -> _description = (String) v);
		add("config-dir", () -> _configDir, v -> _configDir = (String) v);
	}

	public void addCheckerPackage(String name) {

		if (!_checkerPackages.contains(name)) {
			_checkerPackages.add(name);
		}

	}

	//
	// ******************************************************************************************************************
	//

	public String getConfigDir() {
		return (String) get("config-dir");
	}

	public void setConfigDir(String path) {
		set("config-dir", path);
	}

	//
	// ******************************************************************************************************************
	//

	public String getName() {
		return (String) get("name");
	}

	public void setName(String name) {
		set("name", name);
	}

	//
	// ******************************************************************************************************************
	//

	public String getDescription() {
		return (String) get("description");
	}

	public void setDescription(String description) {
		set("decription", description);
	}

	protected Options getOptions() {
		if (_options == null) {

			// TODO Move the definition of options string in static constant string.
			// to make sure this is compliant in defining CLI, reading CLI and parsing JSON.

			_options = new Options();

			_options.addOption("h", "help", false, "display help message");
			_options.addOption("v", "verbose", false, "Run verbosely");
			_options.addOption("g", "debug", false, "Run in debug mode");
			_options.addOption(null, "disable-default", false, "Disable all default checkers");

			_options.addOption(
					Option.builder("cf")
							.required(false)
							.longOpt("config-file")
							.numberOfArgs(1)
							.desc("Specify the JSON configuration file")
							.build());

			_options.addOption(
					Option.builder()
							.required(false)
							.longOpt("list-checkers")
							.desc("List all available checkers")
							.build());

			_options.addOption(
					Option.builder("cd")
							.required(false)
							.longOpt("config-dir")
							.numberOfArgs(1)
							.desc("Specify the main configuration directory")
							.build());

			_options.addOption(
					Option.builder("D")
							.longOpt("overwrite")
							.numberOfArgs(2)
							.valueSeparator('=')
							.desc("Overwrite value for given JSON property")
							.build());

			_options.addOption(
					Option.builder()
							.required(false)
							.longOpt("all")
							.desc("Enable all known checkers")
							.build());

			_options.addOption(
					Option.builder("co")
							.longOpt("checker-option")
							.valueSeparator(':')
							.numberOfArgs(3)
							.desc("Overwrite checker option value CHECKER:OPTION:VALUE")
							.build());

			_options.addOption(
					Option.builder("en")
							.longOpt("enable")
							.numberOfArgs(1)
							.desc("Enable checker metric")
							.build());

		}
		return _options;
	}

	protected C makeChecker(InputStream checkerFile) {
		C result = null;
		JsonNode jsonChecker = null;
		try {
			jsonChecker = AConfig.getObjectMapper().readTree(checkerFile);
			if ((jsonChecker != null) && (jsonChecker.get(AChecker.VERSION).asText("").startsWith("Checker "))) {
				result = makeChecker(jsonChecker);
			} else {
				_logger.error("Submitted JSON is not one of a Checker ? ");
				throw new IllegalArgumentException("\"Submitted JSON is not one of a Checker ? \"");
			}
		} catch (IOException e) {
			_logger.error("Unable to parse JSON : {}", Tools.getExceptionMessages(e));
		}
		return result;
	}

	// ******************************************************************************************************************
	// Checker Serialization
	// ******************************************************************************************************************

	public static ObjectMapper getObjectMapper() {
		if (sMapper == null) {
			sMapper = new ObjectMapper();
			sMapper.setVisibility(PropertyAccessor.ALL, Visibility.PROTECTED_AND_PUBLIC);
			sMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
			sMapper.setVisibility(PropertyAccessor.IS_GETTER, Visibility.NONE);
			sMapper.setVisibility(PropertyAccessor.GETTER, Visibility.NONE);
			sMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
		}
		return sMapper;
	}

	protected C makeChecker(JsonNode jsonChecker) {
		C result = null;

		if (jsonChecker == null) {
			throw new IllegalArgumentException("Undefined Checker's JSON to load.");
		}

		String className = JsonUtils.getFieldAsText(jsonChecker, "class-name", "", null);
		if (className.isEmpty()) {
			throw new IllegalArgumentException("Missing class name '.class-name' in JSON");
		}

		Class<?> checkerClass = null;

		int iPackage = 0;
		while ((iPackage < _checkerPackages.size()) && (checkerClass == null)) {
			String fullClassname = _checkerPackages.get(iPackage) + "." + className;
			try {
				checkerClass = Class.forName(fullClassname);
			} catch (ClassNotFoundException e) {
				_logger.debug("Checker class not found at '{}' : {}", fullClassname, Tools.getExceptionMessages(e));

			}

			iPackage++;
		}

		try {

			if (checkerClass == null) {
				_logger.error("Unknown Checker's class name '{}'", className);
			} else {
				_logger.info("Checker class for '{}' found at '{}'", className, checkerClass.getCanonicalName());
				Constructor<?> checkerConstructor = checkerClass.getConstructor();
				Object checkerInstance = checkerConstructor.newInstance();
				if (checkerInstance instanceof AChecker<?, ?>) {
					result = (C) checkerInstance;
				}
			}
		} catch (Exception e) {
			_logger.error("Unable to create checker object from class {}", className);
			_logger.error("Reason: {}", Tools.getExceptionMessages(e));
			e.printStackTrace();
		}

		if (result != null) {
			result.load(jsonChecker);
		}

		return result;
	}

	//
	// ******************************************************************************************************************
	//

	public String getHelpBanner() {
		HelpFormatter formatter = new HelpFormatter();
		StringWriter stringWriter = new StringWriter();
		PrintWriter printWriter = new PrintWriter(stringWriter);
		formatter.printHelp(printWriter, 80, getName() + " [options] where ", "", getOptions(), 2, 1, "");
		return getStandardBanner() + "\n" + stringWriter.toString();
	}

	public abstract String getStandardBanner();

	/** Return the value of a parameter as read from the CLI, the env var or default value. */
	public Object getParameter(CommandLine cmd, String option, String env, Object fallback) {
		Object result = cmd.getOptionValue(option, null);
		if (result == null) {
			result = System.getenv(env);
		}
		if (result == null) {
			result = fallback;
		}
		return result;
	}

	public String getStrParameter(CommandLine cmd, String option, String env, String fallback) {
		return (String) getParameter(cmd, option, env, fallback);
	}

	/** Load configuration from the options on the command line. */
	public boolean cliLoad(String[] args) {
		boolean result = (args != null) && (args.length > 0);
		if (result) {
			// First, check for json configuration.
			CommandLineParser parser = new RelaxedOptionsParser();
			try {
				// parse the command line arguments
				CommandLine line = parser.parse(getOptions(), args);

				if (line.hasOption("help")) {
					System.err.println(getHelpBanner());
					return true;
				}
				if (line.hasOption("debug")) {

					LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
					Configuration config = ctx.getConfiguration();
					LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
					ConsoleAppender consoleAppender = (ConsoleAppender) loggerConfig.getAppenders().get("Console");
					ThresholdFilter currentFilter = (ThresholdFilter) consoleAppender.getFilter();
					consoleAppender.removeFilter(currentFilter);
					ThresholdFilter newFilter = ThresholdFilter.createFilter(Level.DEBUG, Result.ACCEPT, Result.DENY);
					consoleAppender.addFilter(newFilter);

					ctx.updateLoggers();
					Configurator.setAllLevels(LogManager.getRootLogger().getName(), Level.DEBUG);

					_logger.debug("Activating debug logs");
				}

				// ----------------------------------------------------------------
				// Set all options from the proposed configuration file
				// ----------------------------------------------------------------
				{
					if (line.hasOption("config-file")) {
						String fileName = line.getOptionValue("config-file");
						if (new File(fileName).isFile()) {
							result = jsonLoad(fileName);
							if (!result) {
								_logger.error("Incomplete on bad configuration from '{}'", fileName);
							}
						} else {
							_logger.error("Bad default configuration file (not a file): '{}'", fileName);
							result = false;
						}
					}
				}

				// ----------------------------------------------------------------
				// Set all options from the proposed configuration file
				// ----------------------------------------------------------------
				if (line.hasOption("config-dir")) {
					String dirName = line.getOptionValue("config-dir");
					if (new File(dirName).isDirectory()) {
						setConfigDir(dirName);
					} else {
						_logger.error("Bad  configuration directory specified (not a directory): '{}'", dirName);
						result = false;
					}
				}

				// ----------------------------------------------------------------
				// Initialize configuration from the configuration file and folder
				// ----------------------------------------------------------------
				if (!result) {
					_logger.error("Initialization configuration from JSON file failed.");
				} else {
					result = init();
				}

				// ****************************************************************
				// From here forward, the purpose is to overwrite config params
				// just read from the configuration file during the init()
				// ****************************************************************

				// ----------------------------------------------------------------
				// Overwriting some JSON parameters of the Congiguration file
				// --overwrite attrName=attrValue will override any attribute defined in
				// Config with the provided value.
				// ----------------------------------------------------------------
				{
					Properties props = line.getOptionProperties("overwrite");
					for (String pname : props.stringPropertyNames()) {
						if (hasAttribute(pname)) {
							set(pname, props.getProperty(pname));
						}
					}
				}

				attrNames().forEach(attrName -> {
					if (line.hasOption(attrName)) {
						String value = line.getOptionValue(attrName);
						set(attrName, value);
						_logger.debug("Reading attribute {} from the command line.", attrName);
					}
				});

				// ----------------------------------------------------------------
				// Enabling all checkers.
				// ----------------------------------------------------------------
				{
					if (line.hasOption("all")) {
						if (_availableCheckers.isEmpty()) {
							_logger.error("There's no available checkers. Check that the configuration directory.");
							result = false;
						} else {
							if (!enableAllCheckers()) {
								result = false;
								_logger.error("Enabling all checkers failed.");
							}
						}
					}
				}

				// Enabling Checkers - this enable the checker
				{
					String[] checkerNameList = line.getOptionValues("enable");
					if (checkerNameList != null) {
						for (String checkerName : checkerNameList) {
							C checker = enableChecker(checkerName);
							if (checker == null) {
								_logger.error("Unable to enable checker: {}", checkerName);
								result = false;
							} else {
								_logger.debug("Manually activating checker: {}", checker);
							}
						}
					}
				}

				// Overwriting Checkers options - this enable the checker
				{
					String[] values = line.getOptionValues("checker-option");
					if (values != null) {
						int optionIndex = 0;
						while (optionIndex < values.length) {
							String checkerName = values[optionIndex];
							String fieldName = values[optionIndex + 1];
							String fieldValue = values[optionIndex + 2];
							try {
								C checker = getEnabledChecker(checkerName);
								if (checker == null) {
									_logger.debug("Enabling checker for configuring it.");
									checker = enableChecker(checkerName);
								}
								if (checker != null) {
									if (checker.hasAttribute(fieldName)) {
										checker.set(fieldName, fieldValue);
										_logger.debug("Attribute {} in checker {} changed to {}.",
												fieldName, checkerName, fieldValue);
									} else {
										result = false;
										_logger.warn("Unknown attribute {} for checker {}", fieldName, checkerName);
									}
								} else {
									_logger.error("Unable to find or enable the checker {}", checkerName);
									result = false;
								}
							} catch (NumberFormatException e) {
								_logger.error("Unable to parse threshold for checker {} and metric {}", checkerName,
										fieldName);
								result = false;
							}
							optionIndex += 3;
						}
					}
				}

				{
					if (!line.hasOption("disable-default") && !line.hasOption("enable")) {
						_logger.debug("No specific checker enabled and no disable all option, enabling all checkers.");
						if (!enableAllCheckers()) {
							result = false;
						}
					}
				}

				// ----------------------------------------------------------------
				// Listing all the available checkers
				// ----------------------------------------------------------------
				if (line.hasOption("list-checkers")) {

					for (String logLine : getStandardBanner().split("\n", -1)) {
						_logger.info(logLine);
					}
					_logger.info("List of available checkers:");

					for (C checker : _availableCheckers) {
						_logger.info("  - Checker (disabled): {}", checker);
					}
					for (C checker : _enabledCheckers) {
						_logger.info("  - Checker  (enabled): {}", checker);
					}

					System.exit(0);
				}

			} catch (ParseException exp) {
				// oops, something went wrong
				_logger.error("Parsing failed.  Reason: {}", exp.getMessage());
				result = false;
			}
		} else {
			_logger.error("No command line provided.");
		}

		if (!result) {
			System.err.println(getStandardBanner());
			System.err.println("Use --help for usage information.\n");
		}
		return result;
	}

	//
	// ******************************************************************************************************************
	//

	/** Load configuration from a JSON text. */
	public boolean jsonLoad(String jsonFileName) {
		boolean result = (jsonFileName != null);
		if (result) {
			File file = new File(jsonFileName);
			result = file.isFile();
			if (result) {
				JsonNode root = JsonUtils.getJsonNodeFromFile(jsonFileName);
				if (root != null) {
					_logger.debug("Start loading configuration from {}", jsonFileName);

					attrNames().forEach(attrName -> {
						JsonUtils.getFieldAsText(root, attrName, "", v -> set(attrName, v));
					});

				} else {
					// The given filename doesn't point to a file
					_logger.error("Missing file at {}", jsonFileName);
					result = false;
				}
			} else {
				// No filename is given
				_logger.error("No file provided");
				result = false;
			}
		}
		return result;
	}

	/**
	 * After being loaded, a configuration should be initialized.
	 */
	public boolean init() {
		boolean result = true;

		_logger.debug("Initializing configuration {}", getName());

		_logger.debug("Clearing list of {} enabled checkers.", _enabledCheckers.size());
		_enabledCheckers.clear();

		_logger.debug("Clearing list of {} available checkers.", _availableCheckers.size());
		_availableCheckers.clear();

		//
		// A Configuration can be initialized from a FileSystem directory where all available
		// checker definitions might be found in files METRICS*.json
		//
		if (getConfigDir() != null) {
			File cfgDir = new File(getConfigDir());
			if (cfgDir.isDirectory()) {
				_logger.debug("Loading  list of known checkers from {}.", getConfigDir());
				WildcardFileFilter filter = new WildcardFileFilter(".*\\.json");
				Iterator<File> allCheckerFiles = FileUtils.iterateFiles(cfgDir, filter, DirectoryFileFilter.DIRECTORY);
				while (allCheckerFiles.hasNext()) {
					File checkerFile = allCheckerFiles.next();
					C checker = null;
					try (FileInputStream checkerIS = new FileInputStream(checkerFile)) {
						checker = makeChecker(checkerIS);
					} catch (IOException e) {
						_logger.error("Unable to open stream for file {}", checkerFile.getAbsolutePath());
					}
					if ((checker == null) || !checker.isValid()) {
						_logger.error("Unable to load checker from file {}.", checkerFile.getAbsolutePath());
						result = false;
					} else {
						_logger.debug("Adding new checker fromm configuration folder : {}", checker.getName());
						_availableCheckers.add(checker);
					}
				}
			} else {
				_logger.warn("No configuration directory found at {}", getConfigDir());
			}
		} /* End of loading checker definitions from a confgi dir specified in the config */

		//
		// A configuration can also be initialized from the resources embedded in the application
		// Jar file. This is a bit more tricky because the way resources can be accessed at
		// runtime depends if we are debugging from the IDE or running from a packaged Jar file.
		//

		{
			// This is the root folder of the src/main/resourcess where checkers definition files are
			String folderPath = "/checkers";
			URI uri = null;
			try {
				URL url = this.getClass().getResource(folderPath);
				uri = (url != null) ? url.toURI() : null;
			} catch (URISyntaxException e) {
				_logger.error("Unable to create URI from Jar {}", e.getMessage());
			}

			if (uri == null) {
				_logger.error("Unable to initialize configuration without a config directory specified.");
				result = false;
			} else {

				//
				// Load the checkers from the executable JAR
				//
				if (uri.getScheme().contains("jar")) {
					List<Path> allJsonFiles = ResUtils.searchPath(this.getClass(), null, "/checkers", ".*\\.json");

					HashMap<String, InputStream> allStreams = new HashMap<>();

					for (Path p : allJsonFiles) {
						InputStream is = AConfig.class.getResourceAsStream(p.toString());
						if (is != null) {
							allStreams.put(p.getFileName().toString(), is);
						} else {
							_logger.debug("Unable to create input stream for path {}", p);
						}
					}

					for (Map.Entry<String, InputStream> entry : allStreams.entrySet()) {
						if (entry.getKey().endsWith(".json")) {
							C checker = null;
							try (InputStream checkerIS = entry.getValue()) {
								checker = makeChecker(checkerIS);
							} catch (IOException e) {
								_logger.error("Unable to open stream for resource file {}", entry.getKey());
							}

							if ((checker == null) || !checker.isValid()) {
								_logger.error("Unable to load checker from resource file {}.", entry.getKey());
								result = false;
							} else {
								_logger.debug("Adding new checker : {}", checker.getName());
								_availableCheckers.add(checker);
							}
						}
					}

				}

				//
				// Load the checkers from a FS directory (classpath as for running in the IDE)
				//
				else {
					_logger.debug("Loading  list of known checkers from internal definitions");
					File dir = new File(uri);
					WildcardFileFilter filter = new WildcardFileFilter("*.json");
					Iterator<File> allFiles = FileUtils.iterateFiles(dir, filter, DirectoryFileFilter.DIRECTORY);

					allFiles.forEachRemaining(nextFile -> {
						C checker = null;
						try (FileInputStream checkerIS = new FileInputStream(nextFile)) {
							checker = makeChecker(checkerIS);
						} catch (IOException e) {

						}
						if ((checker == null) || !checker.isValid()) {
							_logger.error("Unable to load checker from file {}.", nextFile.getAbsolutePath());
						} else {
							_logger.debug("Adding new checker : {}", checker.getName());
							_availableCheckers.add(checker);
						}

					});
				}
			}

		}
		return result;

	}

	//
	// ******************************************************************************************************************
	//

	public boolean isValid() {

		boolean result = true;

		_logger.debug("Checking current configuration ...");

		// ----------------------------------------
		// Validate the Enabled Checker list.
		// ----------------------------------------
		if (_enabledCheckers.size() + _availableCheckers.size() == 0) {
			result = false;
			_logger.error("This configuration is not valid because there's no checker defined or enabled.");
		}

		for (C checker : _enabledCheckers) {
			if (!checker.isValid()) {
				result = false;
				_logger.error("Invalid enabled checker detected with {}", checker);
			}
		}

		// ----------------------------------------
		// Validate the Available Checker list.
		// ----------------------------------------
		for (C checker : _availableCheckers) {
			if (!checker.isValid()) {
				result = false;
				_logger.error("Invalid available checker definition detected with {}", checker);
			}
		}

		return result;

	}
	//
	// ******************************************************************************************************************
	//

	/** Enable all available checkers. */
	public boolean enableAllCheckers() {
		boolean result = true;

		while (result && !_availableCheckers.isEmpty()) {
			// Enabling a checker removes it from the available list.
			result = enableChecker(_availableCheckers.get(0).getName()) != null;
		}

		return result;
	}

	public boolean isValidCheckerName(String name) {
		return (name != null) && !name.isEmpty();
	}

	//
	// ******************************************************************************************************************
	//

	/**
	 * Move the specified checker to the list of enabled checkers. A checker with the given name should be listed in the
	 * available checkers and will then be moved to the list of the enabled checkers.
	 *
	 * @param name
	 *          The name of the available checker to search for
	 * @return The now enabled checker the name or null if either already enabled or not available.
	 */
	public C enableChecker(String name) {

		if (!isValidCheckerName(name)) {
			throw new IllegalArgumentException("Invalid checker name to enable : '" + name + "'");
		}

		C checker = getAvailableChecker(name);
		if ((checker != null) && (checker.isValid())) {
			_enabledCheckers.add(checker);
			_availableCheckers.remove(checker);
			_logger.debug("Adding checker {}", checker);
		} else {
			_logger.error("Invalid checker submitted to configuration {}", checker);
			throw new IllegalArgumentException("Checker to enabled is unknown : " + name);
		}

		return checker;
	}

	/** Returns true if the check is already enabled. */
	public boolean isEnabled(String checkerName) {
		if (!isValidCheckerName(checkerName)) {
			throw new IllegalArgumentException("Checker's name is not valid : " + checkerName);
		}
		return _enabledCheckers.stream().filter(c -> checkerName.equals(c.getName())).findFirst().orElse(null) != null;
	}

	//
	// ******************************************************************************************************************
	//

	/**
	 * Returns an available checker identified by the given name if there's one.
	 *
	 * @param name
	 *          The name of the available checker to search for
	 * @return The available checker matching the name or null.
	 */
	public C getAvailableChecker(String name) {
		if (!isValidCheckerName(name)) {
			throw new IllegalArgumentException("Checker's name must be non null and not empty");
		}
		return _availableCheckers.stream().filter(c -> name.equals(c.getName())).findFirst().orElse(null);
	}

	public Stream<C> availableCheckers() {
		return _availableCheckers.stream();
	}

	public Stream<C> enabledCheckers() {
		return _enabledCheckers.stream();
	}

	//
	// ******************************************************************************************************************
	//

	/**
	 * Returns an enabled checker identified by the given name, if there's one.
	 *
	 * @param name
	 *          The name of the enabled checker to search for
	 * @return The enabled checker matching the name or null.
	 */
	public C getEnabledChecker(String name) {
		if (!isValidCheckerName(name)) {
			throw new IllegalArgumentException("Checker's name must be non null and not empty");
		}
		return _enabledCheckers.stream().filter(c -> name.equals(c.getName())).findFirst().orElse(null);
	}

}
