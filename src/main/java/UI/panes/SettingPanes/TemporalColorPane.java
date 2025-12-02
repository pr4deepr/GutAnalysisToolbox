package UI.panes.SettingPanes;

import Features.Core.Params;
import Analysis.TemporalColorCoder;
import Analysis.TemporalColorCoder.TemporalColorOutput;
import ij.ImagePlus;
import ij.IJ;
import UI.Handlers.Navigator;
import UI.panes.WorkflowDashboards.TemporalColourDashboardPane;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import ij.plugin.LutLoader;

/**
 * Pane for Temporal Color Coding of image stacks.
 * Allows users to select image, frame range, LUT, projection method,
 * color scale and batch mode. Results are displayed in a dashboard tab.
 */
public class TemporalColorPane extends JPanel {

    public static final String Name = "Temporal Color Coder";

    private final Window owner;

    // --- UI components ---
    private JTextField tfStartFrame, tfEndFrame, tfImagePath;
    private JComboBox<String> cbLUT, cbProjection;
    private JCheckBox cbColorScale, cbBatchMode;
    private JButton runBtn, selectImageBtn;
    private JTabbedPane dashboardTabs;

    // Currently selected image
    private ImagePlus selectedImage;

    public TemporalColorPane(Navigator navigator, Window owner) {
        super(new BorderLayout(10,10));
        this.owner = owner;
        dashboardTabs = new JTabbedPane();
        initUI();
    }

    /** Initialize UI layout and components */
    private void initUI() {
        setLayout(new BorderLayout(10,10));

        // --- Info panel at top ---
        JTextArea infoArea = new JTextArea(
            "Temporal Color Coding visualizes temporal dynamics in a stack. " +
            "Bright colors represent later frames; darker colors represent earlier frames. " +
            "Use this to see movement, calcium signals, or other time-lapse dynamics."
        );
        infoArea.setEditable(false);
        infoArea.setBackground(getBackground());
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        infoArea.setFont(infoArea.getFont().deriveFont(Font.PLAIN, 13f));
        infoArea.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));
        add(infoArea, BorderLayout.NORTH);

        // --- Settings panel (top, compact GridBagLayout) ---
        JPanel settingsPanel = new JPanel(new GridBagLayout());
        settingsPanel.setBorder(BorderFactory.createTitledBorder("Settings"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.anchor = GridBagConstraints.LINE_START;

        int row = 0;

        // Image selection
        c.gridx = 0; c.gridy = row; settingsPanel.add(new JLabel("Image:"), c);
        c.gridx = 1;
        tfImagePath = new JTextField(20); tfImagePath.setEditable(false);
        tfImagePath.setToolTipText("Select an image stack");
        settingsPanel.add(tfImagePath, c);
        c.gridx = 2;
        selectImageBtn = new JButton("Browse...");
        selectImageBtn.addActionListener(e -> selectImageFile());
        settingsPanel.add(selectImageBtn, c);
        row++;

        // Frame range
        c.gridx = 0; c.gridy = row; settingsPanel.add(new JLabel("Start Frame:"), c);
        c.gridx = 1; tfStartFrame = new JTextField("1",5); settingsPanel.add(tfStartFrame, c);
        c.gridx = 2; settingsPanel.add(new JLabel("End Frame:"), c);
        c.gridx = 3; tfEndFrame = new JTextField("10",5); settingsPanel.add(tfEndFrame, c);
        row++;

        // LUT and Projection
        c.gridx = 0; c.gridy = row; settingsPanel.add(new JLabel("LUT:"), c);
        c.gridx = 1; 
        // Fetch all available LUT names dynamically
        String[] lutNames = getAvailableLUTNames();
        cbLUT = new JComboBox<>(lutNames);
        cbLUT.setSelectedItem("Fire"); // default selection
        settingsPanel.add(cbLUT, c);
        c.gridx = 2; settingsPanel.add(new JLabel("Projection:"), c);
        c.gridx = 3; cbProjection = new JComboBox<>(new String[]{"Max Intensity","Average Intensity","Min Intensity"}); settingsPanel.add(cbProjection, c);
        row++;

        // Color scale & batch
        c.gridx = 0; c.gridy = row; cbColorScale = new JCheckBox("Show Color Scale", true); settingsPanel.add(cbColorScale, c);
        c.gridx = 1; cbBatchMode = new JCheckBox("Batch Mode"); settingsPanel.add(cbBatchMode, c);

        // Run button
        c.gridx = 2; c.gridy = row; c.gridwidth = 2;
        runBtn = new JButton("Run Temporal Color Coding");
        runBtn.addActionListener(e -> runWorkflow());
        settingsPanel.add(runBtn, c);

        add(settingsPanel, BorderLayout.PAGE_START);

        // --- Dashboard ---
        dashboardTabs = new JTabbedPane();
        add(dashboardTabs, BorderLayout.CENTER);
    }

    /** Opens a file chooser to select image stack */
    private void selectImageFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            tfImagePath.setText(f.getAbsolutePath());
            selectedImage = IJ.openImage(f.getAbsolutePath());
            // Remove: if (selectedImage != null) selectedImage.show();
        }
    }

    
   private static String[] getAvailableLUTNames() {
        try {
            // Get all LUT names from the default LUTs directory
            java.util.List<String> lutNames = new java.util.ArrayList<>();
            String[] builtIn = IJ.getLuts(); // this returns all built-in LUT names
            if (builtIn != null) {
                for (String s : builtIn) lutNames.add(s);
            }
            if (lutNames.isEmpty()) {
                // fallback to minimal list
                return new String[]{"Fire", "Ice", "Green", "Red"};
            }
            return lutNames.toArray(new String[0]);
        } catch (Exception e) {
            IJ.log("Failed to load LUT names dynamically: " + e);
            return new String[]{"Fire", "Ice", "Green", "Red"};
        }
    }

    /** Run the temporal color coding workflow and display results in dashboard */
    private void runWorkflow() {
        if (selectedImage == null) {
            IJ.error("No image selected");
            return;
        }

        try {
            Params p = new Params();
            p.referenceFrame = Integer.parseInt(tfStartFrame.getText());
            p.referenceFrameEnd = Integer.parseInt(tfEndFrame.getText());
            p.lutName = (String) cbLUT.getSelectedItem();
            p.projectionMethod = (String) cbProjection.getSelectedItem();
            p.createColorScale = cbColorScale.isSelected();
            p.batchMode = cbBatchMode.isSelected();

            int nFrames = selectedImage.getStackSize();
            if (p.referenceFrame < 1 || p.referenceFrameEnd > nFrames || p.referenceFrame > p.referenceFrameEnd) {
                IJ.error("Frame range invalid. Stack has " + nFrames + " frames.");
                return;
            }

            // Run the temporal color coding algorithm
            TemporalColorOutput output = TemporalColorCoder.run(selectedImage, p);

            TemporalColourDashboardPane dashboard = new TemporalColourDashboardPane(owner);
            dashboard.setOutputs(output.rgbStack, output.colorScale, p);

            // Color strip panel
            Color[] colours = new Color[output.colorScale.getWidth()];
            for (int x = 0; x < colours.length; x++) {
                int rgb = output.colorScale.getProcessor().getPixel(x,0);
                colours[x] = new Color(rgb);
            }
            JPanel colorStrip = createColorScalePanel(500, 25, colours, selectedImage, p.referenceFrame, p.referenceFrameEnd);
            dashboard.add(colorStrip, BorderLayout.NORTH);

            // Parameter summary panel (bottom)
            JTextArea paramSummary = new JTextArea();
            paramSummary.setEditable(false);
            paramSummary.setFont(paramSummary.getFont().deriveFont(Font.PLAIN, 12f));
            paramSummary.setText(String.format(
                    "Image: %s | Frames: %d-%d | LUT: %s | Projection: %s | Color Scale: %s | Batch: %s",
                    selectedImage.getTitle(), p.referenceFrame, p.referenceFrameEnd, p.lutName,
                    p.projectionMethod, cbColorScale.isSelected(), cbBatchMode.isSelected()
            ));
            paramSummary.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));
            dashboard.add(paramSummary, BorderLayout.SOUTH);

            // Add tab
            dashboardTabs.addTab("", dashboard);
            dashboardTabs.setSelectedComponent(dashboard);

        } catch (Exception ex) {
            IJ.handleException(ex);
        }
    }

    /**
     * Clean color scale panel with frame labels and current slice highlight
     */
    private JPanel createColorScalePanel(int width, int height, Color[] colorScale,
                                        ImagePlus image, int startFrame, int endFrame) {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int n = colorScale.length;

                // Draw color bars
                for (int i = 0; i < n; i++) {
                    int x0 = i * width / n;
                    int x1 = (i+1) * width / n;
                    g.setColor(colorScale[i]);
                    g.fillRect(x0, 0, x1 - x0, height);
                }

                // Highlight current slice
                if (image != null) {
                    int cur = image.getCurrentSlice() - 1;
                    if (cur >= 0 && cur < n) {
                        g.setColor(Color.WHITE);
                        int x = cur * width / n;
                        g.drawLine(x, 0, x, height);
                    }
                }

                // Draw start/end labels
                g.setColor(Color.BLACK);
                g.drawString("Start: " + startFrame, 2, height - 5);
                g.drawString("End: " + endFrame, width - 50, height - 5);
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(width, height);
            }
        };
    }
}
