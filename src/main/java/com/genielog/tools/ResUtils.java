package com.genielog.tools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ResUtils {

	protected static Logger logger = LogManager.getLogger(ResUtils.class);

	private ResUtils() {
		
	}
	
	/**
	 * Resources are defined in a configuration file or embedded into the Jar file.
	 */
	public static String getResource(Class fromClass, String path, String resource) {
		String result = null;

		URI uri = null;
		try {
			URL url = fromClass.getResource(path);
			uri = (url != null) ? url.toURI() : null;
		} catch (URISyntaxException e) {
			logger.error("Unable to create URI from Jar {}", e.getMessage());
		}

		if (uri == null) {
			logger.error("Unable to initialize configuration without a config directory specified.");
		} else {

			//
			// Load the resource from the executable JAR
			//
			if (uri.getScheme().contains("jar")) {

				logger.debug("Looking for resource from Jar at {}", uri.getScheme());

				List<Path> match = searchPath(fromClass, null, path, ".*/" + resource);
				if (!match.isEmpty()) {
					try (InputStream is = fromClass.getResourceAsStream(match.get(0).toString())) {
						
						result = IOUtils.toString(is, StandardCharsets.UTF_8);
					} catch (IOException e) {
						logger.error("Unable to load checker definition from Jar {}", e.getLocalizedMessage());
						e.printStackTrace();
					}
				}

				if (result == null) {
					logger.error("Ressource not found in Jar '{}'", resource);
				}

			}

			//
			// Load the checkers from a FS directory (classpath as for running in the IDE)
			//
			else {
				try {
					URL url = fromClass.getResource(path);
					if (url == null) {
						logger.error("Unable to initialize configuration without a config directory specified.");
					} else {
						File dir = new File(url.toURI());
						WildcardFileFilter filter = new WildcardFileFilter("*" + resource);
						Iterator<File> allFiles = FileUtils.iterateFiles(dir, filter, DirectoryFileFilter.DIRECTORY);
						File match = allFiles.hasNext() ? allFiles.next() : null;
						if (match != null) {
							logger.debug("Resource {} found in File System at {}", resource, match.getAbsolutePath());
							result = FileUtils.readFileToString(match, "UTF8");
						}
					}
				} catch (IOException | URISyntaxException e) {
					e.printStackTrace();
				}
			}
			if (result == null) {
				logger.error("Resource not found in File System '{}'", resource);
			}

		}

		return result;
	}
	
	public static List<Path> searchPath(Class fromClass, FileSystem fs, String root, String resource) {

		if (root == null) {
			throw new IllegalArgumentException("Unable to retrieve a resource without a root to search from");
		}

		if ((resource == null) || (resource.isEmpty())) {
			throw new IllegalArgumentException("Unable to retrieve a resource without a path");
		}

		List<Path> result = new ArrayList<>();

		logger.debug("Searching for resource {} from {}", resource, root);
		FileSystem myFS = null;

		//
		// If not file system is provided to search the resource in, then try to create one by
		// looking into the Jar file of the app (where this class is defined)
		//
		if (fs == null) {
			URL jar = fromClass.getProtectionDomain().getCodeSource().getLocation();

			Path jarFile = null;
			try {
				jarFile = Paths.get(jar.toURI());

				if (jarFile != null) {
					logger.debug("Found Jar {}", jar.toURI());
					myFS = FileSystems.newFileSystem(jarFile, fromClass.getClassLoader());
				} else {
					logger.debug("Unable to find Jar at {}", jar.toURI());
				}

			} catch (URISyntaxException | IOException e1) {
				e1.printStackTrace();
			}

		} else {
			myFS = fs;
		}

		if (myFS == null) {
			logger.error("Unable to create a file system for searching resource {} from root {}", resource, root);

		} else {

			try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(myFS.getPath(root))) {

				for (Path p : directoryStream) {
					if (!Files.isDirectory(p)) {
						if (p.toString().matches(resource)) {
							URL is = fromClass.getResource(p.toString());
							if (is != null) {
								result.add(p);
							}
						}
					} else {
						List<Path> r = searchPath(fromClass, myFS, p.toString(), resource);
						result.addAll(r);
					}
				}

			} catch (IOException e) {
				logger.error(" {}", e.getLocalizedMessage());
				e.printStackTrace();
			}
			//
			// If provided FileSystem is null and the one used for searching is not, then
			// we can close the one we used because we just created it.
			//

			finally {
				try {
					if ((myFS != null) && (fs == null)) {
						myFS.close();
					}
				} catch (IOException e) {

				}
			}
		}
		return result;
	}

}
