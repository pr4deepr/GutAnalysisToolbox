package UI.panes.SettingPanes;

import Features.Core.Params;
import UI.Handlers.Navigator;
import UI.util.InputValidation;
import UI.panes.WorkflowDashboards.CalciumImagingAnalysisDashboard;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * Pane for Calcium Imaging Analysis settings.
 * Allows configuration of input stack, normalization, segmentation, cell types, ROIs, and cell names.
 * Provides a stepwise analysis dashboard for execution.
 */
public class calciumImagingAnalysisPane extends JPanel {
    public static final String Name = "Calcium Imaging Analysis";

    private final Window owner;

    // --- UI components ---
    private JTextField imagePathField;
    private JCheckBox useFF0Box;
    private JCheckBox useStarDistBox;
    private JSpinner cellTypesSpinner;
    private JTextField roiPathField;
    private JTextField cellNamesField;
    private JButton browseButton;
    private JButton runButton;

    private JTabbedPane tabs;
    private CalciumImagingAnalysisDashboard calciumDashboard;

    public calciumImagingAnalysisPane(Navigator navigator, Window owner) {
        super(new BorderLayout(10, 10));
        this.owner = owner;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // --- Tabs setup ---
        tabs = new JTabbedPane();

        // Settings panel
        JPanel settingsPanel = buildSettingsPanel();
        JScrollPane settingsScroll = new JScrollPane(settingsPanel);
        settingsScroll.getVerticalScrollBar().setUnitIncrement(16);
        tabs.addTab("Settings", settingsScroll);

        add(tabs, BorderLayout.CENTER);

        // --- Run button ---
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        runButton = new JButton("Run Analysis Stepwise");
        runButton.addActionListener(e -> onRun());
        actions.add(runButton);
        add(actions, BorderLayout.SOUTH);
    }

    /** Builds the main settings panel with all options */
    private JPanel buildSettingsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Title
        JLabel title = new JLabel("Calcium Imaging Analysis Settings");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(12));

        // Input stack
        imagePathField = new JTextField(30);
        browseButton = new JButton("Browse…");
        browseButton.addActionListener(e -> chooseImageFile());
        panel.add(box("Input Stack", row(new JLabel("Aligned Image Stack:"), imagePathField, browseButton)));

        // F/F0 normalization
        useFF0Box = new JCheckBox("Use F/F₀ Normalisation", true);
        panel.add(box("Normalization", useFF0Box));

        // StarDist segmentation
        useStarDistBox = new JCheckBox("Use StarDist Segmentation", false);
        panel.add(box("Segmentation", useStarDistBox));

        // Number of cell types
        cellTypesSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
        panel.add(box("Cell Type Settings", row(new JLabel("Number of Cell Types:"), cellTypesSpinner)));

        // Cell names input
        cellNamesField = new JTextField(30);
        panel.add(box("Cell Names", row(new JLabel("Cell Names (comma-separated):"), cellNamesField)));

        // ROI file selection
        roiPathField = new JTextField(30);
        JButton roiBrowse = new JButton("Browse…");
        roiBrowse.addActionListener(e -> chooseROIFile());
        panel.add(box("ROI Input", row(new JLabel("ROI Manager File (optional):"), roiPathField, roiBrowse)));

        return panel;
    }

    /** Open file chooser for image stack */
    private void chooseImageFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select aligned calcium imaging stack");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            imagePathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    /** Open file chooser for optional ROI manager ZIP */
    private void chooseROIFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select ROI Manager ZIP file");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            roiPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    /** Trigger stepwise analysis dashboard */
    private void onRun() {
        runButton.setEnabled(false);

        // Validate input image
        if (!InputValidation.validateImageOrShow(this, imagePathField.getText())) {
            runButton.setEnabled(true);
            return;
        }

        Params p = getParams();

        // --- Initialize and add dashboard tab ---
        calciumDashboard = new CalciumImagingAnalysisDashboard(p);
        tabs.addTab("Analysis Dashboard", calciumDashboard);
        tabs.setSelectedComponent(calciumDashboard);

        // Dashboard handles stepwise execution
    }

    /** Construct Params object from UI fields */
    public Params getParams() {
        Params p = new Params();
        p.imagePath = imagePathField.getText();
        p.useFF0 = useFF0Box.isSelected();
        p.useStarDist = useStarDistBox.isSelected();
        p.cellTypes = (int) cellTypesSpinner.getValue();
        p.roiPath = roiPathField.getText().isEmpty() ? null : roiPathField.getText();
        p.uiAnchor = SwingUtilities.getWindowAncestor(this);

        // Parse comma-separated cell names
        String namesText = cellNamesField.getText().trim();
        if (!namesText.isEmpty()) {
            String[] names = namesText.split("\\s*,\\s*");
            p.cellNames = new ArrayList<>();
            for (String name : names) p.cellNames.add(name);
        }
        return p;
    }

    // --- UI Helpers ---
    private static JPanel box(String title, JComponent inner) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(inner, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel row(JComponent... comps) {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        for (JComponent c : comps) r.add(c);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        return r;
    }
}
