package analyser;

import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Test runner class.
 */
public final class TestRunner {

    /**
     * Private constructor.
     */
    private TestRunner() {
        // ignored
    }

    /**
     * The array of test class names to be executed.
     */
    private static ArrayList<String> testClassNames = new ArrayList<>();

    /**
     * The main method of the test runner class.
     *
     * @param args command line args.
     * @throws MalformedURLException if a malformed url is used.
     */
    public static void main(final String[] args) throws MalformedURLException {
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        File testRootDirectory = new File(args[0]);
        DirectoryScanner scanner = new DirectoryScanner();
        testClassNames = scanner.scanDirectory(testRootDirectory);
        URL url = testRootDirectory.toURI().toURL();
        URL[] urls = {url};
        ClassLoader loader = new URLClassLoader(urls);
        for (String testClass : testClassNames) {
            Class<?> test;
            try {
                test = loader.loadClass(testClass);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                continue;
            }
            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(selectClass(test))
                    .build();
            List<EngineFilter> params = request.getEngineFilters();
            Launcher launcher = LauncherFactory.create();
            TestPlan plan = launcher.discover(request);
            // testMethods to be read by jdi to executing mojo.
            if (plan.containsTests()) {
                String[] testMethods = getTestNamesFromPlan(plan);
                launcher.registerTestExecutionListeners(listener);
                launcher.execute(request);
                int x = 1; // Need this line to put a breakpoint here.
            }
        }
    }

    /**
     * Method to get test names from test plan.
     *
     * @param plan The test plan to get the names from.
     * @return an array of test names from the plan.
     */
    private static String[] getTestNamesFromPlan(final TestPlan plan) {
        List<String> tests = new ArrayList<>();
        Set<TestIdentifier> planRoots = plan.getRoots();
        for (TestIdentifier tid : planRoots) {
            Set<TestIdentifier> descendants = plan.getDescendants(tid);
            for (TestIdentifier dtid : descendants) {
                if (dtid.isTest()) {
                    String testName = dtid.getLegacyReportingName();
                    // test names are reported differently between junit4 and junit5
                    if (testName.endsWith("()")) { // 4
                        tests.add(testName.substring(0, testName.lastIndexOf("(")));
                    } else { // 5
                        tests.add(testName);
                    }
                }
            }
        }
        return tests.toArray(String[]::new);
    }
}

