package jmri.jmrit.operations;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import jmri.jmrit.XmlFile;

/**
 * Helper class for working with Files and Paths.
 * 
 * @author Gregory Madsen Copyright (C) 2012
 * 
 */

public class FileHelper {

	/**
	 * Simple helper method to just append a text string to the end of the given
	 * filename. The file will be created if it does not exist.
	 */
	public static void appendTextToFile(String fileName, String text)
			throws IOException {

		FileWriter out = new FileWriter(fileName, true);
		PrintWriter pw = new PrintWriter(out);

		pw.println(text);

		pw.close();
	}

	public static void appendTextToFile(File file, String text)
			throws IOException {
		FileWriter out = new FileWriter(file, true);
		PrintWriter pw = new PrintWriter(out);

		pw.println(text);

		pw.close();
	}


	/**
	 * Returns a File reference to the Operations main directory.  
	 * @return
	 */
	public static File getOperationsDirectory() {
		return new File(XmlFile.prefsDir(),
				OperationsXml.getOperationsDirectoryName());
	}

	/**
	 * Returns a File reference to a file inside the Operations directory.
	 */
	public static File getOperationsFile(String fileName) {

		return new File(getOperationsDirectory(), fileName);
	}

	/**
	 * Returns a File reference to a file inside of the given subdirectory,
	 * under the Operations directory.
	 * 
	 * @param subDir
	 * @param fileName
	 * @return
	 */
	public static File getOperationsSubFile(String subDir, String fileName) {
		File operations = getOperationsDirectory();

		File sub = new File(operations, subDir);

		return new File(sub, fileName);
	}

}
