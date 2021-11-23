package com.genielog.auditor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.genielog.tools.Chrono;
import com.genielog.tools.JsonUtils;
import com.genielog.tools.Tools;
import com.genielog.tools.parameters.AttributeWrapper;

/**
 * A class for implementing a Checker. <br>
 * For using a checker:
 * <ul>
 * <li>The engine initializes the checker calling init(config)
 * <li>Only checkers with a return 'true' value for init() and and a isValid() will be used
 * <li>Call check() and get the generated defect from the resulting stream
 * </ul>
 * 
 * @author pacherie
 *
 * @param <S>
 *          The type of the Subject the checker applies to
 * @param <D>
 *          The type of the Defect the checker generates
 */
@JsonPropertyOrder({
		"class-name", "name", "version", "description"
})
public abstract class AChecker<S, D extends ADefect> extends AttributeWrapper implements Serializable {

	private static final long serialVersionUID = -7061448365081429765L;

	protected transient Logger _logger;

	protected transient AConfig<? extends AChecker<S, D>> _config;

	@JsonProperty("class-name")
	private String _className;

	@JsonProperty("name")
	private String _name;

	@JsonProperty("description")
	private String _description;

	@JsonProperty("version")
	private String _version;

	@JsonIgnore
	public AtomicLong _nbCheckedSubjects = new AtomicLong();

	@JsonIgnore
	public Chrono _checksDuration = new Chrono();
	//
	// ******************************************************************************************************************
	//

	protected AChecker() {
		_logger = LogManager.getLogger(this.getClass());
		_className = this.getClass().getSimpleName();
		_version = "Checker 1.0";
	}

	public String toString() {
		return getName();
	}

	//
	// ******************************************************************************************************************
	//

	public String getVersion() {
		return _version;
	}

	public void setVersion(String value) {
		_version = value;
	}

	//
	// ******************************************************************************************************************
	//

	public String getName() {
		return _name;
	}

	public void setName(String value) {
		_name = value;
	}

	//
	// ******************************************************************************************************************
	//

	public String getDescription() {
		return _parameters.process(_description);
	}

	public void setDescription(String value) {
		_description = value;
	}

	// ******************************************************************************************************************
	//
	// ******************************************************************************************************************

	/** Returns true if the checker is well configured and can be used. */
	public boolean isValid() {
		return getVersion().startsWith("Checker ");
	}

	/** Each concrete checker should produce the subject it applies to from the configuration object. */
	public abstract Stream<? extends S> getSubjects();

	/** Only subjects for which this methid returns true will be actually checked. */
	public abstract boolean isValidSubject(S subject);

	/** Initialize the checker from the global configuration */
	public boolean init(AConfig config) {
		_config = config;
		return (_config != null);
	}

	/** Executed once before for all subjects are checked. */
	public abstract boolean setUp();

	/** Executed once after all sujbects are checked. */
	public abstract boolean tearDown();

	protected abstract D doCheck(S subject);

	/** Execute the verification of the subject if it is valid and generate a defect if necessary or null */
	public final Stream<D> check() {
		Stream<? extends S> subjects = getSubjects();

		if (!setUp()) {
			throw new IllegalStateException(String.format("Checker %s failed to tear up.", this.toString()));
		}

		_nbCheckedSubjects.set(0);

		return subjects
				.filter(this::isValidSubject)
				.map((S subject) -> {
					_nbCheckedSubjects.incrementAndGet();
					D defect = null;
					try {
						_checksDuration.resume();
						defect = this.doCheck(subject);
						_checksDuration.pause();
					} catch (Exception e) {
						_logger.error("Checker {} failed on {} because of {}", this, subject, Tools.getExceptionMessages(e));
						e.printStackTrace();
					}
					return defect;
				})
				.filter(Objects::nonNull)
				.onClose(() -> {
					if (!tearDown()) {
						throw new IllegalStateException(String.format("Checker %s failed to tear down.", this.toString()));
					}
				});

	}

	public Chrono getDuration() {
		return _checksDuration;
	}

	public double getAvgDuration() {
		return (double) _checksDuration.elapsed() / _nbCheckedSubjects.get();
	}

	/** Returns the number of subjects checked per seconds */
	public double getChecksPerSeconds() {
		return 1000. * _nbCheckedSubjects.get() / _checksDuration.elapsed();
	}

	// ******************************************************************************************************************
	// Persistence
	// ******************************************************************************************************************

	/** Loads the specifications of the checkers from a JSON node. */
	public boolean load(JsonNode root) {
		boolean result = (root != null);
		if (result) {
			try {
				ObjectReader reader = AConfig.getObjectMapper().readerForUpdating(this);
				reader.readValue(root);
			} catch (IOException e) {
				_logger.error("Unable to deserialize JSON : {}", Tools.getExceptionMessages(e));
				_logger.error("While parsing JSON : {}", root);
				e.printStackTrace();
			}
		}
		return result;
	}

	/** Loads the JSON specification of the checker from an InputStream. */
	public boolean load(InputStream is) {
		JsonNode node = null;
		if (is != null) {
			try {
				node = JsonUtils.getObjectMapper().readTree(is);
			} catch (IOException io) {
				_logger.error("Unable parse JSON node from input stream.");
			}

		}
		return (node != null) && load(node);
	}

	/** Loads the JSON specification of the checker from an file pathname. */
	public boolean load(String filename) {
		boolean result = (filename != null);
		if (result) {
			File file = new File(filename);
			if (file.exists()) {
				result = load(JsonUtils.getJsonNodeFromFile(filename));
			} else {
				result = false;
				_logger.error("Checker configuration file not found at: '{}'", filename);
			}
		}
		return result;
	}

	//
	// ******************************************************************************************************************
	//

	public JsonNode saveAsJson() {
		return AConfig.getObjectMapper().convertValue(this, JsonNode.class);
	}

	public boolean save(String filepath) {
		boolean result = (filepath != null);
		if (result) {
			try {
				File file = new File(filepath);
				JsonUtils.getObjectMapper().writeValue(file, this);
			} catch (IOException e) {
				_logger.error("Unable to serialize JSON : {}", Tools.getExceptionMessages(e));
				e.printStackTrace();
			}
		}
		return result;
	}

}
