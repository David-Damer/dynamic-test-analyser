package analyser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DirectoryScanner {

    /**
     * Java extension.
     */
    private static final String JAVA = ".java";
    /**
     * Java extension length.
     */
    private static final int JAVA_LENGTH = JAVA.length();

    /**
     * Constructor.
     */
    public DirectoryScanner() { }

    /**
     * Scan the given directory recursively for all java classes.
     * @param directory the directory to scan.
     * @return the results of the scan.
     */
    public ArrayList<String> scanDirectory(final File directory) {
        ArrayList<String> results = new ArrayList<>();
        scanDirectory(directory, results, "");
        return results;
    }

    /**
     * Recursively identify and add test classes to the result array.
     * Assumes that only test classes will be in the directory.
     *
     * @param directory     The root directory to begin finding test classes.
     * @param result        The list of test classes.
     * @param packageString The package name to build fully qualified test class names.
     */
    private static void scanDirectory(final File directory, final List<String> result, final String packageString) {
        for (final File f : directory.listFiles()) {
            String filename = f.getName();
            if (f.isFile() && filename.endsWith(JAVA) && !filename.contains("$")) {
                result.add(packageString + filename.substring(0, filename.length() - JAVA_LENGTH));
            }
            if (f.isDirectory()) {
                scanDirectory(f, result, packageString + filename + ".");

            }
        }
    }
}
