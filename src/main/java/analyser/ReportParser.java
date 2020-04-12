package analyser;

import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Parse the xml trace and produce the graphs for the metrics.
 */
public class ReportParser {

    /**
     * The report filename for parsing.
     */
    private static final String REPORT_FILE = "testAnalyserReport.xml";

    /**
     * The project under tests base package.
     */
    private MavenProject project;

    /**
     * Test nodes organised by packages.
     */
    private HashMap<String, ArrayList<Node>> packages;

    /**
     * Draws the graphs.
     */
    private ChartDrawer drawer;

    /**
     * It's a constructor init.
     *
     * @param mavenProject the maven project under analysis.
     */
    public ReportParser(final MavenProject mavenProject) {
        this.project = mavenProject;
        this.packages = new HashMap<>();
        this.drawer = new ChartDrawer();
    }

    /**
     * Parses the report to produce the metrics graphs.
     */
    public void parseReportAndProduceGraphs() {
        try {
            File report = new File(REPORT_FILE);
            DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = documentBuilder.parse(report);
            doc.getDocumentElement().normalize();
            NodeList nodes = doc.getElementsByTagName("test");
            // organise nodelist to packages
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String className = element.getAttribute("class");
                    String packageName = className.substring(0, className.lastIndexOf("."));
                    ArrayList<Node> packageNodes = packages.getOrDefault(packageName, new ArrayList<>());
                    packageNodes.add(node);
                    packages.put(packageName, packageNodes);
                }
            }
            // get trace depth list for package and produce method call and class initialisation graphs per package.
            for (Map.Entry<String, ArrayList<Node>> packageName : this.packages.entrySet()) {
                HashMap<String, ArrayList<Integer>> testMethodCalls = new HashMap<>();
                HashMap<String, ArrayList<Integer>> testClassInits = new HashMap<>();
                for (Node n : packageName.getValue()) {
                    if (n.getNodeType() == Node.ELEMENT_NODE) {
                        List<Integer> depthTrace = new ArrayList<>();
                        Element trace = (Element) n;
                        NodeList traceElements = trace.getElementsByTagName("TraceElement");
                        for (int i = 0; i < traceElements.getLength(); i++) {
                            Element traceElement = (Element) traceElements.item(i);
                            depthTrace.add(Integer.parseInt(traceElement.getAttribute("depth")));
                        }
                        String testClass = trace.getAttribute("class");
                        String testClassName = testClass.substring(testClass.lastIndexOf(".") + 1);
                        String testName = trace.getAttribute("name");
                        this.drawer.drawDepthChart(packageName.getKey(), testClassName, testName, depthTrace);

                        ArrayList<Integer> methodCounts = new ArrayList<>();
                        Element methods = (Element) trace.getElementsByTagName("MethodsCalled").item(0);
                        methodCounts.add(Integer.parseInt(methods.getAttribute("distinct")));
                        methodCounts.add(Integer.parseInt(methods.getAttribute("total")));
                        testMethodCalls.put(testClassName + "." + testName, methodCounts);

                        ArrayList<Integer> classCounts = new ArrayList<>();
                        Element classes = (Element) trace.getElementsByTagName("ClassesInitialised").item(0);
                        classCounts.add(Integer.parseInt(classes.getAttribute("distinct")));
                        classCounts.add(Integer.parseInt(classes.getAttribute("total")));
                        testClassInits.put(testClassName + "." + testName, classCounts);
                    }

                }
                this.drawer.drawClusteredHistogramsForMethodCalls(testMethodCalls, packageName.getKey());
                this.drawer.drawClusteredHistogramsForConstructorCalls(testClassInits, packageName.getKey());
            }

        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
    }

}
