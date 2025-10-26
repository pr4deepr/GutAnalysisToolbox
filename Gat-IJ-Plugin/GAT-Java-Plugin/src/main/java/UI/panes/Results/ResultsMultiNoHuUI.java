package UI.panes.Results;

import Features.AnalyseWorkflows.NeuronsMultiNoHuPipeline.NoHuResult;
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
 * Result viewer for the "Multi-Channel (No Hu)" workflow.
 *
 * Presents:
 *  • A header with output folder and optional ganglia count
 *  • Thumbnail previews of saved overlays (ganglia and the first two marker overlays)
 *  • A totals table for markers and combinations
 *  • Per-ganglion box plots for each marker/combination (if available)
 *  • Optional spatial-analysis section (histograms + summary stats) if CSVs are present
 *
 * Threading & EDT:
 *  All Swing components are created and mutated on the EDT. Callers should invoke
 *  {@link #promptAndMaybeShow(NoHuResult)} from any thread; it will open Swing UI safely.
 *
 * File I/O:
 *  Reads overlay images from {@code r.outDir}. Spatial CSVs are expected under
 *  {@code r.outDir/spatial_analysis/Neighbour_count_<marker>.csv}.
 */
public class ResultsMultiNoHuUI {


    /**
     * Ask the user whether to preview results, open the output folder, or end.
     *
     * @param r The finished pipeline result (output dir, base name, totals, per-ganglion arrays, etc.).
     */
    public static void promptAndMaybeShow(NoHuResult r) {
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

    /**
     * Attempt to open a folder in the native desktop file manager.
     * Falls back to showing the absolute path via ImageJ if Desktop API is unsupported.
     *
     * @param dir Directory to open.
     */
    private static void openFolder(File dir) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir);
            else IJ.showMessage("Folder: " + dir.getAbsolutePath());
        } catch (IOException e) {
            IJ.handleException(e);
        }
    }

    /**
     * Build and show the results JFrame for a completed "No Hu" run.
     *
     * Contents:
     *  - Header (paths, counts)
     *  - Overlay thumbnails (ganglia + up to two single-marker overlays; falls back to MAX if none)
     *  - Totals table for markers & combinations
     *  - Box plots for per-ganglion counts (Markers / Combinations tabs)
     *  - Optional spatial analysis section (combined + per-marker histograms)
     *  - Footer with "Open folder", optional "Save plots…", and "Close"
     *
     * @param r Aggregated result of the No-Hu workflow.
     */
    private static void showResultsFrame(NoHuResult r) {
        JFrame f = new JFrame("Results – " + r.baseName + " (no-Hu multi)");
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        f.setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel(new GridLayout(0,1));
        header.setBorder(new EmptyBorder(6,12,2,12));
        header.add(new JLabel("Output folder: " + r.outDir.getAbsolutePath()));
        if (r.nGanglia != null) header.add(new JLabel("Detected ganglia: " + r.nGanglia));
        f.add(header, BorderLayout.NORTH);

        // Center column
        JPanel center = new JPanel();
        center.setBorder(new EmptyBorder(4,12,4,12));
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        // Overlays: show ganglia overlay (if any) + up to two marker overlays
        center.add(sectionTitle("Images (overlays)"));
        JPanel thumbsRow = new JPanel(new GridLayout(0, 1, 0, 6));

        File gangliaOverlay = new File(r.outDir, "MAX_" + r.baseName + "_ganglia_overlay.tif");
        if (gangliaOverlay.exists()) {
            thumbsRow.add(makeThumbCard("Ganglia overlay", loadForThumb(gangliaOverlay, r.max), 560));
        }

        // first two marker overlays (in insertion order, i.e., your run order)
        int added = 0;
        for (String name : r.totals.keySet()) {
            if (name.contains("+")) continue; // skip combos for thumbnails
            File mOv = new File(r.outDir, "MAX_" + r.baseName + "_" + name + "_overlay.tif");
            if (mOv.exists()) {
                thumbsRow.add(makeThumbCard(name + " overlay", loadForThumb(mOv, r.max), 560));
                if (++added >= 2) break;
            }
        }
        if (thumbsRow.getComponentCount() == 0) {
            thumbsRow.add(makeThumbCard("MAX", r.max, 560));
        }
        center.add(thumbsRow);

        // Summary table (markers + combos)
        center.add(Box.createVerticalStrut(10));
        center.add(sectionTitle("Multi-marker summary (totals)"));

        JTable summary = makeTotalsTable(r.totals);
        JScrollPane tableScroll = new JScrollPane(summary);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());

        // auto-size to rows (no white void); cap to 8 rows
        int rows  = summary.getRowCount();
        int rowH  = summary.getRowHeight();
        int headH = summary.getTableHeader().getPreferredSize().height;
        int vpad  = 2;
        int autoH = headH + rows * rowH + vpad;
        int minH  = headH + Math.min(rows, 3) * rowH + vpad;
        int maxH  = headH + 8 * rowH + vpad;
        int prefH = Math.max(minH, Math.min(autoH, maxH));
        tableScroll.setPreferredSize(new Dimension(720, prefH));
        tableScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, prefH));
        summary.setFillsViewportHeight(false);
        tableScroll.getViewport().setBackground(summary.getBackground());

        center.add(tableScroll);

        // Box plots tabbed (only if ganglia counts exist)
        if (r.perGanglia != null && !r.perGanglia.isEmpty()) {
            center.add(Box.createVerticalStrut(10));
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Markers", makeBoxPlotPanel(r, /*combos=*/false));
            tabs.addTab("Combinations", makeBoxPlotPanel(r, /*combos=*/true));
            center.add(tabs);
        }

        if(r.doSpatialAnalysis){
            center.add(Box.createVerticalStrut(10));
            maybeAddSpatialSection(center, r);
        }

        JScrollPane scroller = new JScrollPane(center);
        scroller.setBorder(null);
        f.add(scroller, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(new JButton("Open folder"){{
            addActionListener(e -> openFolder(r.outDir));
        }});
        if (r.perGanglia != null && !r.perGanglia.isEmpty()) {
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
            footer.add(saveAll);
        }
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
     * Create a scrollable column of box-plot cards for either markers or combinations.
     *
     * @param r       The workflow result.
     * @param combos  If true, include only names containing '+'; otherwise only single markers.
     * @return A panel ready to be embedded in a tab.
     */
    private static JPanel makeBoxPlotPanel(NoHuResult r, boolean combos) {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(new EmptyBorder(4, 4, 4, 4));

        for (Map.Entry<String,int[]> e : r.perGanglia.entrySet()) {
            String name = e.getKey();
            boolean isCombo = name.contains("+");
            if (combos != isCombo) continue;

            BufferedImage img = makeBoxPlotFromCounts(e.getValue());
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
     * Build a two-column table: marker_or_combo → total count.
     *
     * @param totals LinkedHashMap preserving run order.
     * @return JTable with fixed row height, tuned column widths, and non-editable cells.
     */
    private static JTable makeTotalsTable(LinkedHashMap<String,Integer> totals) {
        DefaultTableModel model = new DefaultTableModel(new Object[]{"marker_or_combo","total_cells"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        totals.forEach((k,v) -> model.addRow(new Object[]{k, v}));

        JTable t = new JTable(model);
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
     * Render a single vertical box plot for "neurons per ganglion".
     * Skips index 0 and zero counts (ganglia absent for that marker).
     *
     * @param countsPerGanglion Array indexed by ganglion id (0 unused).
     * @return A {@link BufferedImage} ready for embedding into a Swing label.
     */
    private static BufferedImage makeBoxPlotFromCounts(int[] countsPerGanglion) {
        List<Integer> vals = new ArrayList<>();
        // Skip index 0 (1..G); include only >0 (ganglia with at least 1 cell)
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

        g.setColor(Color.WHITE); g.fillRect(0,0,W,H);

        int x0 = padL, x1 = W - padR, y0 = H - padB, y1 = padT;
        g.setColor(new Color(220,220,220));
        g.drawRect(x0, y1, (x1-x0), (y0-y1));

        int ymax = (int)Math.ceil(q[4] * 1.10); ymax = Math.max(ymax, 1);
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
        String xlab = "Neuron count";
        int xw = g.getFontMetrics().stringWidth(xlab);
        g.setColor(new Color(80,80,80));
        g.drawString(xlab, x0 + ((x1 - x0) - xw) / 2, H - 10);

        final int Yspan = (y0 - y1);
        int finalYmax = ymax;
        java.util.function.DoubleFunction<Integer> Y = v -> y0 - (int)Math.round((v / finalYmax) * Yspan);

        int cx = (x0 + x1) / 2, boxW = 80;
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
     * Compute {min, Q1, median, Q3, max} for integer values using the Tukey-style
     * “median of halves” approach. For n odd, the median element is excluded from halves.
     *
     * @param vals Non-empty list of positive counts.
     * @return five-number summary.
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

    /** Small section header panel with bold label and spacing. */
    private static JPanel sectionTitle(String text) {
        JPanel p = new JPanel(new BorderLayout());
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        p.add(l, BorderLayout.CENTER);
        p.setBorder(new EmptyBorder(2,0,6,0));
        return p;
    }


    /**
     * Make a thumbnail “card” with bold centered title and an ImageIcon scaled to width.
     *
     * @param title      Card header text.
     * @param impForThumb Source image (may be MAX or an opened overlay). Not disposed here.
     * @param widthPx    Target thumbnail width.
     * @return Panel for insertion.
     */

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
        thumb.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(thumb, BorderLayout.CENTER);

        return card;
    }

    /**
     * Scale an ImagePlus to a fixed width, preserving aspect ratio (bicubic),
     * returning a BufferedImage for display.
     *
     * @param imp Raw or overlay image.
     * @param w   Desired width.
     */
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

    /**
     * Open an image from disk (if the file exists) for thumbnailing; falls back to the provided
     * MAX image if opening fails. The returned ImagePlus is not disposed here.
     *
     * @param f        File to open (.tif overlays).
     * @param fallback Fallback ImagePlus to use.
     */
    private static ImagePlus loadForThumb(File f, ImagePlus fallback) {
        ImagePlus imp = (f != null && f.exists()) ? IJ.openImage(f.getAbsolutePath()) : null;
        return (imp != null) ? imp : fallback;
    }

    /**
     * Save every available box plot (markers and combinations) under {@code r.outDir}.
     * File names: {@code Plot_box_<name>.png}.
     *
     * @throws IOException if writing fails.
     */
    private static void saveAllPlots(NoHuResult r) throws IOException {
        for (Map.Entry<String,int[]> e : r.perGanglia.entrySet()) {
            String name = e.getKey();
            BufferedImage img = makeBoxPlotFromCounts(e.getValue());
            File out = new File(r.outDir, "Plot_box_" + sanitize(name) + ".png");
            saveImage(img, out);
        }
    }

    /** Write a PNG, creating parent dirs if needed. */
    private static void saveImage(BufferedImage img, File out) throws IOException {
        out.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", out);
    }

    /** Sanitize a string for filenames: keep letters, digits, _ - +; replace others with '_'. */
    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9_\\-+]+", "_");
    }

    /** Sanitize a string for filenames: keep letters, digits, _ - +; replace others with '_'. */
    private static BufferedImage placeholderImage(int w, int h, String msg) {
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0,0,w,h);
        g.setColor(Color.DARK_GRAY); g.drawString(msg, Math.max(10, w/2-60), h/2);
        g.dispose();
        return bi;
    }

    /**
     * Add a "Spatial analysis" section if per-marker neighbor-count CSVs exist.
     * Renders a combined histogram first, then one card per marker.
     *
     * Expected CSV format per marker:
     *  "Label,<tab or comma> No of cells around <Marker>"
     *  rows with integer label + integer count.
     *
     * @param center Container in the results frame to which the section is appended.
     * @param r      No-Hu result object (for dir & names).
     */
    private static void maybeAddSpatialSection(JPanel center, NoHuResult r) {
        java.util.List<SpatialData> sets = loadAllSpatial(r);
        if (sets.isEmpty()) return;

        center.add(sectionTitle("Spatial analysis"));

        // Combined first (all markers concatenated)
        int[] combined = concatAll(sets);
        int maxVal = java.util.Arrays.stream(combined).max().orElse(0);
        int binW = Math.max(1, (int)Math.ceil((maxVal + 1) / 15.0));

        center.add(makeSpatialCardWithSummary(
                "Combined (all markers)",
                combined,
                binW,
                new File(r.outDir, "Plot_spatial_hist_combined.png")
        ));

        // Then one card per marker
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(new EmptyBorder(4, 4, 4, 4));

        for (SpatialData s : sets) {
            int localMax = java.util.Arrays.stream(s.counts).max().orElse(0);
            int localBin = Math.max(1, (int)Math.ceil((localMax + 1) / 15.0));

            col.add(makeSpatialCardWithSummary(
                    s.name,
                    s.counts,
                    localBin,
                    new File(r.outDir, "Plot_spatial_hist_" + sanitize(s.name) + ".png")
            ));
            col.add(Box.createVerticalStrut(8));
        }
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(col, BorderLayout.NORTH);
        center.add(wrap);
    }

    /** Small holder for spatial counts: marker/combination name and its counts. */
    private static final class SpatialData {
        final String name; final int[] counts;
        SpatialData(String n, int[] c){ name=n; counts=c; }
    }

    /**
     * Load all per-marker spatial datasets from {@code r.outDir/spatial_analysis}.
     * Skips combination names (those containing '+').
     *
     * @param r The No-Hu result.
     * @return List of parsed datasets; empty if none present.
     */
    private static java.util.List<SpatialData> loadAllSpatial(NoHuResult r) {
        java.util.List<SpatialData> out = new java.util.ArrayList<>();
        // marker names = totals keys without '+'
        for (String name : r.totals.keySet()) {
            if (name.contains("+")) continue;
            int[] vals = loadSpatialCounts(r.outDir, name);
            if (vals != null && vals.length > 0) {
                out.add(new SpatialData(name, vals));
            }
        }
        return out;
    }

    /**
     * Parse a single CSV file "Neighbour_count_<cellType>.csv" into an array of ints.
     * Ignores headers and malformed rows; accepts comma- or tab-separated values.
     *
     * @param outDir   Base output directory.
     * @param cellType Marker name used in file naming.
     * @return Counts array or {@code null} if file missing or unreadable.
     */
    private static int[] loadSpatialCounts(File outDir, String cellType) {
        try {
            File csv = new File(new File(outDir, "spatial_analysis"),
                    "Neighbour_count_" + cellType + ".csv");
            if (!csv.isFile()) return null;

            java.util.List<String> lines = java.nio.file.Files.readAllLines(csv.toPath());
            java.util.ArrayList<Integer> vals = new java.util.ArrayList<>();
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split("[,\t]");
                if (parts.length < 2) continue;
                // skip header-ish rows
                String c0 = parts[0].toLowerCase(java.util.Locale.ROOT);
                String c1 = parts[1].toLowerCase(java.util.Locale.ROOT);
                if (c0.contains("label")) continue;
                if (c1.contains("no of cells")) continue;

                try {
                    int v = Integer.parseInt(parts[1].trim());
                    if (v >= 0) vals.add(v);
                } catch (NumberFormatException ignore) {}
            }
            return vals.stream().mapToInt(i->i).toArray();
        } catch (Exception ex) {
            IJ.log("Spatial CSV load failed (" + cellType + "): " + ex.getMessage());
            return null;
        }
    }

    /**
     * Concatenate multiple integer arrays preserving order.
     *
     * @param sets List of spatial datasets.
     * @return Flat array containing all values.
     */
    private static int[] concatAll(java.util.List<SpatialData> sets) {
        int n = 0; for (SpatialData s : sets) n += s.counts.length;
        int[] out = new int[n]; int k=0;
        for (SpatialData s : sets) {
            System.arraycopy(s.counts, 0, out, k, s.counts.length);
            k += s.counts.length;
        }
        return out;
    }

    /**
     * Build a card with a 2x3 summary grid (n/mean/median/min/max/stdev),
     * the histogram image, and a "Save histogram…" button.
     *
     * @param title    Card title.
     * @param values   Data series to summarize/plot.
     * @param binW     Histogram bin width (integer).
     * @param savePath Where to write the PNG when saved.
     * @return Panel to insert in the results UI.
     */
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

        // summary grid (2 x 3)
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

    /** Simple summary stats container for spatial histograms. */
    private static final class Stats {
        final int n, min, max; final double mean, median, stdev;
        Stats(int n,int min,int max,double mean,double median,double stdev){
            this.n=n; this.min=min; this.max=max; this.mean=mean; this.median=median; this.stdev=stdev;
        }
    }

    /**
     * Compute n, min, max, mean, median, sample standard deviation for an int array.
     *
     * @param a Values (may be empty).
     * @return Stats with zeros if empty.
     */
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

    /**
     * Render a frequency histogram image (bars) with a light grid and axis labels.
     *
     * @param values Values to bin (may be empty).
     * @param binW   Bin width (>=1).
     * @return 560×300 RGB image (or placeholder if no data).
     */
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
        g.setColor(new Color(235,235,235));
        for (int i=0;i<=5;i++){
            int y = y0 - (int)Math.round(i*(y0-y1)/5.0);
            g.drawLine(x0,y,x1,y);
        }
        g.setColor(new Color(220,220,220)); g.drawRect(x0,y1,(x1-x0),(y0-y1));

        int bw = Math.max(3, (x1-x0- (bins-1)*4) / Math.max(1,bins));
        int x = x0;
        g.setColor(new Color(120,150,220));
        for (int i=0;i<bins;i++){
            int h = (peak==0) ? 0 : (int)Math.round((counts[i]/(double)peak)*(y0-y1));
            g.fillRect(x, y0-h, bw, h);
            x += bw + 4;
        }

        g.setColor(new Color(90,90,90));
        g.setFont(g.getFont().deriveFont(12f));
        for (int i=0;i<=5;i++){
            int y = y0 - (int)Math.round(i*(y0-y1)/5.0);
            String lab = String.valueOf((int)Math.round(i*peak/5.0));
            int tw=g.getFontMetrics().stringWidth(lab);
            g.drawString(lab, x0 - tw - 6, y + 4);
        }
        g.setFont(g.getFont().deriveFont(11f));
        x = x0;
        for (int i=0;i<bins;i++){
            int lo = i*binW, hi = Math.min(vmax, (i+1)*binW - 1);
            String lab = (lo==hi) ? String.valueOf(lo) : (lo + "–" + hi);
            int tw=g.getFontMetrics().stringWidth(lab);
            g.drawString(lab, x + (bw - tw)/2, H-10);
            x += bw + 4;
        }

        g.setFont(g.getFont().deriveFont(Font.BOLD, 13f));
        g.setColor(new Color(60,60,60));
        g.drawString("Histogram of neighbor counts", x0+4, y1-6);

        g.dispose();
        return bi;
    }

}
