package analyser;

import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

/**
 * Create an xml report from the test measurements
 * collected by the analyser.
 */
public final class XMLReportWriter {

    /**
     * XML header string.
     */
    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>";

    /**
     * Output file name.
     */
    private static final String OUTPUT_FILE_NAME = "testAnalyserReport.xml";
    /**
     * The project under test.
     */
    private MavenProject mavenProject;

    /**
     * The report file.
     */
    private File outputfile;

    /**
     * System line separator.
     */
    private String separator;

    /**
     * File writer.
     */
    private FileWriter writer;


    XMLReportWriter(final MavenProject project) {
        this.mavenProject = project;
        this.outputfile = new File(OUTPUT_FILE_NAME);
        this.separator = System.lineSeparator();
        try {
            this.writer = new FileWriter(this.outputfile, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Builds and writes an xml report to file.
     */
    void openReport() {
        try {
            String rootNode = "<" + mavenProject.getName() + ">" + separator;
            this.writer.append(XML_HEADER).append(separator);
            this.writer.append(rootNode);
            this.writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Adds a test to the report.
     *
     * @param tm the test measurements to add to the report.
     */
    public void addTestMeasurement(final TestMeasurement tm) {
        try {
            this.writer = new FileWriter(this.outputfile, true);
            String testNode = this.measurementToNode(tm);
            writer.write(testNode);
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Closes the xml report.
     */
    public void closeReport() {
        try {
            this.writer = new FileWriter(this.outputfile, true);
            String closeString = "</" + this.mavenProject.getName() + ">" + separator;
            writer.write(closeString);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String measurementToNode(final TestMeasurement tm) {
        StringBuilder test = new StringBuilder();
        String testElement = getTestElement(tm);
        test.append(testElement);
        String depth = getDepthElement(tm);
        test.append(depth);
        String methods = getMethodsElement(tm);
        test.append(methods);
        for (Map.Entry<String, Long> entry : tm.getMethodCalls().entrySet()) {
            String method = getMethodElement(entry);
            test.append(method);
        }
        test.append("     </MethodsCalled>" + separator);
        String classes = getClassesElement(tm);
        test.append(classes);
        for (Map.Entry<String, Long> entry : tm.getClassInitialisations().entrySet()) {
            String clazz = getClassElement(entry);
            test.append(clazz);
        }
        test.append("     </ClassesInitialised>" + separator);
        String trace = getTraceElement(tm);
        test.append(trace);
        for (String traceline : tm.getTrace()) {
            String traceLineElement = getTraceLine(traceline);
            test.append(traceLineElement);
        }
        test.append("     </Trace>").append(separator);
        test.append("  </Test>").append(separator);
        return test.toString();
    }

    private String getTraceLine(final String traceline) {
        return "          <TraceElement depth=\""
                + traceline.substring(traceline.indexOf('(') + 1, traceline.indexOf(')'))
                + "\" "
                + "method=\""
                + traceline.substring(0, traceline.indexOf('(') - 1)
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                + "\"/>"
                + separator;

    }

    private String getTraceElement(final TestMeasurement tm) {
        return "     <Trace length=\""
                + tm.getTrace().size()
                + "\">"
                + separator;
    }

    private String getClassElement(final Map.Entry<String, Long> entry) {
        return "          <Class count=\""
                + entry.getValue()
                + "\" name=\""
                + entry.getKey()
                + "\"/>"
                + separator;
    }

    private String getMethodElement(final Map.Entry<String, Long> entry) {
        return "          <Method calls=\""
                + entry.getValue()
                + "\" name=\""
                + entry.getKey()
                + "\"/>"
                + separator;
    }

    private String getClassesElement(final TestMeasurement tm) {
        return "     <ClassesInitialised distinct=\""
                + tm.getDistinctClassCount()
                + "\" total=\""
                + tm.totalClassesInitialised()
                + "\">"
                + separator;
    }

    private String getMethodsElement(final TestMeasurement tm) {
        return "     <MethodsCalled distinct=\""
                + tm.getDistinctMethodCount()
                + "\" total=\""
                + tm.totalMethodCalls()
                + "\">"
                + separator;

    }

    private String getDepthElement(final TestMeasurement tm) {
        return "     <MaxStackDepth depth=\""
                + tm.getMaximumStackDepth()
                + "\"/>"
                + separator;
    }

    private String getTestElement(final TestMeasurement tm) {
        return "  <Test class=\""
                + tm.getClassName()
                + "\" name=\""
                + tm.getTestName()
                + "\">"
                + separator;
    }
}
