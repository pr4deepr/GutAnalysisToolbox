package UI.panes.WorkflowDashboards;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import Analysis.CalciumAnalysis;
import Features.Core.Params;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * This class is a JPanel-based dashboard for performing calcium
 * imaging analysis steps with interactive buttons and tabs for displaying image data and analysis
 * results.
 */
public class CalciumImagingAnalysisDashboard extends JPanel {

    private final JTabbedPane tabs = new JTabbedPane();
    private final JButton btnOpenImage = new JButton("Step 1: Open Image");
    private final JButton btnMaxProj = new JButton("Step 2: Max Projection");
    private final JButton btnNormalize = new JButton("Step 3: F/F0 Normalization");
    private final JButton btnSetupROIs = new JButton("Step 4: Setup ROI Manager");
    private final JButton btnDrawROIs = new JButton("Step 5: Draw/Import ROIs");
    private final JButton btnRenameROIs = new JButton("Step 6: Rename ROIs");
    private final JButton btnMeasure = new JButton("Step 7: Measure ROIs");
    private final JButton btnSave = new JButton("Step 8: Save Results");

    private CalciumAnalysis analysis;
    private Params params;

    /** Constructor method initialises the CalciumImagingAnalysisDashboard class with a set of parameters
     * @param p the params passed from the options part of the UI pane as selected by the user
    */
    public CalciumImagingAnalysisDashboard(Params p) {
        super(new BorderLayout());
        this.params = p;
        analysis = new CalciumAnalysis(params);

        // --- Button Panel ---
        JPanel controlPanel = new JPanel(new GridLayout(8, 1, 5, 5));
        controlPanel.add(btnOpenImage);
        controlPanel.add(btnMaxProj);
        controlPanel.add(btnNormalize);
        controlPanel.add(btnSetupROIs);
        controlPanel.add(btnDrawROIs);
        controlPanel.add(btnRenameROIs);
        controlPanel.add(btnMeasure);
        controlPanel.add(btnSave);

        add(controlPanel, BorderLayout.WEST);
        add(tabs, BorderLayout.CENTER);

        // --- Button actions ---
        btnOpenImage.addActionListener(e -> analysis.openImage());
        btnMaxProj.addActionListener(e -> {
            if (analysis.maxProj == null) {
                IJ.showMessage("Please open an image first.");
                return;
            }
            analysis.createMaxProjection();
        });
        btnNormalize.addActionListener(e -> {
            if (analysis.normStack == null && analysis.maxProj == null) {
                IJ.showMessage("Please perform Max Projection first.");
                return;
            }
            analysis.normalizeStack();
            if (params.useFF0) addImageTab(analysis.normStack, "F/F0 Stack");
        });
        btnSetupROIs.addActionListener(e -> analysis.setupROIManager());
        btnDrawROIs.addActionListener(e -> {
            if (analysis.normStack == null) {
                IJ.showMessage("Please perform normalization first.");
                return;
            }
            for (int i = 0; i < params.cellTypes; i++) {
                analysis.handleCellType(i);
            }
        });
        btnRenameROIs.addActionListener(e -> {
            if (analysis.normStack == null) {
                IJ.showMessage("Please perform normalization first.");
                return;
            }
            analysis.renameROIs();
        });
        btnMeasure.addActionListener(e -> {
            if (analysis.normStack == null) {
                IJ.showMessage("Please perform normalization first.");
                return;
            }
            analysis.measureROIs();
        });
        btnSave.addActionListener(e -> {
            if (analysis.normStack == null) {
                IJ.showMessage("Please perform normalization and ROIs first.");
                return;
            }
            File resultsFile = analysis.saveResults();
            if (resultsFile != null && resultsFile.exists()) {
                addResultsPlot(resultsFile);
            }
        });
    }

    /**
     * Creates a new tab in a GUI interface to display an image with a
     * scrollable canvas and a slider for navigating through image slices.
     * 
     * @param imp An ImagePlus object, which
     * represents an image or stack of images in ImageJ/Fiji. It contains the image data and associated
     * metadata.
     * @param title String that represents the
     * title of the tab that will be added to a JTabbedPane. This title will be displayed on the tab
     * in the user interface to help identify the content of that tab.
     */
    private void addImageTab(ImagePlus imp, String title) {
        if (imp == null) return;

        ImageCanvas canvas = new ImageCanvas(imp);
        JScrollPane scroll = new JScrollPane(canvas);
        scroll.setPreferredSize(new Dimension(900, 600));
        scroll.setBorder(BorderFactory.createTitledBorder(title));

        JSlider slider = new JSlider(1, imp.getStackSize(), 1);
        slider.addChangeListener(e -> {
            imp.setSlice(slider.getValue());
            canvas.repaint();
        });

        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(slider, BorderLayout.SOUTH);

        tabs.addTab(title, panel);
        tabs.setSelectedIndex(tabs.getTabCount() - 1);
    }

    /**
     * The function reads data from a CSV file, processes it to create a plot of ROI
     * traces, and displays the plot in ImageJ.
     * 
     * @param csvFile File object
     * representing the CSV file that contains the data to be plotted. This method reads the data from
     * the CSV file, processes it, and generates a plot of the ROI traces based on the data in the file
     */
    private void addResultsPlot(File csvFile) {
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(csvFile))) {
            java.util.List<String[]> rows = new java.util.ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                rows.add(line.split(","));
            }

            if (rows.size() < 2) {
                IJ.showMessage("Results file is empty or invalid.");
                return;
            }

            // --- First row is header ---
            String[] headers = rows.get(0);
            int roiCount = headers.length - 1;  // assuming first column is Frame

            int frameCount = rows.size() - 1;
            double[] x = new double[frameCount]; // time or frame index
            double[][] y = new double[roiCount][frameCount]; // intensities for each ROI

            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                x[i - 1] = Double.parseDouble(row[0]);
                for (int r = 0; r < roiCount; r++) {
                    try {
                        y[r][i - 1] = Double.parseDouble(row[r + 1]);
                    } catch (NumberFormatException ex) {
                        y[r][i - 1] = Double.NaN;
                    }
                }
            }

            // --- Plot all ROI traces ---
            ij.gui.Plot plot = new ij.gui.Plot("ROI Traces", "Frame", "Mean Intensity (F/F₀)", x, y[0]);

            for (int r = 1; r < roiCount; r++) {
                plot.setColor(new java.awt.Color((r * 40) % 255, (r * 90) % 255, (r * 60) % 255));
                plot.addPoints(x, y[r], ij.gui.Plot.LINE);
            }

            ImagePlus plotImage = plot.getImagePlus();
            plotImage.hide();
            addImageTab(plotImage, "ROI Traces Plot");

        } catch (Exception ex) {
            IJ.showMessage("Error creating plot: " + ex.getMessage());
        }
    }

    
}
