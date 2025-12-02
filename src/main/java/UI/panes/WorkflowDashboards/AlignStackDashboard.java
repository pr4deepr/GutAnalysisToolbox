package UI.panes.WorkflowDashboards;

import ij.ImagePlus;
import ij.gui.Plot;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dashboard for showing aligned image stacks alongside their alignment results.
 */
public class AlignStackDashboard extends JPanel {

    private final JTabbedPane tabs = new JTabbedPane();

    public AlignStackDashboard() {
        super(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
    }

    /**
     * Displays a new aligned stack and its corresponding results CSV.
     * @param imp The aligned image stack.
     * @param resultCSV The CSV file containing alignment results.
     */
    public void addAlignedStackWithResults(ImagePlus imp, File resultCSV) {
        if (imp == null) {
            System.err.println("No aligned image provided, cannot display dashboard tab.");
            return;
        }

        // Show the aligned stack with a slider to scroll through slices
        JLabel imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setVerticalAlignment(SwingConstants.CENTER);
        updateImageLabel(imageLabel, imp, 1);

        JScrollPane imageScroll = new JScrollPane(imageLabel);
        imageScroll.setPreferredSize(new Dimension(500, 600));
        imageScroll.setBorder(BorderFactory.createTitledBorder("Aligned Stack"));

        JSlider slider = new JSlider(1, imp.getStackSize(), 1);
        slider.addChangeListener(e -> updateImageLabel(imageLabel, imp, slider.getValue()));

        JPanel imagePanel = new JPanel(new BorderLayout(5, 5));
        imagePanel.add(imageScroll, BorderLayout.CENTER);
        imagePanel.add(slider, BorderLayout.SOUTH);

        JPanel combined;

        if (resultCSV != null && resultCSV.exists()) {
            try {
                File cleanCSV = new File(resultCSV.getParent(), "CLEANED_" + resultCSV.getName());
                List<Double> slices = new ArrayList<>();
                List<Double> dx = new ArrayList<>();
                List<Double> dy = new ArrayList<>();

                try (BufferedReader br = new BufferedReader(new FileReader(resultCSV));
                    PrintWriter pw = new PrintWriter(new FileWriter(cleanCSV))) {

                    String headerLine = br.readLine();
                    if (headerLine != null) {
                        String[] headings = headerLine.split(",");
                        int sliceCol = headings.length - 3;
                        int dxCol = headings.length - 2;
                        int dyCol = headings.length - 1;

                        pw.println("Slice,Dx,Dy");

                        String line;
                        while ((line = br.readLine()) != null) {
                            String[] tokens = line.split(",");
                            if (tokens.length < 3) continue;

                            double sliceVal = Double.parseDouble(tokens[sliceCol]);
                            double dxVal = Double.parseDouble(tokens[dxCol]);
                            double dyVal = Double.parseDouble(tokens[dyCol]);

                            slices.add(sliceVal);
                            dx.add(dxVal);
                            dy.add(dyVal);

                            pw.println(sliceVal + "," + dxVal + "," + dyVal);
                        }
                    }
                }

                // Plot motion
                double[] x = slices.stream().mapToDouble(Double::doubleValue).toArray();
                double[] yDx = dx.stream().mapToDouble(Double::doubleValue).toArray();
                double[] yDy = dy.stream().mapToDouble(Double::doubleValue).toArray();

                Plot plot = new Plot("Alignment Motion", "Slice", "Pixels");
                plot.setColor(Color.RED);
                plot.addPoints(x, yDx, Plot.LINE);
                plot.setColor(Color.BLUE);
                plot.addPoints(x, yDy, Plot.LINE);

                ImagePlus plotImp = plot.getImagePlus();
                JScrollPane plotScroll = new JScrollPane(new JLabel(new ImageIcon(plotImp.getImage())));
                plotScroll.setBorder(BorderFactory.createTitledBorder("Motion Plot"));
                plotScroll.setPreferredSize(new Dimension(500, 600));

                // Combine stack and plot in a single panel
                combined = new JPanel(new GridLayout(1, 2, 10, 10));
                combined.add(imagePanel);
                combined.add(plotScroll);

            } catch (IOException ex) {
                ex.printStackTrace();
                System.err.println("Failed to read CSV results, only showing aligned stack.");
                combined = imagePanel; // fallback to image only
            }
        } else {
            if (resultCSV == null) System.err.println("No CSV results provided, only showing aligned stack.");
            else System.err.println("CSV results file does not exist: " + resultCSV.getAbsolutePath());
            combined = imagePanel;
        }

        // Add tab
        tabs.addTab(imp.getTitle() + (resultCSV != null ? " + Results" : ""), combined);
        tabs.setSelectedIndex(tabs.getTabCount() - 1);
    }

    // Updates the label to show the current slice of the image stack
    private void updateImageLabel(JLabel label, ImagePlus imp, int slice) {
        imp.setSlice(slice);
        label.setIcon(new ImageIcon(imp.getProcessor().getBufferedImage()));
    }
}
