// UI/panes/Tools/dialogs/ProbabilityDialog.java
package UI.panes.Tools.dialogs;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;

import static UI.panes.Tools.dialogs.RescaleHuDialog.wrapLabelCentered;

/**
 * Modal dialog for configuring a StarDist probability threshold sweep.
 * <p>
 * This dialog is aimed at helping the user choose a good probability cutoff
 * for neuron segmentation. It can operate in two modes:
 * </p>
 *
 * <ul>
 *   <li><b>NEURON</b>: Segment Hu-positive neurons directly from the Hu
 *       channel.</li>
 *   <li><b>SUBTYPE</b>: Segment a specific neuron subtype from another
 *       marker channel using a provided StarDist model ZIP.</li>
 * </ul>
 *
 * <p>The user can:</p>
 * <ul>
 *   <li>Pick the image to segment</li>
 *   <li>Select mode (NEURON vs SUBTYPE) and the 1-based image channel</li>
 *   <li>Define a sweep of probability thresholds (min/max/step)</li>
 *   <li>Specify fixed parameters used during the sweep, such as the
 *       rescaling factor and the overlap threshold</li>
 *   <li>(If SUBTYPE mode) choose the StarDist model ZIP</li>
 *   <li>Select an output directory for generated previews/CSVs</li>
 * </ul>
 *
 * <p>Pressing OK validates all inputs and produces a
 * {@link ProbabilityDialog.Config} instance. If validation fails, the
 * dialog warns the user and stays open. Call {@link #showAndGet()} to
 * show the dialog and retrieve the final config (or {@code null} if
 * the user cancelled).</p>
 */

public final class ProbabilityDialog extends JDialog {

    public enum Mode { NEURON, SUBTYPE }

    public static final class Config {
        public File   imageFile;           // nullable if imageAlreadyOpen


        public Mode   mode = Mode.NEURON;

        // Channel to segment (1-based). For NEURON this is Hu; for SUBTYPE it's the marker channel.
        public int    channel = 3;

        // Sweep
        public double probMin  = 0.40;
        public double probMax  = 0.90;
        public double probStep = 0.05;

        // Fixed thresholds used during the sweep
        public double rescaleFactor = 1.0;
        public double overlap       = 0.30;

        // Optional model path (required when mode == SUBTYPE)
        public File   modelZip;

        // where previews/CSV are saved
        public File   outDir;
    }

    private Config result;

    // Controls
    private final JTextField pathTf    = new JTextField();
    private final JButton    browseImg = new JButton("Browse");


    private final JRadioButton rbNeuron  = new JRadioButton("Neuron Segmentation", true);
    private final JRadioButton rbSubtype = new JRadioButton("Neuron subtype segmentation", false);
    private final JSpinner     chSp      = new JSpinner(new SpinnerNumberModel(3, 1, 99, 1));

    private final JSpinner minSp  = new JSpinner(new SpinnerNumberModel(0.40, 0.01, 0.99, 0.01));
    private final JSpinner maxSp  = new JSpinner(new SpinnerNumberModel(0.90, 0.01, 0.99, 0.01));
    private final JSpinner stepSp = new JSpinner(new SpinnerNumberModel(0.05, 0.01, 0.50, 0.01));

    private final JSpinner rescaleSp = new JSpinner(new SpinnerNumberModel(1.0, 0.05, 5.0, 0.01));
    private final JSpinner overlapSp = new JSpinner(new SpinnerNumberModel(0.30, 0.00, 1.00, 0.01));

    private final JTextField modelTf   = new JTextField();
    private final JButton    browseZip = new JButton("Browse");

    // NEW: output folder
    private final JTextField outTf     = new JTextField();
    private final JButton    browseOut = new JButton("Browse");

    private final JButton ok = new JButton("OK");
    private final JButton cancel = new JButton("Cancel");


    /**
     * Builds and initializes the ProbabilityDialog UI.
     * <p>
     * This dialog lets the user sweep over a range of StarDist probability thresholds
     * (and optionally subtype model settings) to find a good segmentation cutoff.
     * The dialog is modal, lays out all controls, wires up listeners, and prepares
     * validation.
     * </p>
     *
     * @param owner parent window for modality/centering; may be a Frame or Dialog
     */
    public ProbabilityDialog(Window owner) {
        super(owner, "Test neuron probability", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);
        setMinimumSize(new Dimension(800, 560)); // nicer size
        setLayout(new BorderLayout(10,10));
        ((JComponent)getContentPane()).setBorder(new EmptyBorder(12,12,12,12));

        //  Image row
        pathTf.setColumns(30);
        JPanel imgRow = rowWithBrowse(pathTf, browseImg);

        // Mode + channel row
        ButtonGroup bg = new ButtonGroup(); bg.add(rbNeuron); bg.add(rbSubtype);
        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        modeRow.add(new JLabel("Choose mode of segmentation"));
        modeRow.add(rbNeuron);
        modeRow.add(rbSubtype);
        modeRow.add(Box.createHorizontalStrut(16));


        // Sweep inputs
        JPanel sweep = new JPanel(new GridLayout(4,2,8,8));
        sweep.add(new JLabel("Channel:"));  sweep.add(chSp);
        sweep.add(new JLabel("Enter minimum value"));             sweep.add(minSp);
        sweep.add(new JLabel("Enter maximum max value"));         sweep.add(maxSp);
        sweep.add(new JLabel("Enter size of each increment step")); sweep.add(stepSp);

        // Fixed thresholds row
        JPanel fixed = new JPanel(new GridLayout(2,2,8,8));
        fixed.add(new JLabel("Rescaling Factor"));  fixed.add(rescaleSp);
        fixed.add(new JLabel("Overlap Threshold")); fixed.add(overlapSp);

        //  Model row (for SUBTYPE)
        modelTf.setColumns(28);
        JPanel modelRow = rowWithBrowse(modelTf, browseZip);
        JPanel modelWrap = labeled("Choose the StarDist model based on celltype.", modelRow);

        // Output row
        outTf.setColumns(28);
        JPanel outRow = labeled("Select output location", rowWithBrowse(outTf, browseOut));

        // Buttons
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(ok);
        actions.add(cancel);

        // Compose
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(labeled("Choose the image to segment", imgRow));
        center.add(Box.createVerticalStrut(6));
        center.add(modeRow);
        center.add(Box.createVerticalStrut(8));
        center.add(wrapLabelCentered(
                "Test a range of probability thresholds to get the value with the most accurate cell segmentation. " +
                        "Default is 0.4. Default rescaling_factor is 1.0 and Overlap threshold is 0.3. " +
                        "Leave it as default when first trying this. More info: https://www.imagej.net/StarDist/" +
                        ". Large images may freeze or hang.",
                520
        ));
        center.add(Box.createVerticalStrut(6));
        center.add(sweep);
        center.add(Box.createVerticalStrut(6));
        center.add(fixed);
        center.add(Box.createVerticalStrut(8));
        center.add(modelWrap);   // visibility toggled by mode
        center.add(Box.createVerticalStrut(6));
        center.add(outRow);      // always visible

        add(center, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        // Behavior
        updateModelVisibility();
        rbNeuron.addActionListener(e -> updateModelVisibility());
        rbSubtype.addActionListener(e -> updateModelVisibility());

        browseImg.addActionListener(e -> chooseFile(pathTf, "Choose image", "tif","tiff","czi","lif"));
        browseZip.addActionListener(e -> chooseFile(modelTf, "Choose StarDist model (.zip)", "zip"));
        browseOut.addActionListener(e -> chooseDir(outTf));


        cancel.addActionListener(e -> { result = null; dispose(); });
        ok.addActionListener(e -> onOK());

        pack();
        setSize(Math.min(820, getWidth()), getHeight());
        setLocationRelativeTo(owner);
    }

    /**
     * Enables or disables the "model ZIP" controls depending on the chosen mode.
     * <p>
     * In NEURON mode the subtype model is not required, so the model file
     * textfield and Browse button are disabled. In SUBTYPE mode those
     * controls are enabled because a model ZIP is mandatory.
     * </p>
     */

    private void updateModelVisibility() {
        boolean subtype = rbSubtype.isSelected();
        modelTf.setEnabled(subtype);
        browseZip.setEnabled(subtype);
    }

    /**
     * Wraps the given text in HTML and constrains it to a fixed width so it wraps
     * instead of stretching the layout horizontally.
     *
     * @param text    plain text or HTML snippet to display
     * @param widthPx maximum width, in pixels, for wrapping
     * @return a JLabel containing the wrapped HTML block
     */
    private  JLabel wrapLabel(String text, int widthPx) {
        String html = "<html><div style='width:" + widthPx + "px; white-space: normal;'>" + text + "</div></html>";
        return new JLabel(html);
    }

    /**
     * Creates a horizontal row containing a text field (usually a path) and a Browse button.
     * <p>
     * The row uses GridBagLayout so the text field can expand horizontally while
     * keeping the Browse button at its preferred size.
     * </p>
     *
     * @param tf     the text field that will hold the selected file/folder path
     * @param browse the button that triggers a chooser
     * @return a JPanel suitable for embedding in a labeled() row
     */
    private JPanel rowWithBrowse(JTextField tf, JButton browse){
        JPanel row = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4,4,4,4);
        gc.gridx=0; gc.weightx=1; gc.fill=GridBagConstraints.HORIZONTAL; row.add(tf, gc);
        gc.gridx=1; gc.weightx=0; gc.fill=GridBagConstraints.NONE;       row.add(browse, gc);
        return row;
    }

    /**
     * Creates a two-column row with a descriptive label on the left (wrapped so it
     * doesn't blow out the dialog width) and an arbitrary component (such as a
     * rowWithBrowse(...) panel) on the right.
     *
     * @param label text to display in the left column
     * @param comp  component to place in the right column
     * @return a JPanel with BorderLayout.WEST for the label and CENTER for the component
     */
    private JPanel labeled(String label, JComponent comp){
        JPanel p = new JPanel(new BorderLayout(8,0));
        // cap label width so it wraps instead of stretching the dialog
        p.add(wrapLabel(label, 260), BorderLayout.WEST);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    /**
     * Opens a file chooser and writes the chosen file path into the provided text field.
     * <p>
     * If extensions are provided, a FileNameExtensionFilter is applied so the user
     * only sees allowed file types (e.g. TIFF, ZIP).
     * </p>
     *
     * @param tf    destination text field that will receive the chosen path
     * @param title title to display in the chooser dialog
     * @param exts  optional list of allowed file extensions (without dots), e.g. "tif", "zip"
     */
    private void chooseFile(JTextField tf, String title, String... exts){
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(title);
        if (exts != null && exts.length>0) fc.setFileFilter(new FileNameExtensionFilter(title, exts));
        int ret = fc.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION && fc.getSelectedFile() != null) {
            tf.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    /**
     * Opens a directory chooser (JFileChooser in DIRECTORIES_ONLY mode) and writes the
     * chosen folder path into the provided text field.
     *
     * @param tf destination text field that will receive the chosen directory path
     */
    private void chooseDir(JTextField tf){
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose output folder");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setMultiSelectionEnabled(false);
        int ret = fc.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION && fc.getSelectedFile() != null) {
            tf.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    /**
     * Validates all user inputs and, if everything is acceptable:
     * <ul>
     *   <li>Builds a {@link Config} object reflecting the user's selections</li>
     *   <li>Saves it to {@code result}</li>
     *   <li>Disposes the dialog</li>
     * </ul>
     *
     * <p>If validation fails (missing image, invalid range, missing model in
     * SUBTYPE mode, missing/invalid output directory, etc.), a warning dialog
     * is shown and the dialog remains open for correction.</p>
     */
    private void onOK() {
        Config c = new Config();

        String p = text(pathTf);
        if (p.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please choose an image file.",
                    "Input required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File f = new File(p);
        if (!f.isFile()) {
            JOptionPane.showMessageDialog(this, "Image file does not exist:\n" + p,
                    "Input required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        c.imageFile = f;


        c.mode    = rbSubtype.isSelected() ? Mode.SUBTYPE : Mode.NEURON;
        c.channel = ((Number) chSp.getValue()).intValue();

        c.probMin  = ((Number)minSp.getValue()).doubleValue();
        c.probMax  = ((Number)maxSp.getValue()).doubleValue();
        c.probStep = ((Number)stepSp.getValue()).doubleValue();

        if (c.probMin <= 0 || c.probMax <= c.probMin || c.probStep <= 0) {
            JOptionPane.showMessageDialog(this, "Please enter a valid probability range/step.",
                    "Input required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        c.rescaleFactor = ((Number)rescaleSp.getValue()).doubleValue();
        c.overlap       = ((Number)overlapSp.getValue()).doubleValue();

        if (c.mode == Mode.SUBTYPE) {
            String m = text(modelTf);
            if (m.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please choose a StarDist model for subtype segmentation.",
                        "Input required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            File fm = new File(m);
            if (!fm.isFile()) {
                JOptionPane.showMessageDialog(this, "Model ZIP not found:\n" + m,
                        "Input required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            c.modelZip = fm;
        }

        String out = text(outTf);
        if (out.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select an output folder.",
                    "Input required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File of = new File(out);
        if (!of.isDirectory() && !of.mkdirs()) {
            JOptionPane.showMessageDialog(this, "Cannot create output folder:\n" + out,
                    "Input required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        c.outDir = of;

        result = c;
        dispose();
    }

    /**
     * Returns the trimmed contents of a text field.
     *
     * @param tf text field to read
     * @return the field's text with leading/trailing whitespace removed,
     *         or the empty string if the field text is null
     */
    private static String text(JTextField tf){ return tf.getText()==null? "" : tf.getText().trim(); }

    /**
     * Shows this dialog modally and returns the collected configuration if the
     * user pressed OK and validation passed.
     *
     * @return a populated {@link Config}, or {@code null} if the user cancelled
     *         or closed the dialog
     */
    public Config showAndGet() { setVisible(true); return result; }


}
