package analyser;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.QuickChart;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.style.Styler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.IntStream;

public class ChartDrawer {

    /**
     * Output directory name.
     */
    private static final String OUTPUT_DIRECTORY = "./testAnalysisGraphs/";

    /**
     * Resolution setting for png files.
     */
    private static final int DPI = 300;

    /**
     * Angle for label rotation.
     */
    private static final int LABEL_ROTATION = 90;

    ChartDrawer() {
        File f = new File(OUTPUT_DIRECTORY);
        f.mkdir();
    }


    /**
     * Draws depth charts per test analysed.
     *
     * @param packageName The name of the package.
     * @param className   The name of the Class.
     * @param testName    The name of the test.
     * @param depths      The depths to plot.
     */
    public void drawDepthChart(final String packageName, final String className, final String testName, final List<Integer> depths) {
        File f = new File(OUTPUT_DIRECTORY + "/" + packageName);
        f.mkdir();
        List<Integer> xData = new ArrayList<>();
        IntStream.range(0, depths.size()).forEach(xData::add);
        XYChart chart = QuickChart.getChart(testName, "Step", "Depth", "Depth over time", xData, depths);
        chart.getStyler().setXAxisLabelRotation(LABEL_ROTATION);
        try {
            BitmapEncoder.saveBitmapWithDPI(chart, OUTPUT_DIRECTORY + packageName + "/" + className + "." + testName, BitmapEncoder.BitmapFormat.PNG, DPI);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Draws the suites method call histogram.
     */
    public void drawClusteredHistogramsForMethodCalls(HashMap<String, ArrayList<Integer>> methodCalls, String packageName) {
        CategoryChart chart = new CategoryChartBuilder()
                .width(1200)
                .height(1000)
                .title("Method calls per test for package")
                .xAxisTitle("Test")
                .yAxisTitle("Count")
                .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        chart.getStyler().setAvailableSpaceFill(.96);
        chart.getStyler().setOverlapped(false);

        List<Integer> totals = new ArrayList<>();
        List<Integer> distinct = new ArrayList<>();
        List<String> testNames = new ArrayList<>();

        methodCalls.forEach((k, v) -> {
            distinct.add(v.get(0));
            totals.add(v.get(1));
            testNames.add(k);
        });
        chart.getStyler().setXAxisTicksVisible(true);
        chart.getStyler().setXAxisLabelRotation(LABEL_ROTATION);
        chart.addSeries("Total methods called", testNames, totals);
        chart.addSeries("Distinct methods called", testNames, distinct);

        try {
            BitmapEncoder.saveBitmapWithDPI(chart, OUTPUT_DIRECTORY + "/" + packageName + "/" + "Package-Method-Summary", BitmapEncoder.BitmapFormat.PNG, DPI);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Draws the suites method call histogram.
     */
    public void drawClusteredHistogramsForConstructorCalls(HashMap<String, ArrayList<Integer>> classInits, String packageName) {
        CategoryChart chart = new CategoryChartBuilder()
                .width(1200)
                .height(1000)
                .title("Classes initialised per test for package")
                .xAxisTitle("Test")
                .yAxisTitle("Count")
                .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        chart.getStyler().setAvailableSpaceFill(.96);
        chart.getStyler().setOverlapped(false);

        List<Integer> totals = new ArrayList<>();
        List<Integer> distinct = new ArrayList<>();
        List<String> testNames = new ArrayList<>();
        classInits.forEach((k, v) -> {
            distinct.add(v.get(0));
            totals.add(v.get(1));
            testNames.add(k);
        });
        chart.getStyler().setXAxisTicksVisible(true);
        chart.getStyler().setXAxisLabelRotation(LABEL_ROTATION);
        chart.addSeries("Total classes initialised", testNames, totals);
        chart.addSeries("Distinct classes initialised", testNames, distinct);

        try {
            BitmapEncoder.saveBitmapWithDPI(chart, OUTPUT_DIRECTORY + "/" + packageName + "/" + "Package-Class-Initialisations-Summary", BitmapEncoder.BitmapFormat.PNG, DPI);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
