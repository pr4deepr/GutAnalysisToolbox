package UI.panes.WorkflowDashboards;

import Features.Core.Params;
import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;

/**
 * Dashboard panel for displaying temporal color-coded image stacks and their
 * associated analysis parameters.
 *
 * This panel is part of the workflow dashboard UI and provides a compact visualization
 * of temporally color-coded calcium imaging data. It displays:
 *
 *  A preview of the color-coded image stack</li>
 *  Relevant processing parameters (e.g., LUT, projection, batch mode
 *  A dynamic bar plot showing average intensity per frame with corresponding LUT colors
 *
 * */
public class TemporalColourDashboardPane extends JPanel {

    private JPanel imagePanel;
    private JTextArea paramInfo;
    private JPanel intensityPlotPanel;

    private double[] frameIntensity; // average intensity per frame
    private Color[] frameColors;     // LUT colors per frame
    private ImagePlus rgbStack;
    private JPanel imageContainer;
    private JButton saveBtn;

    /**
     * Constructs a new dashboard panel for displaying temporal color-coded analysis results.
     *
     * @param owner the parent window that owns this panel (may be used for dialog positioning)
     */
    public TemporalColourDashboardPane(Window owner) {
        super(new BorderLayout(6,6));

        // --- Parameter info panel (west) ---
        paramInfo = new JTextArea();
        paramInfo.setEditable(false);
        paramInfo.setBackground(getBackground());
        paramInfo.setFont(paramInfo.getFont().deriveFont(Font.PLAIN, 12f));
        add(paramInfo, BorderLayout.WEST);

        // --- Image panel + Save button (center) ---
        imageContainer = new JPanel();
        imageContainer.setLayout(new BorderLayout());
        imagePanel = new JPanel(new BorderLayout());
        imageContainer.add(imagePanel, BorderLayout.CENTER);

        saveBtn = new JButton("Save Image");
        saveBtn.addActionListener(e -> saveRGBStack());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(saveBtn);
        imageContainer.add(btnPanel, BorderLayout.SOUTH);

        add(imageContainer, BorderLayout.CENTER);

        // --- Intensity plot panel (bottom) ---
        intensityPlotPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (frameIntensity == null || frameColors == null) return;

                int W = getWidth(), H = getHeight();
                int nFrames = frameIntensity.length;

                // Find max intensity for scaling
                double maxVal = Arrays.stream(frameIntensity).max().orElse(1.0);

                for (int i = 0; i < nFrames; i++) {
                    int x0 = i * W / nFrames;
                    int x1 = (i+1) * W / nFrames;
                    int barHeight = (int)((frameIntensity[i]/maxVal) * H);
                    g.setColor(frameColors[i]);
                    g.fillRect(x0, H - barHeight, x1-x0, barHeight);
                }

                // Highlight current frame
                if (rgbStack != null) {
                    int cur = rgbStack.getCurrentSlice() - 1;
                    if (cur >= 0 && cur < nFrames) {
                        g.setColor(Color.RED);
                        int x = cur * W / nFrames;
                        g.drawLine(x, 0, x, H);
                    }
                }

                // Optional: axis labels
                g.setColor(Color.BLACK);
                g.drawString("Intensity", 5, 12);
                g.drawString("Frame", W - 40, H - 5);
            }
        };
        intensityPlotPanel.setPreferredSize(new Dimension(400,100));
        add(intensityPlotPanel, BorderLayout.SOUTH);
    }

    /**
     * Set outputs for display
     * @param rgbStack The temporally color-coded stack
     * @param colorScale Optional color scale
     * @param p Params used for info display
     */
    public void setOutputs(ImagePlus rgbStack, ImagePlus colorScale, Params p) {
        this.rgbStack = rgbStack;

        rgbStack.setTitle("");

        // --- Display RGB image ---
        BufferedImage bi = rgbStack.getBufferedImage();
        imagePanel.removeAll();
        imagePanel.add(new JLabel(new ImageIcon(bi)), BorderLayout.CENTER);

        // --- Display params info ---
        paramInfo.setText(String.format(
                "Start Frame: %d\nEnd Frame: %d\nLUT: %s\nProjection: %s\nColor Scale: %s\nBatch Mode: %s",
                p.referenceFrame, p.referenceFrameEnd, p.lutName, p.projectionMethod,
                p.createColorScale, p.batchMode
        ));

        // --- Build intensity plot data ---
        int nFrames = rgbStack.getStackSize();
        frameIntensity = new double[nFrames];
        frameColors = new Color[nFrames];

        // Prepare LUT colors from colorScale image
        if (colorScale != null) {
            int W = colorScale.getWidth();
            for (int i = 0; i < nFrames; i++) {
                int idx = i * W / nFrames;
                int rgb = colorScale.getProcessor().getPixel(idx, 0);
                frameColors[i] = new Color(rgb);
            }
        } else {
            // fallback to grayscale
            Arrays.fill(frameColors, Color.BLUE);
        }

        // Compute average intensity per frame
        for (int t = 1; t <= nFrames; t++) {
            ImageProcessor ip = rgbStack.getStack().getProcessor(t);
            double sum = 0;
            int w = ip.getWidth(), h = ip.getHeight();
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    sum += ip.getPixel(x, y) & 0xFF; // take brightness
            frameIntensity[t-1] = sum / (w*h);
        }

        // Refresh plot
        intensityPlotPanel.repaint();

        revalidate();
        repaint();
    }

    private void saveRGBStack() {
        if (rgbStack == null) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setSelectedFile(new File("TemporalColorStack.tif"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            IJ.save(rgbStack, path);
            JOptionPane.showMessageDialog(this, "Image saved to:\n" + path);
        }
    }
}
