package analyser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Holds collected test measurements.
 */
public final class TestMeasurement {

    /**
     * The test class.
     */
    private final String className;
    /**
     * The name of the test method.
     */
    private final String testName;
    /**
     * The maximum stack reached during execution of the test method.
     */
    private long maximumStackDepth;
    /**
     * The methods called in the test execution and their quantities.
     */
    private HashMap<String, Long> methodCalls;
    /**
     * The distinct classes initialised in the test execution.
     */
    private HashMap<String, Long> classInitialisations;

    /**
     * The stack trace of the test.
     */
    private ArrayList<String> trace;

    /**
     * Gets the depth trace.
     * @return the depth trace.
     */
    public List<Integer> getDepthTrace() {
        return depthTrace;
    }

    /**
     * Trace of stack depth over time.
     */
    private List<Integer> depthTrace;

    /**
     * Gets trace.
     * @return trace.
     */
    public List<String> getTrace() {
        return this.trace;
    }

    /**
     * Initialises an instance of TestMeasurement Class.
     *
     * @param testname The name of the test being measured.
     * @param classname   The test class.
     */
    TestMeasurement(final String testname, final String classname) {
        this.testName = testname;
        this.className = classname;
        this.maximumStackDepth = 0;
        this.methodCalls = new HashMap<>();
        this.classInitialisations = new HashMap<>();
        this.trace = new ArrayList<>();
        this.depthTrace = new ArrayList<>();
    }

    /**
     * Gets the method calls Hash map.
     *
     * @return The method calls hashmap.
     */
    public HashMap<String, Long> getMethodCalls() {
        return this.methodCalls;
    }

    /**
     * Gets the class initialisations hash map.
     *
     * @return the class initialisation hash map.
     */
    public HashMap<String, Long> getClassInitialisations() {
        return this.classInitialisations;
    }

    /**
     * Sets the maximum stack depth.
     *
     * @param maxDepth The depth to set.
     */
    void setMaximumStackDepth(final long maxDepth) {
        this.maximumStackDepth = maxDepth;
    }

    String getDistinctMethodCount() {
        return Integer.toString(this.methodCalls.size());
    }

    String getDistinctClassCount() {
        return Integer.toString(this.classInitialisations.size());
    }
    /**
     * Counts the total number
     * of methods called.
     * @return The number of methods called.
     */
    long totalMethodCalls() {
        return this.methodCalls.values().stream()
                .mapToLong(Long::longValue)
                .sum();
    }
    /**
     * Counts the total number
     * of classes initialised.
     * @return The number of classes initialised.
     */
    long totalClassesInitialised() {
        return this.classInitialisations.values().stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    /**
     * Gets the maximum stack depth.
     *
     * @return The maximum stack depth.
     */
    long getMaximumStackDepth() {
        return maximumStackDepth;
    }

    /**
     * Gets the test name.
     * @return the test name.
     */
    String getTestName() {
        return this.testName;
    }

    /**
     * Gets the class name.
     * @return the class name.
     */
    String getClassName() {
        return this.className;
    }

    String getFQTestname() {
        return this.className + "." + this.testName;
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String lineSeparator = System.lineSeparator();
        sb.append("Test name: ").append(this.className)
                .append(".")
                .append(this.testName)
                .append(lineSeparator);
        sb.append("Maximum stack depth: ")
                .append(this.maximumStackDepth)
                .append(lineSeparator);
        if (!this.methodCalls.isEmpty()) {
            sb.append("Method Calls: ")
                    .append(lineSeparator);
            this.methodCalls.forEach((k, v) ->
                    sb.append("Method: ")
                            .append(k)
                            .append(" ")
                            .append("Calls: ")
                            .append(v)
                            .append(lineSeparator));
            sb.append("Total methods called: ")
                    .append(this.totalMethodCalls())
                    .append(lineSeparator);
        }
        if (!this.classInitialisations.isEmpty()) {
            sb.append("Class Initialisations: ")
                    .append(lineSeparator);
            this.classInitialisations.forEach((k, v) ->
                    sb.append("Class: ")
                            .append(k)
                            .append(" ")
                            .append("Initialised: ")
                            .append(v)
                            .append(lineSeparator));
            sb.append("Total classes initialised: ")
                    .append(this.totalClassesInitialised())
                    .append(lineSeparator);
        }
        return sb.toString();
    }
}
