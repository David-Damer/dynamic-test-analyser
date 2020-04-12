package analyser;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class Connection {

    /**
     * Line for breakpoint to read the test methods array
     * from the TestRunner in the java debug interface.
     */
    private static final int[] BREAK_POINT_LINES = {71, 72};

    /**
     * The suspend policy for event requests.
     */
    private final int suspendPolicy = EventRequest.SUSPEND_ALL;

    /**
     * The virtual machine created by the Launching Connector.
     */
    private VirtualMachine vm;

    /**
     * The project under test test classpath elements.
     */
    private List<String> testClasspathElements;

    /**
     * The TestRunner Class for the Launching Connector to
     * run as main.
     */
    private final Class<TestRunner> testRunner = TestRunner.class;

    /**
     * The project under test test directory.
     */
    private File testOutputDirectory;

    /**
     * List of method entry requests on this vm.
     */
    private List<MethodEntryRequest> methodEntryRequestList = new ArrayList<>();

    /**
     * List of method exit requests on this vm.
     */
    private List<MethodExitRequest> methodExitRequestList = new ArrayList<>();

    /**
     * List of inclusion filters for the method exit
     * and entry requests.
     */
    private List<String> methodEventFilters = new ArrayList<>();


    /**
     * The classes to include in the method entry
     * and exit requests.
     */
    private static final String[] INCLUDED_CLASSES = {
            "org.junit.jupiter.api.AssertionUtils",
            "org.junit.internal.runners.model.EachTestNotifier"
    };

    /**
     * Initialises a new instance of the Connection Class.
     *
     * @param testClassPathElements the project under test test classpath elements.
     * @param testDirectory         the project under test base directory.
     * @param project               the project under test.
     */
    Connection(final List<String> testClassPathElements,
               final File testDirectory,
               final MavenProject project) {
        this.testClasspathElements = testClassPathElements;
        this.testOutputDirectory = testDirectory;
        this.methodEventFilters.add(testRunner.getName());
        this.methodEventFilters.add(project.getGroupId() + "." + toRegExFilter(project.getName()));
        this.methodEventFilters.addAll(Arrays.asList(INCLUDED_CLASSES));
    }

    String buildClasspath() {
        String pathSeparator = System.getProperty("path.separator");
        StringBuilder classpath = new StringBuilder();
        classpath.append("-cp \"");
        for (String pathElement : testClasspathElements) {
            classpath.append(pathElement).append(pathSeparator);
        }
        classpath.append("\"");
        return classpath.toString();

    }

    VirtualMachine getVm() {
        return this.vm;
    }

    void launchTestRunner() throws IOException, IllegalConnectorArgumentsException, VMStartException {
        LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
        // "main" is test runner and command line arguments i.e. "java TestRunner args".
        arguments.get("main").setValue(testRunner.getName()
                + " "
                + this.wrapInQuotes(testOutputDirectory.getAbsolutePath()));
        arguments.get("options").setValue(this.buildClasspath());
        this.vm = launchingConnector.launch(arguments);
        this.enableClassPrepareRequest();
        this.createMethodEntryRequest();
        this.createMethodExitRequest();
    }

    String wrapInQuotes(final String in) {
        return "\"" + in + "\"";
    }

    /**
     * Sets and enables the class prepare request.
     */
    void enableClassPrepareRequest() {
        ClassPrepareRequest cpr = this.vm.eventRequestManager().createClassPrepareRequest();
        cpr.addClassFilter(testRunner.getName());
        cpr.enable();
    }

    private String toRegExFilter(final String classPrefix) {
        return classPrefix + ".*";
    }

    void setBreakPoints(final ClassPrepareEvent event) throws AbsentInformationException {
        ReferenceType referenceType = event.referenceType();
        BreakpointRequest bpr;
        for (int breakpoint : BREAK_POINT_LINES) {
            Location loc = referenceType.locationsOfLine(breakpoint).get(0);
            bpr = this.vm.eventRequestManager().createBreakpointRequest(loc);
            bpr.setSuspendPolicy(suspendPolicy);
            bpr.enable();
        }
    }

    void createMethodExitRequest() {
        for (String filter : methodEventFilters) {
            MethodExitRequest request = this.vm.eventRequestManager().createMethodExitRequest();
            request.addClassFilter(filter);
            request.setSuspendPolicy(suspendPolicy);
            this.methodExitRequestList.add(request);
        }
    }

    void enableMethodExitRequest() {
        for (MethodExitRequest request : this.methodExitRequestList) {
            request.enable();
        }
    }

    void disableMethodExitRequest() {
        for (MethodExitRequest request : this.methodExitRequestList) {
            request.disable();
        }
    }

    void createMethodEntryRequest() {
        for (String filter : methodEventFilters) {
            MethodEntryRequest request = this.vm.eventRequestManager().createMethodEntryRequest();
            request.addClassFilter(filter);
            request.setSuspendPolicy(suspendPolicy);
            this.methodEntryRequestList.add(request);
        }
    }

    void enableMethodEntryRequest() {
        for (MethodEntryRequest request : this.methodEntryRequestList) {
            request.enable();
        }
    }

    void disableMethodEntryRequest() {
        for (MethodEntryRequest request : this.methodEntryRequestList) {
            request.disable();
        }
    }

    TestClassDataObj readTestNamesFromRunner(final LocatableEvent event)
            throws IncompatibleThreadStateException, AbsentInformationException {
        List<String> tests = new ArrayList<>();
        StackFrame stackFrame = event.thread().frame(0);
        LocalVariable tm = stackFrame.visibleVariableByName("testMethods");
        LocalVariable className = stackFrame.visibleVariableByName("testClass");
        ArrayReference arrayReference = (ArrayReference) stackFrame.getValue(tm);
        StringReference name = (StringReference) stackFrame.getValue(className);
        List<Value> arrayValues = arrayReference.getValues();
        for (Value val : arrayValues) {
            tests.add(((StringReference) val).value());
        }
        return new TestClassDataObj(name.value(), tests);
    }
}
