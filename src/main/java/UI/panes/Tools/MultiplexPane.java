package UI.panes.Tools;

import UI.Handlers.Navigator;
import services.multiplex.config.MultiplexConfig;
import services.multiplex.core.MultiplexRegistrationService;

import javax.swing.event.HyperlinkEvent;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static UI.panes.Tools.HelpAndSupportPane.buildHtml;
import static UI.panes.Tools.HelpAndSupportPane.openInBrowser;

public class MultiplexPane extends JPanel {
    public static final String Name = "Multiplex";

    // ---- Layout tuning ----
    // Width of the left label column (pixels) — labels will wrap at this width
    private static final int LABEL_COL_PX = 280;

    // Controls
    private final JTextField immunoFolderTf = new JTextField();
    private final JButton    browseImmuno   = new JButton("Browse");

    private final JTextField huTf           = new JTextField();
    private final JSpinner   roundsSp       = new JSpinner(new SpinnerNumberModel(2, 1, 99, 1));
    private final JTextField batchTf        = new JTextField();

    private final JCheckBox  chooseSaveCb   = new JCheckBox("Choose_Save_Folder");
    private final JTextField saveFolderTf   = new JTextField();
    private final JButton    browseSave     = new JButton("Browse");


    private final JCheckBox  finetuneCb     = new JCheckBox("Finetune_parameters");

    private final LinkItem link = new LinkItem("Step-by-step tutorial (Documentation)", "https://gut-analysis-toolbox.gitbook.io/docs/");

    private final JButton    runBtn         = new JButton("Run");
    private final JButton    resetBtn       = new JButton("Reset");

    public MultiplexPane(Navigator navigator) {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        // Title
        JLabel title = new JLabel("Multiplex", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        add(title, BorderLayout.NORTH);

        // Main form
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 24));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(10, 8, 10, 8);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.EAST;

        // Wider text fields by default (they'll still grow with layout)
        immunoFolderTf.setColumns(36);
        saveFolderTf.setColumns(36);
        huTf.setColumns(24);
        batchTf.setColumns(24);

        // 1) Select folder with immuno files
        immunoFolderTf.setEditable(false);
        JPanel immunoRow = rowWithBrowse(immunoFolderTf, browseImmuno);
        addLabeled(form, gc, 0, "Select folder with immuno files", immunoRow);

        // 2) Common marker (Hu)
        addLabeled(form, gc, 1, "Enter name of common marker (Hu)", huTf);

        // 3) Rounds
        addLabeled(form, gc, 2, "Enter number of rounds of multiplexing", roundsSp);

        // 4) Batch label (long — will wrap neatly)
        addLabeled(form, gc, 3, "Enter name that distinguishes each batch (Layer/Round)", batchTf);

        // 5) Choose_Save_Folder + conditional save path
        addLabeled(form, gc, 4, "", chooseSaveCb);

        saveFolderTf.setEditable(false);
        JPanel saveRow = rowWithBrowse(saveFolderTf, browseSave);
        saveRow.setVisible(false);
        addLabeled(form, gc, 5, "", saveRow);

        // 6) Finetune
        addLabeled(form, gc, 6, "", finetuneCb);

        //Help functionality
        JEditorPane html = new JEditorPane("text/html", buildHtml());
        html.setEditable(false);
        html.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        html.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                openInBrowser(e.getURL().toString());
            }
        });




        // Actions
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(resetBtn);
        actions.add(runBtn);

        JPanel leftWrap = new JPanel(new BorderLayout());
        leftWrap.add(form, BorderLayout.NORTH);
        leftWrap.add(html, BorderLayout.CENTER);
        leftWrap.add(actions, BorderLayout.SOUTH);

        // IMPORTANT: put in CENTER so the form can expand horizontally
        add(leftWrap, BorderLayout.CENTER);



        // Behavior
        browseImmuno.addActionListener(e -> chooseDirInto(immunoFolderTf));
        browseSave.addActionListener(e -> chooseDirInto(saveFolderTf));

        chooseSaveCb.addActionListener(e -> {
            saveRow.setVisible(chooseSaveCb.isSelected());
            revalidate();
            repaint();
        });

        resetBtn.addActionListener(e -> resetFields());
        runBtn.addActionListener(e -> onRun());
    }

    // ---- Actions ----

    private void onRun() {
        Path immuno = pathFromText(immunoFolderTf.getText());
        Path save   = chooseSaveCb.isSelected() ? pathFromText(saveFolderTf.getText()) : null;
        String hu   = huTf.getText().trim();
        int rounds  = (Integer) roundsSp.getValue();
        String batch= batchTf.getText().trim();
        boolean finetune = finetuneCb.isSelected();

        if (immuno == null) {
            warn("Please select the folder with immuno files.");
            return;
        }
        if (hu.isEmpty()) {
            warn("Please enter the common marker name (e.g., Hu).");
            return;
        }
        if (chooseSaveCb.isSelected() && save == null) {
            warn("Please choose a save folder, or untick 'Choose_Save_Folder'.");
            return;
        }

        runBtn.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        // Java 8 compatible generics
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    MultiplexConfig cfg = new MultiplexConfig.Builder()
                            .imageFolder(immuno.toFile())
                            .commonMarker(hu)
                            .multiplexRounds(rounds)
                            .layerKeyword(batch)
                            .saveFolder(save != null ? save.toFile() : immuno.toFile())
                            .fineTuneParams(finetune)
                            .build();

                    new MultiplexRegistrationService(cfg).run();

                } catch (Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(MultiplexPane.this,
                                    "Error during registration:\n" + ex.getMessage(),
                                    "Error", JOptionPane.ERROR_MESSAGE));
                }
                return null;
            }

            @Override
            protected void done() {
                runBtn.setEnabled(true);
                setCursor(Cursor.getDefaultCursor());
            }
        };
        worker.execute();
    }

    private void resetFields() {
        immunoFolderTf.setText("");
        immunoFolderTf.setToolTipText(null);
        saveFolderTf.setText("");
        saveFolderTf.setToolTipText(null);
        huTf.setText("");
        roundsSp.setValue(2);
        batchTf.setText("");
        chooseSaveCb.setSelected(false);
        finetuneCb.setSelected(false);
    }

    // ---- UI helpers ----

    private void addLabeled(JPanel panel, GridBagConstraints gc, int row, String label, JComponent field) {
        // Label column (fixed width + HTML wrapping)
        gc.gridx = 0; gc.gridy = row; gc.weightx = 0; gc.fill = GridBagConstraints.NONE; gc.anchor = GridBagConstraints.EAST;

        if (!Objects.equals(label, "")) {
            JLabel jl = wrapLabel(label, LABEL_COL_PX);
            panel.add(jl, gc);
        } else {
            panel.add(Box.createHorizontalStrut(1), gc); // spacer for empty label rows
        }

        // Field column (grows)
        gc.gridx = 1; gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.anchor = GridBagConstraints.WEST;
        panel.add(field, gc);
    }

    /** Create a wrapping label using basic HTML and constrain its width. */
    private static JLabel wrapLabel(String text, int widthPx) {
        // Use HTML with a fixed width div to allow natural wrap; keeps LAF fonts/styles
        String html = "<html><div style='width:" + widthPx + "px'>" + text + "</div></html>";
        JLabel jl = new JLabel(html);
        // Align right so it behaves like a classic form label
        jl.setHorizontalAlignment(SwingConstants.RIGHT);
        return jl;
    }

    private static JPanel rowWithBrowse(JTextField tf, JButton browseBtn) {
        JPanel row = new JPanel(new GridBagLayout());
        GridBagConstraints rgc = new GridBagConstraints();
        rgc.insets = new Insets(0,0,0,6);
        rgc.gridx = 0; rgc.weightx = 1; rgc.fill = GridBagConstraints.HORIZONTAL;
        row.add(tf, rgc);
        rgc.gridx = 1; rgc.weightx = 0; rgc.fill = GridBagConstraints.NONE;
        row.add(browseBtn, rgc);
        return row;
    }

    private void chooseDirInto(JTextField tf) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        int ret = chooser.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            tf.setText(path);
            tf.setToolTipText(path); // show full path on hover
            // show the end of the long path
            tf.setCaretPosition(tf.getText().length());
        }
    }

    private static Path pathFromText(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        return new java.io.File(s.trim()).toPath();
    }

    private static final class LinkItem {
        final String label, url;
        /**
         * Constructs a new {@code LinkItem}.
         *
         * @param label human-readable label describing this link
         * @param url   absolute URL associated with this link
         */
        LinkItem(String label, String url){ this.label = label; this.url = url; }
    }

    private static java.util.List<LinkItem> links(){
        List<LinkItem> L = new ArrayList<>();
        L.add(new LinkItem("", "https://gut-analysis-toolbox.gitbook.io/docs/7.-multiplex/multiplex-image-alignment"));
        return L;
    }

    static String buildHtml() {
        StringBuilder ul = new StringBuilder();
        for (LinkItem li : links()) {
            ul.append("<li>")
                    .append(escape(li.label))
                    .append(" <a href='")
                    .append(li.url)
                    .append("'>")
                    .append(li.url)
                    .append("</a></li>");
        }

        return "<html><head><style>"

                + "body{font-family:Segoe UI,Roboto,Arial,sans-serif;color:#FFFFFF;font-size:13px;margin:0;padding:0;}"
                + "h1{font-size:14 px;margin:0 0 8px 0}"
                + ".wrap{padding:4px 8px}"
                + ".section{margin:10px 0 0 0}"
                + "ul{margin:6px 0 0 18px}"
                + "a{text-decoration:none; color: #808080;}"
                + "a:hover{text-decoration:underline}"
                + "</style></head><body>"
                + "<div class='wrap'>"
                + "<div class='section'>Explanation on how this works here:"
                + "<ul>" + ul + "</ul>"
                + "</div></div></body></html>";
    }

    private static String escape(String s){
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }


    private void warn(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Input required", JOptionPane.WARNING_MESSAGE);
    }
}
