package com.genielog.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ZipUtils {

	protected static Logger logger = LogManager.getLogger(ZipUtils.class);

	private ZipUtils() {

	}

	public static InputStream openFile(String fileName) {
		InputStream result = null;
		File file = new File(fileName);
		if (file.exists() && file.canRead()) {
			try {
				result = new FileInputStream(file);
				if (fileName.endsWith(".gz")) {
					GZIPInputStream gis = new GZIPInputStream(result);
					result = gis;
				}
			} catch (FileNotFoundException fne) {
				logger.error("Unable to find input file at '{}'", fileName);
				fne.printStackTrace();
			} catch (IOException e) {
				logger.error("Unable to open input file at '{}': {}", fileName, Tools.getExceptionMessages(e));
				e.printStackTrace();
			} finally {
				
			}
		}
		return result;
	}

	public static boolean unzip(String fileZip, File destDir) throws IOException {
		byte[] buffer = new byte[1024];
		boolean result = (fileZip != null) && (destDir.canWrite());
		if (result) {
			FileOutputStream fos = null;
			try (ZipInputStream zis = new ZipInputStream(new FileInputStream(fileZip))) {
				ZipEntry zipEntry = zis.getNextEntry();
				while (zipEntry != null) {

					// Get a file descriptor for the file ziped in zipEntry
					File newFile = newFile(destDir, zipEntry);

					// Write the destination file, which remains in the target dir
					fos = new FileOutputStream(newFile);
					int len;
					while ((len = zis.read(buffer)) > 0) {
						fos.write(buffer, 0, len);
					}
					fos.close();

					zipEntry = zis.getNextEntry();
				}
				zis.closeEntry();
			} catch (IOException e) {
				result = false;
				if (fos != null)
					fos.close();
			}
		}
		return result;
	}

	//
	// ******************************************************************************************************************
	//

	/** Zip one single file. */
	public static void zipOneFile(String sourceFile, String zipFile) throws IOException {
		File fileToZip = new File(sourceFile);
		if (fileToZip.exists()) {
			try (
					FileOutputStream fos = new FileOutputStream(zipFile);
					ZipOutputStream zipOut = new ZipOutputStream(fos);
					FileInputStream fis = new FileInputStream(fileToZip);) //
			{
				ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
				zipOut.putNextEntry(zipEntry);
				byte[] bytes = new byte[1024];
				int length;
				while ((length = fis.read(bytes)) >= 0) {
					zipOut.write(bytes, 0, length);
				}
			} catch (IOException e) {
				logger.error("Exception while zipping {}: {}", sourceFile, e.getLocalizedMessage());
			}
		} else {
			logger.error("Missing file to zip: '{}'", sourceFile);
		}
	}

	//
	// ******************************************************************************************************************
	//

	/** Creates a File instance for the given ZipEntry, making sure the target file remains inside the target dir. */
	public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
		File destFile = new File(destinationDir, zipEntry.getName());

		String destDirPath = destinationDir.getCanonicalPath();
		String destFilePath = destFile.getCanonicalPath();

		if (!destFilePath.startsWith(destDirPath + File.separator)) {
			throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
		}

		return destFile;
	}
}
