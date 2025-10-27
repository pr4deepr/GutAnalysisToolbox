// UI/panes/Tools/dialogs/GangliaExpansionDialog.java
package UI.panes.Tools.dialogs;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;

import static UI.panes.Tools.dialogs.RescaleHuDialog.wrapLabelCentered;


/**
 * A modal Swing dialog that collects parameters for the "ganglia expansion" sweep.
 * <p>
 * The tool this belongs to tries different expansion distances (in microns) to
 * grow neuron body ROIs and approximate whole ganglia boundaries. The idea is:
 * take segmented neuron ROIs (from an ROI zip), dilate/expand them by several
 * candidate distances, and evaluate which expansion best matches real ganglion
 * outlines.
 * </p>
 *
 * <p>This dialog lets the user:</p>
 * <ul>
 *   <li>Choose the input image (2D/max projection)</li>
 *   <li>Optionally choose a ROI zip containing per-cell segmentations</li>
 *   <li>Specify a sweep range in microns: min, max, and increment</li>
 *   <li>Choose an output folder for previews/CSVs</li>
 * </ul>
 *
 * <p>
 * When the user clicks OK and all inputs validate, {@link #showAndGet()} returns
 * a {@link Config} with the chosen values. If the user cancels or validation
 * fails, {@code null} is returned.
 * </p>
 *
 * <p>Typical usage:</p>
 *
 * <pre>
 *     GangliaExpansionDialog dlg = new GangliaExpansionDialog(parentWindow);
 *     GangliaExpansionDialog.Config cfg = dlg.showAndGet();
 *     if (cfg != null) {
 *         // run sweep using cfg.imageFile, cfg.huRoiZip, cfg.minUm... etc.
 *     }
 * </pre>
 */
public final class GangliaExpansionDialog extends JDialog {


    /**
     * Immutable-style value object populated when the dialog is accepted.
     * <p>
     * All fields are public for convenience in downstream pipeline code.
     * Callers should treat it as read-only once returned.
     * </p>
     */
    public static final class Config {
        public File imageFile;            // nullable if imageAlreadyOpen


        // Optional ROI zip (not required)
        public File huRoiZip;

        public double minUm  = 10.0;
        public double maxUm  = 15.0;
        public double stepUm = 0.1;

        // NEW: output folder
        public File outDir;
    }

    private Config result;

    private final JTextField imgTf = new JTextField();
    private final JButton    browseImg = new JButton("Browse");

    private final JTextField roiTf = new JTextField();
    private final JButton    browseRoi = new JButton("Browse");

    private final JSpinner minSp  = new JSpinner(new SpinnerNumberModel(12.0, 0.1, 200.0, 0.1));
    private final JSpinner maxSp  = new JSpinner(new SpinnerNumberModel(15.0, 0.1, 500.0, 0.1));
    private final JSpinner stepSp = new JSpinner(new SpinnerNumberModel(0.1, 0.01, 50.0, 0.01));

    // NEW: output
    private final JTextField outTf  = new JTextField();
    private final JButton    browseOut = new JButton("Browse");

    private final JButton ok = new JButton("OK");
    private final JButton cancel = new JButton("Cancel");


    /**
     * Constructs the dialog, lays out all Swing components, and wires up
     * validation/OK/Cancel actions. The dialog is modal.
     *
     * @param owner the parent {@link Window} (may be a Frame or Dialog).
     *              Used for modality and centering.
     */
    public GangliaExpansionDialog(Window owner) {
        super(owner, "Test ganglia segmentation Hu", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);
        setMinimumSize(new Dimension(800, 500));
        setLayout(new BorderLayout(10,10));
        ((JComponent)getContentPane()).setBorder(new EmptyBorder(12,12,12,12));

        // Image row
        imgTf.setColumns(30);
        JPanel imgRow = rowWithBrowse(imgTf, browseImg);

        // ROI zip row
        roiTf.setColumns(30);
        JPanel roiRow = rowWithBrowse(roiTf, browseRoi);

        JPanel sweep = new JPanel(new GridLayout(3,2,8,8));
        sweep.add(new JLabel("Enter minimum value"));   sweep.add(minSp);
        sweep.add(new JLabel("Enter maximum max value")); sweep.add(maxSp);
        sweep.add(new JLabel("Enter increment step/s"));  sweep.add(stepSp);

        // Output row
        outTf.setColumns(28);
        JPanel outRow = labeled("Select output location", rowWithBrowse(outTf, browseOut));

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(wrapLabelCentered("<html>Evaluate a range of values to expand segmented cells to get accurate ganglia outlines.<br>" +
                "<b>You will need an ROI Zip File with segmented cells to run this.</b></html>",520));
        center.add(labeled("Select the maximum projection or 2D image", imgRow));
        center.add(labeled("Select roi ZIP to load", roiRow));
        center.add(Box.createVerticalStrut(10));
        center.add(sweep);
        center.add(Box.createVerticalStrut(10));
        center.add(outRow);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(ok);
        actions.add(cancel);

        add(center, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        browseImg.addActionListener(e -> chooseFile(imgTf, "Choose image", "tif","tiff","czi","lif"));
        browseRoi.addActionListener(e -> chooseFile(roiTf, "Choose ROI zip", "zip"));
        browseOut.addActionListener(e -> chooseDir(outTf));

        cancel.addActionListener(e -> { result = null; dispose(); });
        ok.addActionListener(e -> onOK());

        setPreferredSize(new Dimension(900, 600));
        setMinimumSize(new Dimension(900, 500));
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Convenience helper that wraps a string of HTML in a JLabel with a fixed
     * width. The HTML layout uses a block div to force wrapping instead of
     * letting the label stretch the entire dialog horizontally.
     *
     * @param text     text/HTML to render
     * @param widthPx  max width of the text block, in pixels
     * @return JLabel suitable for use as a left-hand column label, etc.
     */
    private static JLabel wrapLabel(String text, int widthPx) {
        String html = "<html><div style='width:" + widthPx + "px; white-space: normal;'>" + text + "</div></html>";
        return new JLabel(html);
    }

    /**
     * Builds a two-column row where the left side is a (wrapped) label and the
     * right side is any component (often a row with a text field + Browse).
     *
     * @param label text to display on the left
     * @param comp  component to display on the right
     * @return a JPanel with BorderLayout(WEST/EAST style) suitable for vertical stacking
     */
    private static JPanel labeled(String label, JComponent comp){
        JPanel p = new JPanel(new BorderLayout(8,0));
        p.add(wrapLabel(label, 260), BorderLayout.WEST); // was: new JLabel(label)
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    /**
     * Creates a small horizontal row containing:
     * <ul>
     *     <li>a text field (usually a file/folder path)</li>
     *     <li>a "Browse" button to populate it</li>
     * </ul>
     *
     * @param tf     text field for the path
     * @param browse browse button that will trigger a chooser
     * @return panel suitable to drop into {@link #labeled(String, JComponent)}
     */
    private static JPanel rowWithBrowse(JTextField tf, JButton browse){
        JPanel row = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(0,0,0,6);

        // don't let the textfield gobble all the width
        gc.gridx=0; gc.weightx=0; gc.fill=GridBagConstraints.NONE;
        tf.setColumns(28);
        row.add(tf, gc);

        gc.gridx=1; gc.weightx=0; gc.fill=GridBagConstraints.NONE;
        row.add(browse, gc);
        return row;
    }


    /**
     * Shows a file chooser and writes the chosen path into the provided text field.
     * Optionally restricts extensions.
     *
     * @param tf    destination text field
     * @param title dialog title for the chooser
     * @param exts  optional list of allowed extensions (e.g. "tif","tiff")
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
     * Shows a directory chooser (JFileChooser in DIRECTORIES_ONLY mode) and
     * writes the chosen folder path into the provided text field.
     *
     * @param tf destination text field
     */
    private void chooseDir(JTextField tf){
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose output folder");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int ret = fc.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION && fc.getSelectedFile() != null) {
            tf.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }


    /**
     * Validates all user inputs, and if everything looks good:
     * <ul>
     *   <li>Creates a {@link Config}</li>
     *   <li>Stores it in {@link #result}</li>
     *   <li>Disposes the dialog</li>
     * </ul>
     *
     * <p>If validation fails, this method shows a warning dialog and keeps the
     * dialog open for correction.</p>
     */
    private void onOK() {
        Config c = new Config();

        String p = text(imgTf);
        if (p.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please choose an image file or tick 'Image_already_open'.",
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


        String rz = text(roiTf);
        if (!rz.isEmpty()) {
            File fz = new File(rz);
            if (fz.isFile()) c.huRoiZip = fz; // optional
        }

        c.minUm  = ((Number)minSp.getValue()).doubleValue();
        c.maxUm  = ((Number)maxSp.getValue()).doubleValue();
        c.stepUm = ((Number)stepSp.getValue()).doubleValue();
        if (c.minUm <= 0 || c.maxUm <= c.minUm || c.stepUm <= 0) {
            JOptionPane.showMessageDialog(this, "Please enter a valid µm range/step.",
                    "Input required", JOptionPane.WARNING_MESSAGE);
            return;
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
     * Safe getter for a JTextField's trimmed contents.
     *
     * @param tf the text field
     * @return the trimmed text, or "" if null
     */
    private static String text(JTextField tf){ return tf.getText()==null? "" : tf.getText().trim(); }

    /**
     * Shows the dialog (blocking, since it's modal), and returns the completed
     * {@link Config} if the user clicked OK and inputs validated.
     *
     * @return a populated {@link Config}, or {@code null} if the user cancelled
     *         or closed the dialog
     */
    public Config showAndGet() { setVisible(true); return result; }
}
