package UI.panes.Results;

import Features.AnalyseWorkflows.NeuronsMultiPipeline.MultiResult;
import ij.IJ;
import ij.ImagePlus;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Result viewer for the Hu-gated Multiplex workflow.
 *
 * Shows:
 *  • Header with output folder, Hu total, and optional ganglia count
 *  • Neurons and ganglia overlay thumbnails
 *  • Ganglia table and Hu "neurons per ganglion" box plot (if available)
 *  • Summary table of Hu-gated totals (markers and combinations)
 *  • Tabs with "Markers" and "Combinations" per-ganglion box plots
 *  • Optional spatial histograms (combined + per-dataset) if CSVs are present
 */
public class ResultsMultiUI {

    /**
     * End-of-workflow prompt: preview, open folder, or end.
     * @param r Completed multiplexer result bundle.
     */
    public static void promptAndMaybeShow(MultiResult r) {
        String msg = "Results saved to:\n" + r.outDir.getAbsolutePath();
        String[] options = {"Preview results…", "Open folder", "End"};
        int choice = JOptionPane.showOptionDialog(
                null, msg, "Workflow finished",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, options, options[0]
        );
        if (choice == 1) {
            openFolder(r.outDir);
        } else if (choice == 0) {
            showResultsFrame(r);
        }
    }

    /** Try to open {@code dir} in the native file manager; log/show path on failure. */
    private static void openFolder(File dir) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir);
            else IJ.showMessage("Folder: " + dir.getAbsolutePath());
        } catch (IOException e) {
            IJ.handleException(e);
        }
    }

    /** Build the Hu ganglia table: ganglion_id, neuron_count, area_um2 (skips empty rows). */
    private static JTable makeGangliaTable(MultiResult r) {
        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"ganglion_id","neuron_count","area_um2"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        int n = Math.max(
                r.neuronsPerGanglion != null ? r.neuronsPerGanglion.length : 0,
                r.gangliaAreaUm2     != null ? r.gangliaAreaUm2.length     : 0
        );
        for (int gid = 1; gid < n; gid++) {
            int count = (r.neuronsPerGanglion != null && gid < r.neuronsPerGanglion.length)
                    ? r.neuronsPerGanglion[gid] : 0;
            double area = (r.gangliaAreaUm2 != null && gid < r.gangliaAreaUm2.length)
                    ? r.gangliaAreaUm2[gid] : 0.0;
            if (count == 0 && area == 0.0) continue;
            model.addRow(new Object[]{gid, count, String.format(java.util.Locale.US, "%.2f", area)});
        }
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setFont(table.getFont().deriveFont(12f));
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, 12f));
        TableColumnModel cm = table.getColumnModel();
        if (cm.getColumnCount() >= 3) {
            cm.getColumn(0).setPreferredWidth(90);
            cm.getColumn(1).setPreferredWidth(120);
            cm.getColumn(2).setPreferredWidth(140);
        }
        return table;
    }


    /**
     * Build and display the main results window for the Multiplex run.
     * See class-level Javadoc for included sections.
     *
     * @param r Aggregated outputs and measurements from the pipeline.
     */
    private static void showResultsFrame(MultiResult r) {
        JFrame f = new JFrame("Results – " + r.baseName + " (multi)");
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        f.setLayout(new BorderLayout());

        // ---- header ----
        JPanel header = new JPanel(new GridLayout(0,1));
        header.setBorder(new EmptyBorder(6,12,2,12));
        header.add(new JLabel("Output folder: " + r.outDir.getAbsolutePath()));
        header.add(new JLabel("Total neurons (Hu): " + r.totalHu));
        if (r.nGanglia != null) header.add(new JLabel("Detected ganglia: " + r.nGanglia));
        f.add(header, BorderLayout.NORTH);

        // ---- center ----
        JPanel center = new JPanel();
        center.setBorder(new EmptyBorder(4,12,4,12));
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        // Overlay thumbnails (reuse the ones from Hu workflow if present)
        File neuronOverlay = new File(r.outDir, "RGB_" + r.baseName + "_neurons_overlay.tif");
        File gangliaOverlay = new File(r.outDir, "RGB_" + r.baseName + "_ganglia_overlay.tif");

        center.add(sectionTitle("Images (overlays)"));
        JPanel thumbsRow = new JPanel(new GridLayout(0, 1, 0, 6));
        thumbsRow.add(makeThumbCard("Neurons overlay",
                loadForThumb(neuronOverlay, r.max), gangliaOverlay.exists() ? 400 : 600));
        if (gangliaOverlay.exists()) {
            thumbsRow.add(makeThumbCard("Ganglia overlay",
                    loadForThumb(gangliaOverlay, r.max), 400));
        }
        center.add(thumbsRow);

        // --- Hu ganglia table + Hu box plot (same as single-channel UI) ---
        if (r.neuronsPerGanglion != null && r.neuronsPerGanglion.length > 1) {
            center.add(Box.createVerticalStrut(10));
            center.add(sectionTitle("Ganglia results (table)"));

            JTable table = makeGangliaTable(r);
            JScrollPane tableScroll = new JScrollPane(table);
            tableScroll.setBorder(BorderFactory.createEmptyBorder());
            tableScroll.setPreferredSize(new Dimension(750, 220));
            center.add(tableScroll);

            center.add(Box.createVerticalStrut(10));
            center.add(sectionTitle("Neurons per ganglion (box plot)"));

            BufferedImage boxImgHu = makeBoxPlotFromCounts(r.neuronsPerGanglion);
            JLabel chartHu = new JLabel(new ImageIcon(boxImgHu));
            JPanel chartWrapHu = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            chartHu.setBorder(new EmptyBorder(6,0,8,0));
            chartWrapHu.add(chartHu);

            JButton saveHu = new JButton("Save plot…");
            saveHu.addActionListener(e -> {
                try {
                    File outPng = new File(r.outDir, "Plot_neurons_per_ganglion_box.png");
                    saveImage(boxImgHu, outPng);
                    JOptionPane.showMessageDialog(center, "Saved:\n" + outPng.getAbsolutePath(),
                            "Saved", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) { IJ.handleException(ex); }
            });

            JPanel chartWithBtn = new JPanel();
            chartWithBtn.setLayout(new BoxLayout(chartWithBtn, BoxLayout.Y_AXIS));
            chartWithBtn.add(chartWrapHu);
            JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            btnRow.add(saveHu);
            btnRow.setBorder(new EmptyBorder(0,0,8,0));
            chartWithBtn.add(btnRow);

            center.add(chartWithBtn);
        }

        // Summary table of totals (markers + combos)
        center.add(Box.createVerticalStrut(10));
        center.add(sectionTitle("Multi-marker summary (Hu-gated totals)"));


        JTable summary = makeTotalsTable(r.totals);
        JScrollPane tableScroll = new JScrollPane(summary);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());

    // --- auto-size the scroll pane to the table height (no extra white) ---
        int rows  = summary.getRowCount();
        int rowH  = summary.getRowHeight();
        int headH = summary.getTableHeader().getPreferredSize().height;
        int vpad  = 2;

    // desired height = header + rows * rowHeight (clamped)
        int autoH = headH + rows * rowH + vpad;
        int minH  = headH + Math.min(rows, 3) * rowH + vpad;   // show up to 3 rows before it gets tiny
        int maxH  = headH + 8 * rowH + vpad;                   // cap: if >8 rows, enable scrolling
        int prefH = Math.max(minH, Math.min(autoH, maxH));

        tableScroll.setPreferredSize(new Dimension(720, prefH));
    // with BoxLayout, also cap the max size so it doesn’t stretch vertically
        tableScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, prefH));

    // don’t paint empty viewport area
        summary.setFillsViewportHeight(false);
    // match viewport bg to table (prevents a white band on some LaFs)
        tableScroll.getViewport().setBackground(summary.getBackground());

        center.add(tableScroll);

        // Tabs: Markers vs Combinations
        center.add(Box.createVerticalStrut(10));
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Markers", makeBoxPlotPanel(r, /*combos=*/false));
        tabs.addTab("Combinations", makeBoxPlotPanel(r, /*combos=*/true));
        if(r.nGanglia != null  && r.nGanglia >0) {
            center.add(tabs);
        }

        if (r.doSpatialAnalysis){
            center.add(Box.createVerticalStrut(10));
            addSpatialHistogramsSection(center, r);
        }



        JScrollPane scroller = new JScrollPane(center);
        scroller.setBorder(null);
        f.add(scroller, BorderLayout.CENTER);

        // footer with Save All Plots
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveAll = new JButton("Save plots…");
        saveAll.addActionListener(e -> {
            try {
                saveAllPlots(r);
                JOptionPane.showMessageDialog(f, "All plots saved to:\n" + r.outDir.getAbsolutePath(),
                        "Saved", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                IJ.handleException(ex);
            }
        });
        footer.add(new JButton("Open folder"){{
            addActionListener(e -> openFolder(r.outDir));
        }});
        footer.add(saveAll);
        footer.add(new JButton("Close"){{
            addActionListener(e -> f.dispose());
        }});
        f.add(footer, BorderLayout.SOUTH);

        f.pack();
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        f.setSize(Math.min(f.getWidth(), 980), Math.min(f.getHeight(), 760));
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }


    /**
     * Create a scrollable column of per-ganglion box plots, filtering by single markers or
     * combinations depending on the {@code combos} flag.
     *
     * @param r      Workflow results.
     * @param combos True to include only names containing '+', false for single markers.
     */
    private static JPanel makeBoxPlotPanel(MultiResult r, boolean combos) {
        // Build a scrollable column of box plots (one per marker/combo)
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(new EmptyBorder(4, 4, 4, 4));

        for (Map.Entry<String,int[]> e : r.perGanglia.entrySet()) {
            String name = e.getKey();
            boolean isCombo = name.contains("+");
            if (combos != isCombo) continue; // filter for this tab

            int[] counts = e.getValue();
            BufferedImage img = makeBoxPlotFromCounts(counts /*yLabel=*/);

            JPanel card = new JPanel(new BorderLayout());
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(220,220,220)),
                    new EmptyBorder(6,6,6,6)
            ));
            JLabel title = new JLabel(name + " — neurons per ganglion (box)");
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            title.setBorder(new EmptyBorder(0,0,6,0));
            card.add(title, BorderLayout.NORTH);

            JLabel chart = new JLabel(new ImageIcon(img));
            chart.setHorizontalAlignment(SwingConstants.CENTER);
            card.add(chart, BorderLayout.CENTER);

            JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            JButton save = new JButton("Save plot…");
            save.addActionListener(ev -> {
                try {
                    File out = new File(r.outDir, "Plot_box_" + sanitize(name) + ".png");
                    saveImage(img, out);
                    JOptionPane.showMessageDialog(card, "Saved:\n" + out.getAbsolutePath(),
                            "Saved", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    IJ.handleException(ex);
                }
            });
            row.add(save);
            row.setBorder(new EmptyBorder(6,0,0,0));
            card.add(row, BorderLayout.SOUTH);

            col.add(card);
            col.add(Box.createVerticalStrut(8));
        }

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(col, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(wrap);
        sp.setBorder(BorderFactory.createEmptyBorder());
        JPanel outer = new JPanel(new BorderLayout());
        outer.add(sp, BorderLayout.CENTER);
        return outer;
    }

    /**
     * Create a 2-column table: "marker_or_combo" and "hu_gated_total".
     * @param totals LinkedHashMap preserving run order.
     */
    private static JTable makeTotalsTable(LinkedHashMap<String,Integer> totals) {
        DefaultTableModel model = new DefaultTableModel(new Object[]{"marker_or_combo","hu_gated_total"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        totals.forEach((k,v) -> model.addRow(new Object[]{k, v}));
        JTable t = new JTable(model);
        t.setFillsViewportHeight(true);
        t.setRowHeight(22);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        t.setFont(t.getFont().deriveFont(12f));
        t.getTableHeader().setFont(t.getTableHeader().getFont().deriveFont(Font.BOLD, 12f));

        TableColumnModel cm = t.getColumnModel();
        if (cm.getColumnCount() >= 2) {
            cm.getColumn(0).setPreferredWidth(260);
            cm.getColumn(1).setPreferredWidth(140);
        }
        return t;
    }

    /**
     * Construct a box plot image for per-ganglion counts (skipping id 0 and zeros).
     * @param countsPerGanglion Array indexed by ganglion id (0 unused).
     */
    private static BufferedImage makeBoxPlotFromCounts(int[] countsPerGanglion) {
        // Skip index 0 and zeros (zeros = absent ganglia for this marker)
        List<Integer> vals = new ArrayList<>();
        for (int i = 1; i < countsPerGanglion.length; i++) {
            int v = countsPerGanglion[i];
            if (v > 0) vals.add(v);
        }
        if (vals.isEmpty()) return placeholderImage(560, 300, "No ganglia data");

        double[] q = fiveNumberSummary(vals); // {min, q1, median, q3, max}

        int W = 560, H = 300, padL = 60, padR = 20, padT = 36, padB = 40;
        BufferedImage bi = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);

        // frame
        int x0 = padL, x1 = W - padR, y0 = H - padB, y1 = padT;
        g.setColor(new Color(220,220,220));
        g.drawRect(x0, y1, (x1 - x0), (y0 - y1));

        // y ticks/grid
        int ymax = (int)Math.ceil(q[4] * 1.10);
        ymax = Math.max(ymax, 1);
        g.setFont(g.getFont().deriveFont(12f));
        for (int i = 0; i <= 5; i++) {
            double yv = i * (ymax / 5.0);
            int y = y0 - (int)Math.round((yv / ymax) * (y0 - y1));
            g.setColor(new Color(235,235,235)); g.drawLine(x0, y, x1, y);
            g.setColor(new Color(90,90,90));
            String lab = String.valueOf((int)Math.round(yv));
            int tw = g.getFontMetrics().stringWidth(lab);
            g.drawString(lab, x0 - tw - 6, y + 4);
        }
        // x-axis label
        String xlab = "Neuron count";
        int xw = g.getFontMetrics().stringWidth(xlab);
        g.setColor(new Color(80,80,80));
        g.drawString(xlab, x0 + ((x1 - x0) - xw) / 2, H - 10);

        // mapper
        int finalYmax = ymax;
        java.util.function.DoubleFunction<Integer> Y = v -> y0 - (int)Math.round((v / finalYmax) * (y0 - y1));

        // box
        int cx = (x0 + x1) / 2;
        int boxW = 80;

        g.setColor(new Color(70,70,70));
        g.drawLine(cx, Y.apply(q[0]), cx, Y.apply(q[1]));
        g.drawLine(cx, Y.apply(q[3]), cx, Y.apply(q[4]));
        g.drawLine(cx - 15, Y.apply(q[0]), cx + 15, Y.apply(q[0]));
        g.drawLine(cx - 15, Y.apply(q[4]), cx + 15, Y.apply(q[4]));

        int top = Y.apply(q[3]), bot = Y.apply(q[1]);
        g.setColor(new Color(180,205,255));
        g.fillRect(cx - boxW/2, top, boxW, bot - top);
        g.setColor(new Color(60,90,160));
        g.drawRect(cx - boxW/2, top, boxW, bot - top);

        int my = Y.apply(q[2]);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(cx - boxW/2, my, cx + boxW/2, my);

        // stats inside plot, top-left
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(60,60,60));
        String stats = String.format(java.util.Locale.US,
                "min=%.0f  Q1=%.0f  median=%.0f  Q3=%.0f  max=%.0f   (n=%d)",
                q[0], q[1], q[2], q[3], q[4], vals.size());
        int ascent = g.getFontMetrics().getAscent();
        g.drawString(stats, x0 + 6, y1 + ascent + 2);

        g.dispose();
        return bi;
    }

    /**
     * Five-number summary for a sorted list of integers using “median of halves”.
     * @param vals Non-empty list of positive counts.
     * @return {min, Q1, median, Q3, max}.
     */
    private static double[] fiveNumberSummary(List<Integer> vals) {
        Collections.sort(vals);
        int n = vals.size();
        java.util.function.IntFunction<Double> at = i -> vals.get(Math.max(0, Math.min(n-1, i))).doubleValue();

        java.util.function.BiFunction<Integer,Integer,Double> median = (lo, hi) -> {
            int len = hi - lo + 1;
            if (len <= 0) return Double.NaN;
            int mid = lo + len/2;
            if ((len & 1) == 1) return at.apply(mid);
            return (at.apply(mid-1) + at.apply(mid)) / 2.0;
        };
        double med = median.apply(0, n-1);
        int hiL = (n%2==0) ? (n/2 - 1) : (n/2 - 1);
        double q1 = median.apply(0, Math.max(0, hiL));
        int loU = (n%2==0) ? (n/2) : (n/2 + 1);
        double q3 = median.apply(loU, n-1);
        return new double[]{ vals.get(0), q1, med, q3, vals.get(n-1) };
    }

    // ---------- misc helpers ----------

    private static JPanel sectionTitle(String text) {
        JPanel p = new JPanel(new BorderLayout());
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        p.add(l, BorderLayout.CENTER);
        p.setBorder(new EmptyBorder(2,0,6,0)); // extra space to avoid collisions
        return p;
    }

    private static JPanel makeThumbCard(String title, ImagePlus impForThumb, int widthPx) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220,220,220)),
                new EmptyBorder(4,4,4,4)
        ));

        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD));
        t.setBorder(new EmptyBorder(0,0,2,0));
        t.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(t, BorderLayout.NORTH);

        JLabel thumb = new JLabel(new ImageIcon(makeThumbnail(impForThumb, widthPx)));
        thumb.setBorder(new EmptyBorder(0,0,0,0));
        thumb.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(thumb, BorderLayout.CENTER);

        return card;
    }

    private static BufferedImage makeThumbnail(ImagePlus imp, int w) {
        if (imp == null || imp.getProcessor() == null) {
            BufferedImage bi = new BufferedImage(200, 120, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = bi.createGraphics();
            g.setColor(new Color(245,245,245)); g.fillRect(0,0,bi.getWidth(), bi.getHeight());
            g.setColor(Color.DARK_GRAY); g.drawString("No image", 70, 60);
            g.dispose();
            return bi;
        }
        double scale = w / (double) imp.getWidth();
        int h = Math.max(1, (int) Math.round(imp.getHeight() * scale));
        return imp.getProcessor().resize(w, h, true).getBufferedImage();
    }

    private static ImagePlus loadForThumb(File f, ImagePlus fallback) {
        ImagePlus imp = (f != null && f.exists()) ? IJ.openImage(f.getAbsolutePath()) : null;
        return (imp != null) ? imp : fallback;
    }

    /** Save every available box plot and the Hu box plot (if present). */
    private static void saveAllPlots(MultiResult r) throws IOException {
        // markers and combos
        for (Map.Entry<String,int[]> e : r.perGanglia.entrySet()) {
            String name = e.getKey();
            BufferedImage img = makeBoxPlotFromCounts(e.getValue());
            File out = new File(r.outDir, "Plot_box_" + sanitize(name) + ".png");
            saveImage(img, out);
        }

        // Save the Hu-only box plot too (if available)
        if (r.neuronsPerGanglion != null && r.neuronsPerGanglion.length > 1) {
            BufferedImage imgHu = makeBoxPlotFromCounts(r.neuronsPerGanglion);
            File outHu = new File(r.outDir, "Plot_neurons_per_ganglion_box.png");
            saveImage(imgHu, outHu);
        }

    }

    /** PNG writer with parent directory creation. */
    private static void saveImage(BufferedImage img, File out) throws IOException {
        out.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", out);
    }

    /** Filename-safe transformation. */
    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9_\\-+]+", "_");
    }

    /** Placeholder (white background) with message. */
    private static BufferedImage placeholderImage(int w, int h, String msg) {
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0,0,w,h);
        g.setColor(Color.DARK_GRAY); g.drawString(msg, Math.max(10, w/2-60), h/2);
        g.dispose();
        return bi;
    }

    /**
     * Add the "Spatial histograms" section (combined, then one card per dataset).
     * Datasets are parsed from {@code outDir/spatial_analysis/Neighbour_count_*.csv}.
     *
     * @param center Container to append to.
     * @param r      Multiplex result.
     */
    private static void addSpatialHistogramsSection(JPanel center, MultiResult r) {
        java.util.List<SpatialDataset> sets = loadAllSpatialDatasets(r.outDir);
        if (sets.isEmpty()) return;

        center.add(sectionTitle("Spatial histograms"));

        // choose a common bin width across all so axes align visually
        int globalMax = 0;
        for (SpatialDataset s : sets)
            globalMax = Math.max(globalMax, java.util.Arrays.stream(s.counts).max().orElse(0));
        int binW = Math.max(1, (int)Math.ceil((globalMax + 1) / 15.0)); // ~15 bins target

        // combined first
        int[] combined = sets.stream()
                .flatMapToInt(s -> java.util.Arrays.stream(s.counts))
                .toArray();
        center.add(makeSpatialCardWithSummary("Combined (Hu + markers)", combined, binW,
                new File(r.outDir, "Plot_spatial_hist_combined.png")));

        // then one per dataset (Hu first if present)
        // ensure Hu is first
        sets.sort((a,b) -> {
            if ("Hu".equalsIgnoreCase(a.name)) return -1;
            if ("Hu".equalsIgnoreCase(b.name)) return 1;
            return a.name.compareToIgnoreCase(b.name);
        });

        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(new EmptyBorder(0,0,0,0));

        for (SpatialDataset s : sets) {
            col.add(makeSpatialCardWithSummary(s.name, s.counts, binW,
                    new File(r.outDir, "Plot_spatial_hist_" + sanitize(s.name) + ".png")));
            col.add(Box.createVerticalStrut(8));
        }

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(col, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(wrap);
        sp.setBorder(BorderFactory.createEmptyBorder());
        center.add(sp);
    }

    /** Holder for a spatial dataset (name + values). */
    private static final class SpatialDataset {
        final String name; final int[] counts;
        SpatialDataset(String n, int[] c){ name=n; counts=c; }
    }

    /** Load all datasets under {@code spatial_analysis/}. */
    private static java.util.List<SpatialDataset> loadAllSpatialDatasets(File outDir) {
        java.util.List<SpatialDataset> list = new java.util.ArrayList<>();
        File dir = new File(outDir, "spatial_analysis");
        if (!dir.isDirectory()) return list;
        File[] files = dir.listFiles(f -> f.getName().startsWith("Neighbour_count_") && f.getName().endsWith(".csv"));
        if (files == null) return list;

        for (File csv : files) {
            String name = csv.getName();
            // Neighbour_count_<cellType>.csv
            String cellType = name.substring("Neighbour_count_".length(), name.length() - ".csv".length());
            int[] vals = loadSpatialCountsFromCsv(csv);
            if (vals.length > 0) list.add(new SpatialDataset(cellType, vals));
        }
        return list;
    }

    /** Parse neighbor-count CSV (lenient on separators and headers). */
    private static int[] loadSpatialCountsFromCsv(File csv) {
        java.util.ArrayList<Integer> vals = new java.util.ArrayList<>();
        try {
            java.util.List<String> lines = java.nio.file.Files.readAllLines(csv.toPath());
            for (String line : lines) {
                if (line == null) continue;
                String t = line.trim();
                if (t.isEmpty()) continue;
                String[] parts = t.split("[,\t]");
                if (parts.length < 2) continue;
                // skip headers / non-numeric first column
                if (!parts[0].trim().matches("\\d+")) continue;
                try {
                    int v = Integer.parseInt(parts[1].trim());
                    if (v >= 0) vals.add(v);
                } catch (NumberFormatException ignore) {}
            }
        } catch (Exception ex) {
            IJ.log("Spatial CSV read failed (" + csv.getName() + "): " + ex.getMessage());
        }
        return vals.stream().mapToInt(i->i).toArray();
    }

    /** Draw the histogram image with a common bin width. */
    private static BufferedImage makeSpatialHistogram(int[] values, int binW) {
        if (values == null || values.length == 0) return placeholderImage(560,300,"No spatial data");
        int vmax = java.util.Arrays.stream(values).max().orElse(0);
        int bins = Math.max(1, (int)Math.ceil((vmax+1)/(double)binW));

        int[] counts = new int[bins];
        for (int v : values) counts[Math.min(bins-1, v / binW)]++;
        int peak = java.util.Arrays.stream(counts).max().orElse(1);

        int W=560,H=300,padL=50,padR=20,padT=24,padB=40;
        BufferedImage bi = new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0,0,W,H);

        int x0=padL, x1=W-padR, y0=H-padB, y1=padT;
        // grid + frame
        g.setColor(new Color(235,235,235));
        for (int i=0;i<=5;i++){
            int y = y0 - (int)Math.round(i*(y0-y1)/5.0);
            g.drawLine(x0,y,x1,y);
        }
        g.setColor(new Color(220,220,220)); g.drawRect(x0,y1,(x1-x0),(y0-y1));

        // bars
        int bw = Math.max(3, (x1-x0- (bins-1)*4) / Math.max(1,bins));
        int x = x0;
        g.setColor(new Color(120,150,220));
        for (int i=0;i<bins;i++){
            int h = (peak==0) ? 0 : (int)Math.round((counts[i]/(double)peak)*(y0-y1));
            g.fillRect(x, y0-h, bw, h);
            x += bw + 4;
        }

        // axes labels
        g.setColor(new Color(90,90,90));
        g.setFont(g.getFont().deriveFont(12f));
        // y labels
        for (int i=0;i<=5;i++){
            int y = y0 - (int)Math.round(i*(y0-y1)/5.0);
            String lab = String.valueOf((int)Math.round(i*peak/5.0));
            int tw=g.getFontMetrics().stringWidth(lab);
            g.drawString(lab, x0 - tw - 6, y + 4);
        }
        // x labels (bin ranges)
        g.setFont(g.getFont().deriveFont(11f));
        x = x0;
        for (int i=0;i<bins;i++){
            int lo = i*binW, hi = Math.min(vmax, (i+1)*binW - 1);
            String lab = (lo==hi) ? String.valueOf(lo) : (lo + "–" + hi);
            int tw=g.getFontMetrics().stringWidth(lab);
            g.drawString(lab, x + (bw - tw)/2, H-10);
            x += bw + 4;
        }

        // title inside chart
        g.setFont(g.getFont().deriveFont(Font.BOLD, 13f));
        g.setColor(new Color(60,60,60));
        g.drawString("Histogram of neighbor counts (bin="+binW+")", x0+4, y1-6);

        g.dispose();
        return bi;
    }

    /** Stats and card builder identical in spirit to ResultsMultiNoHuUI (see there for details). */
    private static final class Stats {
        final int n, min, max; final double mean, median, stdev;
        Stats(int n,int min,int max,double mean,double median,double stdev){
            this.n=n; this.min=min; this.max=max; this.mean=mean; this.median=median; this.stdev=stdev;
        }
    }
    private static Stats stats(int[] a) {
        if (a == null || a.length == 0) return new Stats(0, 0, 0, 0, 0, 0);
        int n = a.length;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE; double sum=0;
        for (int v : a) { if (v<min) min=v; if (v>max) max=v; sum+=v; }
        double mean = sum/n;
        int[] s = java.util.Arrays.copyOf(a, n);
        java.util.Arrays.sort(s);
        double median = (n%2==1) ? s[n/2] : (s[n/2-1] + s[n/2]) / 2.0;
        double ss=0; for (int v : a) { double d=v-mean; ss += d*d; }
        double stdev = Math.sqrt(ss / Math.max(1, n-1));
        return new Stats(n,min,max,mean,median,stdev);
    }

    // ===== Card with summary + histogram + save button =====
    private static JPanel makeSpatialCardWithSummary(String title, int[] values, int binW, File savePath) {
        BufferedImage img = makeSpatialHistogram(values, binW);
        Stats st = stats(values);

        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220,220,220)),
                new EmptyBorder(6,6,6,6)
        ));

        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD));
        t.setBorder(new EmptyBorder(0,0,6,0));
        card.add(t, BorderLayout.NORTH);

        // summary grid (2 rows x 3 columns)
        JPanel summary = new JPanel(new GridLayout(2, 3, 8, 4));
        summary.setBorder(new EmptyBorder(0,0,6,0));
        summary.add(new JLabel("n = " + st.n));
        summary.add(new JLabel(String.format(java.util.Locale.US, "mean = %.2f", st.mean)));
        summary.add(new JLabel(String.format(java.util.Locale.US, "median = %.1f", st.median)));
        summary.add(new JLabel("min = " + st.min));
        summary.add(new JLabel("max = " + st.max));
        summary.add(new JLabel(String.format(java.util.Locale.US, "stdev = %.2f", st.stdev)));

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(summary);

        JLabel chart = new JLabel(new ImageIcon(img));
        chart.setHorizontalAlignment(SwingConstants.CENTER);
        center.add(chart);

        card.add(center, BorderLayout.CENTER);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        JButton save = new JButton("Save histogram…");
        save.addActionListener(ev -> {
            try {
                saveImage(img, savePath);
                JOptionPane.showMessageDialog(card, "Saved:\n" + savePath.getAbsolutePath(),
                        "Saved", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                IJ.handleException(ex);
            }
        });
        row.add(save);
        row.setBorder(new EmptyBorder(6,0,0,0));
        card.add(row, BorderLayout.SOUTH);

        return card;
    }


}
