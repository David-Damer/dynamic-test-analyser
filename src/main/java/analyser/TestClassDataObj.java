package analyser;

import java.util.ArrayList;
import java.util.List;

/**
 * A class to hold test class data.
 * Including the name of the class and
 * a list of tests discovered.
 */
public class TestClassDataObj {

    /**
     * The name of the test class.
     */
    private final String className;

    /**
     * An arraylist of test method names for the class.
     */
    private final List<String> testMethods;

    /**
     * Initialises a new instance of the TestClassDO.
     * @param classname The name of the class.
     * @param testmethods The test methods in this class.
     */
    public TestClassDataObj(final String classname, final List<String> testmethods) {
        this.className = classname;
        this.testMethods = testmethods;
    }

    /**
     * Initialises a new TestClassDO
     * with default field values.
     */
    public TestClassDataObj() {
        this.className = "";
        this.testMethods = new ArrayList<>();
    }

    /**
     * Gets the class name.
     * @return The class name.
     */
    public String getTestClassName() {
        return this.className;
    }

    /**
     * Gets the list of test methods.
     * @return the list of test methods.
     */
    public List<String> getTestMethods() {
        return this.testMethods;
    }
}
