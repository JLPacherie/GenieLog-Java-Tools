package com.genielog.tools;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// https://stackoverflow.com/questions/33874902/apache-commons-cli-1-3-1-how-to-ignore-unknown-arguments
public class RelaxedOptionsParser extends DefaultParser {
	
  protected Logger _logger = LogManager.getLogger(this.getClass());
      
	 @Override
   public CommandLine parse(Options options, String[] arguments) throws ParseException {
		 final List<String> knownArgs = new ArrayList<>();
	    for (int i = 0; i < arguments.length; i++) {
	        if (options.hasOption(arguments[i])) {
	            knownArgs.add(arguments[i]);
	            if (i + 1 < arguments.length && options.getOption(arguments[i]).hasArg()) {
	                knownArgs.add(arguments[i + 1]);
	            }
	        } else {
	          _logger.warn("Unknown option {}",arguments[i]);
	        }
	    }
	    return super.parse(options, knownArgs.toArray(new String[0]));
   }
}
