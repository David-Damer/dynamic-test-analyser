package analyser;


import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;


@Mojo(name = "analyse-test", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class TestAnalyser extends AbstractMojo {

    // Mojo Fields.

    /**
     * The buffer size for reading the output streams from
     * the test runner.
     */
    private static final int BUFFER_SIZE = 4096;

    /**
     * The empty string.
     */
    private static final String EMPTY_STRING = "";

    /**
     * Constructor method name.
     */
    private static final String CONSTRUCTOR = "<init>";

    /**
     * The Junit class prefix.
     */
    public static final String JUNIT_CLASS_PREFIX = "org.junit";

    /**
     * {@link TestClassDataObj} containing the class name and test names..
     */
    private TestClassDataObj testClassDataObj = new TestClassDataObj();

    /**
     * Test we are currently executing.
     */
    private String runningTestName = EMPTY_STRING;

    /**
     * {@link TestMeasurement} for the in method.
     */
    private TestMeasurement testMeasurement;

    /**
     * {@link ArrayList } of test measurements.
     */
    private List<TestMeasurement> testMeasurements = new ArrayList<>();

    // Parameters from plugin pom.

    /**
     * The project under test.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject mavenProject;

    /**
     * Test output directory.
     */
    @Parameter(defaultValue = "${project.build.testSourceDirectory}")
    private File testSourceDirectory;

    /**
     * Breakpoint at which to enable method entry requests.
     */
    public static final int ENABLE_METHOD_ENTRY_LINE = 71;

    /**
     * Breakpoint at which to disable method entry requests.
     */
    public static final int DISABLE_METHOD_ENTRY_LINE = 72;

    /**
     * Stack for counting how deep the test method goes into the code.
     */
    private Deque<String> testStack = new ArrayDeque<>();

    /**
     * Report writer.
     */
    private XMLReportWriter report;

    /**
     * "Main" plugin method.
     *
     * @throws MojoExecutionException on execution exceptions.
     */
    public final void execute() throws MojoExecutionException {
        this.report = new XMLReportWriter(this.mavenProject);
        this.report.openReport();
        List<String> projectTestClasspath;
        try {
            projectTestClasspath = this.mavenProject.getTestClasspathElements();
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Cannot resolve test classpath", e.getCause());
        }
        ReportParser reportParser = new ReportParser(this.mavenProject);
        Connection connection = new Connection(projectTestClasspath, this.testSourceDirectory, this.mavenProject);
        try {
            getLog().info("Running tests and collecting data");
            connection.launchTestRunner();
            EventSet eventSet;
            boolean run = true;
            while (run) {
                eventSet = connection.getVm().eventQueue().remove();
                for (Event event : eventSet) {
                    getLog().debug(event.toString());
                    if (event instanceof ClassPrepareEvent) {
                        handleClassPrepareEvent(connection, event);
                    }
                    if (event instanceof BreakpointEvent) {
                        handleBreakpointEvent(connection, event);
                    }
                    if (event instanceof MethodEntryEvent) {
                        handleMethodEntryEvent(connection, event);
                    }
                    if (event instanceof MethodExitEvent) {
                        handleMethodExitEvent(connection, event);
                    }
                    if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
                        getLog().info("Disconnecting from the VM");
                        logRunnerOutput(connection);
                        run = false;
                        break;
                    }
                    connection.getVm().resume();
                }
            }
        } catch (VMDisconnectedException e) {
            getLog().debug(e);
        } catch (Exception e) {
            getLog().error(e);
            throw new MojoExecutionException("Exception occurred in plugin", e.getCause());
        } finally {
            this.report.closeReport();
            getLog().info(EMPTY_STRING);
            getLog().debug("Measurements collected:");
            try {
                logRunnerOutput(connection);
            } catch (IOException e) {
                e.printStackTrace();
            }
            reportParser.parseReportAndProduceGraphs();
        }
    }

    /**
     * Log output from test runner.
     *
     * @param connection The test runner connection.
     * @throws IOException If the streams cannot be read.
     */
    private void logRunnerOutput(final Connection connection) throws IOException {
        String testRunnerOutput = readOutAndErrStreams(connection);
        if (!testRunnerOutput.isEmpty()) {
            getLog().info(testRunnerOutput);
        }
    }

    /**
     * Handles the Class Prepare Events.
     *
     * @param connection the connection.
     * @param event      the event to handle.
     * @throws AbsentInformationException if debug info is not available.
     */
    private void handleClassPrepareEvent(final Connection connection, final Event event)
            throws AbsentInformationException {
        ClassPrepareEvent classPrepareEvent = (ClassPrepareEvent) event;
        getLog().debug("Handling class prepare event: " + event.toString());
        connection.setBreakPoints(classPrepareEvent);
    }

    /**
     * Handles Breakpoint Events.
     *
     * @param connection the connection.
     * @param event      the event to handle.
     * @throws IncompatibleThreadStateException if the thread is not suspended.
     * @throws AbsentInformationException       if debug info is not available.
     * @throws IOException                      if reading the output streams cause an exception..
     */
    private void handleBreakpointEvent(final Connection connection, final Event event)
            throws IncompatibleThreadStateException, AbsentInformationException, IOException {
        int lineNumber = ((BreakpointEvent) event).location().lineNumber();
        if (lineNumber == ENABLE_METHOD_ENTRY_LINE) {
            this.testClassDataObj = connection.readTestNamesFromRunner((BreakpointEvent) event);
            getLog().info("---------------------------------");
            getLog().info("Analysing tests in: " + this.testClassDataObj.getTestClassName());
            getLog().info("---------------------------------");
            connection.enableMethodEntryRequest();
            connection.enableMethodExitRequest();
        }
        if (lineNumber == DISABLE_METHOD_ENTRY_LINE) {
            connection.disableMethodEntryRequest();
            connection.disableMethodExitRequest();
            logRunnerOutput(connection);
        }
    }

    /**
     * Handles Method Entry Events.
     *
     * @param connection the connection.
     * @param event      the event to handle.
     */
    private void handleMethodEntryEvent(final Connection connection, final Event event) {
        MethodEntryEvent methodEntryEvent = (MethodEntryEvent) event;
        getLog().debug("Handling Method Entry Event: " + methodEntryEvent.toString());
        String methodName = methodEntryEvent.method().name();
        String className = methodEntryEvent.method().declaringType().name();
        if (this.testClassDataObj.getTestMethods().contains(methodName)) {
            this.handleTestEntry(connection, methodEntryEvent, methodName);
        }
        if (this.inTest()) {
            this.handleMethodEntryWhileInTestExecution(
                    methodName,
                    className
            );
        }
    }

    /**
     * Handles Method Exit Events.
     *
     * @param connection the connection.
     * @param event      the event to handle.
     */
    private void handleMethodExitEvent(final Connection connection, final Event event) {
        MethodExitEvent methodExitEvent = (MethodExitEvent) event;
        String className = methodExitEvent.method().declaringType().name();
        String methodname = methodExitEvent.method().name();
        getLog().debug("Handling Method Exit Event: " + methodExitEvent.toString());
        if (methodname.equals(this.runningTestName)) {
            this.exitTest(className + "." + methodname);
        }
        if (this.inTest()) {
            handleMethodExitEventInTest(className + "." + methodname, className);
        }
    }

    private void handleMethodExitEventInTest(final String fqMethodname, final String className) {
        if (!className.startsWith(JUNIT_CLASS_PREFIX)) {
            this.testStack.removeFirst();
            this.testMeasurement.getTrace().add("Exit: " + fqMethodname + ":(" + (testStack.size()) + ")");
            this.testMeasurement.getDepthTrace().add(testStack.size());
        }
    }


    /**
     * Suspends the running thread if it is still running.
     *
     * @param thread the thread to suspend.
     */
    private void suspendIfRequired(final ThreadReference thread) {
        if (!thread.isSuspended()) {
            thread.suspend();
        }
    }

    /**
     * Handle test entry events.
     *
     * @param connection the connection.
     * @param event      the event.
     * @param methodName the method entered.
     */
    private void handleTestEntry(final Connection connection,
                                 final MethodEntryEvent event,
                                 final String methodName) {
        this.runningTestName = methodName;
        String className = this.testClassDataObj.getTestClassName();
        getLog().info("Analysing Test: "
                + className
                + "."
                + this.runningTestName);
        this.testMeasurement = new TestMeasurement(
                this.runningTestName, className
        );
        connection.enableMethodExitRequest();
    }

    /**
     * Handles a method entry event.
     * Counts methods entered and classes constructed.
     * <br>
     * <p>
     * If the test throws an exception (even an expected exception)
     * it does not exit normally (in junit4) and
     * trigger a MethodExitEvent so we need to check for the
     * fireTest and fail methods of junit instead.
     * </p>
     *
     * @param methodName The name of the method entered.
     * @param className  The name of the class the method belongs to.
     */
    private void handleMethodEntryWhileInTestExecution(
            final String methodName,
            final String className) {
        String fullyQualifiedMethodName = className + "." + methodName;
        if (!className.startsWith(JUNIT_CLASS_PREFIX)) {
            this.testMeasurement.getTrace().add("Entry: " + fullyQualifiedMethodName + ":(" + (testStack.size()) + ")");
            this.testMeasurement.getDepthTrace().add(testStack.size());
            this.testStack.addFirst(className);
        }

        getLog().debug("Method execution in test:  " + fullyQualifiedMethodName);
        int depth = testStack.size() - 1;
        if (depth > this.testMeasurement.getMaximumStackDepth()) {
            this.testMeasurement.setMaximumStackDepth(depth);
        }
        if (isTextExitMethod(methodName, className)) {
            this.exitTest(fullyQualifiedMethodName);
        } else if (isRecordableMethodCall(methodName, className)) {
            this.incrementOrAddKey(fullyQualifiedMethodName, this.testMeasurement.getMethodCalls());
        }
        if (isConstructor(methodName) && !className.startsWith(JUNIT_CLASS_PREFIX)) {
            this.incrementOrAddKey(className, this.testMeasurement.getClassInitialisations());
        }
    }

    /**
     * Whether the method is a constructor.
     *
     * @param methodName the method to determine.
     * @return whether the method is a constructor.
     */
    private boolean isConstructor(final String methodName) {
        return methodName.equals(CONSTRUCTOR);
    }

    /**
     * Should we record the method call.
     *
     * @param methodName the method.
     * @param className  the class of the method.
     * @return whether we should record the method call.
     */
    private boolean isRecordableMethodCall(final String methodName, final String className) {
        return !(className.startsWith(JUNIT_CLASS_PREFIX)
                || methodName.equals(this.runningTestName)
                || isConstructor(methodName)
                || methodName.equals("<clinit>"));
    }

    /**
     * Determine if a method indicates a test is finished.
     *
     * @param methodName the method tot check.
     * @param className  the class the method belongs to.
     * @return whether the method is an exit method.
     */
    private boolean isTextExitMethod(final String methodName, final String className) {
        return (methodName.startsWith("fireTest") || methodName.startsWith("fail"))
                && className.startsWith(JUNIT_CLASS_PREFIX);
    }

    /**
     * Increment or add key, value to HashMap.
     *
     * @param keyToIncrement The item in the map to be incremented or added.
     * @param hashMap        The map the item is in.
     */
    private void incrementOrAddKey(final String keyToIncrement, final HashMap<String, Long> hashMap) {
        long val = hashMap.getOrDefault(keyToIncrement, (long) 0);
        hashMap.put(keyToIncrement, ++val);
    }

    /**
     * Check if a test is running.
     *
     * @return true if a test is running otherwise false.
     */
    private boolean inTest() {
        return !this.runningTestName.isEmpty();
    }

    /**
     * Handle exiting a test.
     *
     * @param fqMethodname fully qualified method name.
     */
    private void exitTest(final String fqMethodname) {
        this.testMeasurement.getTrace().add("Exit: " + fqMethodname + ":(" + (testStack.size() - 1) + ")");
        this.testMeasurement.getDepthTrace().add(testStack.size() - 1);
        this.report.addTestMeasurement(this.testMeasurement);
        getLog().info("Test finished: " + this.testClassDataObj.getTestClassName() + "." + this.runningTestName);
        this.runningTestName = EMPTY_STRING;
        this.testStack.clear();
    }

    /**
     * Method to read the output and error streams from the launched VM and
     * return it as a string.
     *
     * @param connection The connection to read from.
     * @return the output string from the test runner.
     * @throws IOException If the test runner output streams cannot be read.
     */
    private String readOutAndErrStreams(final Connection connection) throws IOException {
        Process process = connection.getVm().process();
        InputStreamReader readerOut = new InputStreamReader(process.getInputStream());
        InputStreamReader readerErr = new InputStreamReader(process.getErrorStream());
        StringBuilder testRunnerOutput = new StringBuilder();
        String lineSeparator = System.lineSeparator();
        if (readerOut.ready()) {
            testRunnerOutput.append(streamToString(readerOut)).append(lineSeparator);
        }
        if (readerErr.ready()) {
            testRunnerOutput.append(streamToString(readerErr)).append(lineSeparator);
        }
        return testRunnerOutput.toString();
    }

    /**
     * Reads a char stream to a string.
     *
     * @param reader The stream to read from.
     * @return The converted string.
     * @throws IOException If an IO exception occurs.
     */
    private String streamToString(final InputStreamReader reader) throws IOException {
        char[] buffer = new char[BUFFER_SIZE];
        int charsInBuffer = reader.read(buffer);
        return new String(buffer, 0, charsInBuffer);
    }
}
