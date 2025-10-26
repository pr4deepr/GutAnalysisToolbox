package UI.panes.Tools;

import Features.Core.Params;
import Features.AnalyseWorkflows.NeuronsMultiPipeline;
import Features.Tools.SegOne;
import UI.panes.Tools.dialogs.GangliaExpansionDialog;
import UI.panes.Tools.dialogs.ProbabilityDialog;
import UI.panes.Tools.dialogs.RescaleHuDialog;
import UI.util.GatSettings;
import ij.IJ;
import ij.ImagePlus;

import javax.swing.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public final class TuningTools {

    // ---------- Row (one option in a sweep) ----------
    public static final class Row {
        public final double x;
        public final int    count;
        public final File   preview;
        public Row(double x, int count, File preview){ this.x=x; this.count=count; this.preview=preview; }
        @Override public String toString(){ return String.format(java.util.Locale.US, "%.3f  —  %d", x, count); }
    }

    // ===================== General helpers =====================

    private static File ensureSweepDir(File outDir) {
        File base = outDir;
        if (base == null) {
            base = new File(System.getProperty("user.home"), "Analysis");
        }
        // If caller already passed .../Tuning, use it directly (avoid Tuning/Tuning)
        if ("Tuning".equalsIgnoreCase(base.getName())) {
            if (!base.isDirectory()) base.mkdirs();
            return base;
        }
        File t = new File(base, "Tuning");
        if (!t.isDirectory()) t.mkdirs();
        return t;
    }

    private static double round3(double d){ return Math.round(d*1000.0)/1000.0; }

    private static ImagePlus toMaxProjection(ImagePlus src, Params base) {
        if (src == null) throw new IllegalArgumentException("Image is null");
        if (src.getNSlices() <= 1) return src.duplicate();
        if (base.useClij2EDF) {
            // EDF path using your helper
            return Features.Core.PluginCalls.clij2EdfVariance(src);
        } else {
            src.show();
            IJ.run("Z Project...", "projection=[Max Intensity]");
            ImagePlus out = IJ.getImage();
            out.hide();
            return out;
        }
    }

    private static void saveRowsCsv(List<Row> rows, File csv) {
        try {
            java.io.PrintWriter pw = new java.io.PrintWriter(csv, "UTF-8");
            try {
                pw.println("x,count,preview");
                for (Row r : rows) {
                    String path = (r.preview != null) ? r.preview.getAbsolutePath().replace('\\','/') : "";
                    pw.println(String.format(Locale.US, "%.3f,%d,%s", r.x, r.count, path));
                }
            } finally { pw.close(); }
        } catch (Exception ignore) { }
    }

    // ===================== Picker with Preview (modeless) =====================

    /**
     * Modeless chooser that still blocks the calling (background) thread with a latch.
     * Not always-on-top; preview opens PNG via IJ and you can freely close/move it.
     */
    private static Row pickWithPreview(final String title, final java.util.List<Row> rows) {
        if (rows == null || rows.isEmpty()) return null;

        // If we are (unexpectedly) on EDT, fall back to a simple modal chooser to avoid deadlock
        if (SwingUtilities.isEventDispatchThread()) {
            Object choice = JOptionPane.showInputDialog(
                    null, "Choose the setting that looks best (previews saved in Tuning/).",
                    title, JOptionPane.QUESTION_MESSAGE, null,
                    rows.toArray(new Row[0]), rows.get(Math.max(0, rows.size()/2)));
            return (choice instanceof Row) ? (Row) choice : null;
        }

        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final Row[] result = new Row[1];

        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                final JDialog dlg = new JDialog((java.awt.Frame) null, title, java.awt.Dialog.ModalityType.MODELESS);
                dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                dlg.setLayout(new java.awt.BorderLayout(10, 10));
                dlg.getRootPane().setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
                dlg.setAlwaysOnTop(false); // allow ImageJ windows to get focus

                final DefaultListModel<Row> model = new DefaultListModel<Row>();
                for (Row r : rows) model.addElement(r);

                final JList<Row> list = new JList<Row>(model);
                list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                list.setVisibleRowCount(Math.min(rows.size(), 10));
                list.setSelectedIndex(Math.max(0, rows.size()/2));

                final JScrollPane scroll = new JScrollPane(list);
                final JLabel pathLabel = new JLabel(" ");
                pathLabel.setFont(pathLabel.getFont().deriveFont(11f));

                final JButton previewBtn = new JButton("Preview");
                final JButton previewAllBtn  = new JButton("Preview All");
                final JButton okBtn      = new JButton("OK");
                final JButton cancelBtn  = new JButton("Cancel");

                list.addListSelectionListener(e -> {
                    Row sel = list.getSelectedValue();
                    String p = (sel != null && sel.preview != null) ? sel.preview.getAbsolutePath() : "(no preview)";
                    pathLabel.setText(p);
                });
                list.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                        if (e.getClickCount() == 2) previewBtn.doClick();
                    }
                });

                previewBtn.addActionListener(e -> {
                    Row sel = list.getSelectedValue();
                    if (sel == null || sel.preview == null || !sel.preview.isFile()) {
                        JOptionPane.showMessageDialog(dlg, "No preview image available for this option.");
                        return;
                    }
                    ij.ImagePlus imp = ij.IJ.openImage(sel.preview.getAbsolutePath());
                    if (imp != null) imp.show();
                    else JOptionPane.showMessageDialog(dlg, "Failed to open:\n" + sel.preview.getAbsolutePath());
                });

                previewAllBtn.addActionListener(e -> {
                    showAllPreviews(title + " – All Options", rows);
                });

                okBtn.addActionListener(e -> {
                    result[0] = list.getSelectedValue();
                    dlg.dispose();
                    latch.countDown();
                });
                cancelBtn.addActionListener(e -> {
                    result[0] = null;
                    dlg.dispose();
                    latch.countDown();
                });

                JPanel top = new JPanel(new java.awt.BorderLayout(6,6));
                top.add(new JLabel("Choose the setting that looks best (previews are PNGs in Tuning/)."),
                        java.awt.BorderLayout.NORTH);
                top.add(scroll, java.awt.BorderLayout.CENTER);
                top.add(pathLabel, java.awt.BorderLayout.SOUTH);

                JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
                actions.add(previewBtn);
                actions.add(previewAllBtn);
                actions.add(okBtn);
                actions.add(cancelBtn);

                dlg.add(top, java.awt.BorderLayout.CENTER);
                dlg.add(actions, java.awt.BorderLayout.SOUTH);
                dlg.pack();
                dlg.setSize(Math.max(520, dlg.getWidth()), Math.min(480, dlg.getHeight()+80));
                dlg.setLocationRelativeTo(null);
                dlg.setVisible(true);
            }
        });

        try { latch.await(); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        return result[0];
    }

    // ===================== TEST (.cfg) saving =====================

    private static void put(Properties pr, String key, Object val) {
        if (val == null) return;
        pr.setProperty(key, String.valueOf(val));
    }

    /** Stamp as a "testing" config so ConfigIO will allow it anywhere. */
    private static void markAsTesting(Properties pr) {
        pr.setProperty("workflow", "test");
        if (!pr.containsKey("cfgVersion")) pr.setProperty("cfgVersion", "1");
    }

    /** Save a tiny, Advanced-only config file next to the sweep outputs. */
    private static void saveRunCfg(Properties pr, File sweepDir, String baseNameNoExt) {
        markAsTesting(pr);
        JFileChooser fc = new JFileChooser(sweepDir);
        fc.setSelectedFile(new File(sweepDir, baseNameNoExt + ".cfg"));
        int ret = fc.showSaveDialog(null);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if (!f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".cfg")) {
                f = new File(f.getParentFile(), f.getName() + ".cfg");
            }
            try (java.io.OutputStream os = new java.io.FileOutputStream(f);
                 java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                pr.store(w, "GAT testing config (Advanced-only)");
                JOptionPane.showMessageDialog(null,
                        "Saved config:\n" + f.getAbsolutePath() + "\n\nUse 'Load Config' on any pane.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null,
                        "Failed to save config:\n" + ex.getMessage(),
                        "Save error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ---- Build Advanced-only test props for each sweep ----

    /** Advanced keys for Hu rescale tuning. */
    private static Properties testCfgForRescale(Params base,
                                                RescaleHuDialog.Config cfg,
                                                Row pick) {
        Properties pr = new Properties();

        // Neuron (Hu) advanced bits only
        put(pr, "hu.modelZip", base.stardistModelZip); // advanced in Neuron/Multichannel panes
        put(pr, "hu.prob", cfg.prob);
        put(pr, "hu.nms",  cfg.overlap);

        // Rescale group (advanced everywhere)
        put(pr, "rescale.enabled", base.rescaleToTrainingPx);
        put(pr, "rescale.trainingPxUm", base.trainingPixelSizeUm);
        // The thing we actually tuned:
        put(pr, "rescale.trainingScale", (pick != null ? pick.x : base.trainingRescaleFactor));

        return pr;
    }

    /** Advanced keys for Hu probability tuning. */
    private static Properties testCfgForProbHu(Params base,
                                               ProbabilityDialog.Config cfg,
                                               Row pick) {
        Properties pr = new Properties();

        put(pr, "hu.modelZip", base.stardistModelZip);
        // The thing we tuned:
        put(pr, "hu.prob", (pick != null ? pick.x : cfg.probMin));
        put(pr, "hu.nms",  cfg.overlap);

        // We also record rescale bits used for this sweep (advanced)
        put(pr, "rescale.enabled", base.rescaleToTrainingPx);
        put(pr, "rescale.trainingPxUm", base.trainingPixelSizeUm);
        put(pr, "rescale.trainingScale", cfg.rescaleFactor);

        return pr;
    }

    /** Advanced keys for Subtype probability tuning. */
    private static Properties testCfgForProbSubtype(ProbabilityDialog.Config cfg,
                                                    Row pick) {
        Properties pr = new Properties();
        // Multichannel advanced keys
        if (cfg.modelZip != null) put(pr, "multi.subtypeModelZip", cfg.modelZip.getAbsolutePath());
        put(pr, "multi.subtypeProb", (pick != null ? pick.x : cfg.probMin));
        put(pr, "multi.subtypeNms",  cfg.overlap);
        return pr;
    }

    /** Advanced keys for Ganglia expansion tuning (map to Hu-dilation control). */
    private static Properties testCfgForGanglia(Row pick) {
        Properties pr = new Properties();
        // Neuron advanced: the dilation (µm) control in Ganglia post-processing
        if (pick != null) put(pr, "ganglia.huDilationUm", pick.x);
        return pr;
    }

    // ===================== Sweeps =====================

    // ---- Rescale sweep (Hu) ----
    public static void runRescaleSweep(Params base,
                                       File outDir,
                                       GatSettings settings,
                                       RescaleHuDialog.Config cfg) {
        ij.ImagePlus imp = null;
        boolean closeImp = false;
        try {
            imp = Features.Core.PluginCalls.openWithBioFormats(cfg.imageFile.getAbsolutePath());
            closeImp = true;

            ij.ImagePlus max = toMaxProjection(imp, base);

            // Use dialog’s fixed thresholds for this run
            base.probThresh = cfg.prob;
            base.nmsThresh  = cfg.overlap;
            ij.macro.Interpreter.batchMode = true;
            File sweepDir = ensureSweepDir(outDir);
            List<Row> rows = new ArrayList<Row>();
            for (double f = cfg.rescaleMin; f <= cfg.rescaleMax + 1e-12; f += cfg.rescaleStep) {
                rows.add(SegOne.runHuAtScale(max, cfg.channel, base, round3(f), sweepDir));
            }
            ij.macro.Interpreter.batchMode = false;

            Row pick = pickWithPreview("Pick best Hu rescale", rows);
            if (pick != null) {
                // quick-cache for session
                settings.setHuTrainingRescale(pick.x);
                // Save a tiny Advanced-only testing cfg
                saveRunCfg(testCfgForRescale(base, cfg, pick), sweepDir, "tuning_hu_rescale");
            }

            saveRowsCsv(rows, new File(sweepDir, "hu_rescale_sweep.csv"));

            if (max != null) { max.changes = false; max.close(); }
        } finally {
            if (closeImp && imp != null) { imp.changes = false; imp.close(); }
        }
    }

    // ---- Probability sweep (Hu or Subtype) ----
    public static void runProbSweep(Params base,
                                    File unusedOutDir,
                                    GatSettings s,
                                    ProbabilityDialog.Config cfg) {
        ij.ImagePlus imp = Features.Core.PluginCalls.openWithBioFormats(cfg.imageFile.getAbsolutePath());
        if (imp == null) throw new IllegalStateException("No image available.");
        boolean closeImp = true;

        try {
            base.rescaleToTrainingPx   = true;
            base.trainingRescaleFactor = cfg.rescaleFactor;
            base.nmsThresh             = cfg.overlap;

            ij.ImagePlus max = toMaxProjection(imp, base);
            List<Row> rows = new ArrayList<Row>();
            File tdir = ensureSweepDir(cfg.outDir);

            ij.macro.Interpreter.batchMode = true;
            for (double p = cfg.probMin; p <= cfg.probMax + 1e-12; p += cfg.probStep) {
                double pp = round3(p);
                if (cfg.mode == ProbabilityDialog.Mode.NEURON) {
                    rows.add(SegOne.runHuAtProb(max, cfg.channel, base, cfg.rescaleFactor, pp, tdir));
                } else {
                    NeuronsMultiPipeline.MultiParams mp = new NeuronsMultiPipeline.MultiParams();
                    mp.base = base;
                    mp.multiProb = pp;
                    mp.multiNms  = cfg.overlap;
                    mp.subtypeModelZip = cfg.modelZip.getAbsolutePath();
                    rows.add(SegOne.runSubtypeAtProb(max, cfg.channel, mp, pp, tdir));
                }
            }
            ij.macro.Interpreter.batchMode = false;

            Row pick = pickWithPreview("Pick best probability", rows);
            if (pick != null) {
                Properties pr;
                if (cfg.mode == ProbabilityDialog.Mode.NEURON) {
                    s.setHuProb(pick.x);
                    s.setOverlapFrac(cfg.overlap);
                    pr = testCfgForProbHu(base, cfg, pick);
                } else {
                    s.setSubtypeProb(pick.x);
                    s.setOverlapFrac(cfg.overlap);
                    pr = testCfgForProbSubtype(cfg, pick);
                }
                saveRunCfg(pr, tdir, "tuning_probability");
            }

            saveRowsCsv(rows, new File(tdir, "prob_sweep.csv"));
        } finally {
            if (closeImp) { imp.changes = false; imp.close(); }
        }
    }

    // ---- Ganglia expansion (µm) ----
    public static void runGangliaExpansionSweep(Params base,
                                                File unusedOutDir,
                                                GatSettings s,
                                                GangliaExpansionDialog.Config cfg) {
        ij.ImagePlus imp = Features.Core.PluginCalls.openWithBioFormats(cfg.imageFile.getAbsolutePath());
        if (imp == null) throw new IllegalStateException("No image available.");
        boolean closeImp = true;

        try {
            ij.ImagePlus max = toMaxProjection(imp, base);
            List<Row> rows = new ArrayList<Row>();
            File tdir = ensureSweepDir(cfg.outDir);

            ij.macro.Interpreter.batchMode = true;
            for (double um = cfg.minUm; um <= cfg.maxUm + 1e-12; um += cfg.stepUm) {
                double uu = round3(um);
                rows.add(SegOne.runGangliaFromHuExpansion(max, base.huChannel, base, uu, tdir));
            }
            ij.macro.Interpreter.batchMode = false;

            Row pick = pickWithPreview("Pick ganglia expansion (µm)", rows);
            if (pick != null) {
                s.setGangliaExpandUm(pick.x);
                saveRunCfg(testCfgForGanglia(pick), tdir, "tuning_ganglia");
            }

            saveRowsCsv(rows, new File(tdir, "ganglia_expand_sweep.csv"));
        } finally {
            if (closeImp) { imp.changes = false; imp.close(); }
        }
    }

    // ===================== Gallery (preview all) =====================
    private static void showAllPreviews(final String title, final java.util.List<Row> rows) {
        if (rows == null || rows.isEmpty()) return;

        SwingUtilities.invokeLater(() -> {
            final JDialog dlg = new JDialog((java.awt.Frame) null, title, java.awt.Dialog.ModalityType.MODELESS);
            dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            dlg.setLayout(new java.awt.BorderLayout(8,8));

            final JPanel listPanel = new JPanel();
            listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
            listPanel.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));

            final JScrollPane scroll = new JScrollPane(
                    listPanel,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            );
            scroll.getVerticalScrollBar().setUnitIncrement(24);
            dlg.add(scroll, java.awt.BorderLayout.CENTER);

            dlg.setSize(1000, 720);
            dlg.setLocationRelativeTo(null);
            dlg.setVisible(true);

            // Stream cards in without blocking the EDT
            new SwingWorker<Void, JPanel>() {
                @Override protected Void doInBackground() {
                    for (Row r : rows) {
                        publish(buildPreviewCard(r));
                    }
                    return null;
                }
                @Override protected void process(java.util.List<JPanel> chunks) {
                    for (JPanel card : chunks) {
                        listPanel.add(card);
                        listPanel.add(Box.createVerticalStrut(8));
                    }
                    listPanel.revalidate();
                    listPanel.repaint();
                }
            }.execute();
        });
    }

    private static JPanel buildPreviewCard(Row r) {
        JPanel card = new JPanel(new java.awt.BorderLayout(6,6));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(220,220,220)),
                BorderFactory.createEmptyBorder(8,8,8,8)
        ));

        // Header: x/count
        String hdr = String.format(java.util.Locale.US, "x = %.3f    count = %d", r.x, r.count);
        JLabel header = new JLabel(hdr);
        header.setFont(header.getFont().deriveFont(header.getFont().getSize2D()+1.0f));
        card.add(header, java.awt.BorderLayout.NORTH);

        // Image placeholder
        JLabel imageLabel = new JLabel("Loading preview…", SwingConstants.CENTER);
        imageLabel.setVerticalAlignment(SwingConstants.TOP);
        imageLabel.setOpaque(true);
        imageLabel.setBackground(java.awt.Color.WHITE);
        imageLabel.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        card.add(imageLabel, java.awt.BorderLayout.CENTER);

        // Footer: path + open button
        JPanel footer = new JPanel(new java.awt.BorderLayout(6,6));
        String path = (r.preview != null) ? r.preview.getAbsolutePath() : "(no preview file)";
        JLabel pathLabel = new JLabel(path);
        pathLabel.setFont(pathLabel.getFont().deriveFont(11f));
        footer.add(pathLabel, java.awt.BorderLayout.CENTER);

        JButton openBtn = new JButton("Open in IJ");
        openBtn.addActionListener(e -> {
            if (r.preview != null && r.preview.isFile()) {
                ij.ImagePlus imp = ij.IJ.openImage(r.preview.getAbsolutePath());
                if (imp != null) imp.show();
                else JOptionPane.showMessageDialog(card, "Failed to open:\n" + r.preview.getAbsolutePath());
            } else {
                JOptionPane.showMessageDialog(card, "No preview image available for this option.");
            }
        });
        footer.add(openBtn, java.awt.BorderLayout.EAST);
        card.add(footer, java.awt.BorderLayout.SOUTH);

        // Async load + scale thumbnail to fit nicely
        if (r.preview != null && r.preview.isFile()) {
            new SwingWorker<ImageIcon,Void>() {
                @Override protected ImageIcon doInBackground() {
                    try {
                        java.awt.image.BufferedImage full = javax.imageio.ImageIO.read(r.preview);
                        if (full == null) return null;

                        // Scale to a sane card size while keeping aspect ratio
                        int maxW = 940;   // a bit less than dialog width to leave margins
                        int maxH = 600;   // tall enough, users can scroll
                        int w = full.getWidth(), h = full.getHeight();
                        double scale = Math.min(1.0, Math.min(maxW/(double)w, maxH/(double)h));
                        int nw = Math.max(1, (int)Math.round(w*scale));
                        int nh = Math.max(1, (int)Math.round(h*scale));

                        java.awt.image.BufferedImage scaled =
                                new java.awt.image.BufferedImage(nw, nh, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        java.awt.Graphics2D g2 = scaled.createGraphics();
                        try {
                            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,     java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,  java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                            g2.drawImage(full, 0, 0, nw, nh, null);
                        } finally {
                            g2.dispose();
                        }
                        return new ImageIcon(scaled);
                    } catch (Exception ex) {
                        return null;
                    }
                }
                @Override protected void done() {
                    try {
                        ImageIcon icon = get();
                        if (icon != null) {
                            imageLabel.setText(null);
                            imageLabel.setIcon(icon);
                        } else {
                            imageLabel.setText("(failed to load preview)");
                        }
                    } catch (Exception ex) {
                        imageLabel.setText("(failed to load preview)");
                    }
                }
            }.execute();
        } else {
            imageLabel.setText("(no preview available)");
        }

        return card;
    }


    private TuningTools(){}
}
