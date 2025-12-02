package UI.panes;

import UI.Handlers.Navigator;
import UI.panes.Tools.HelpAndSupportPane;
import UI.panes.SettingPanes.NeuronWorkflowPane;
import UI.panes.SettingPanes.MultichannelPane;
import UI.panes.SettingPanes.MultiChannelNoHuPane;
import ij.IJ;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.dnd.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Landing / home screen for the Gut Analysis Toolbox UI.
 *
 * <p>
 * This panel is the first thing a user sees. It:
 * </p>
 * <ul>
 *   <li>Shows a greeting ("Good morning", etc.) and a live-updating clock.</li>
 *   <li>Provides quick navigation buttons into the main workflows
 *       (Neuron workflow, Multichannel workflow, etc.).</li>
 *   <li>Lets the user drag & drop an image file to open it directly in ImageJ/Fiji,
 *       or click to browse for a file.</li>
 *   <li>Shows a "tip of the day".</li>
 *   <li>Checks for required model assets (Hu, subtype, ganglia models) and reports
 *       whether they're present in the ImageJ/Fiji {@code models/} folder.</li>
 *   <li>Displays a short "recent images" list populated from user preferences.</li>
 * </ul>
 *
 * <p>
 * The content is laid out in titled sections inside a scrollable column.
 * The panel also starts a {@link javax.swing.Timer} to keep the greeting
 * and timestamp label fresh once per second.
 * </p>
 *
 * <p>
 * This class also manages "recents" persistence using {@link java.util.prefs.Preferences},
 * so recently opened images can be re-opened quickly.
 * </p>
 */
public class HomePane extends JPanel {

    public static final String Name = "Home";

    private final Navigator navigator;

    private final DateTimeFormatter clockFmt = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy  HH:mm:ss");
    private final JLabel clockLabel   = new JLabel();
    private final JLabel greetingLabel= new JLabel();
    private final JLabel titleLabel   = new JLabel("Gut Analysis Toolbox", SwingConstants.CENTER);
    private final Timer  clockTimer;

    // Recents
    private final DefaultListModel<String> recentModel = new DefaultListModel<>();
    private final JList<String> recentList = new JList<>(recentModel);
    private final Preferences prefs = Preferences.userNodeForPackage(HomePane.class);
    private static final String RECENTS_KEY = "recentImagesV1";
    private static final int RECENTS_MAX = 4;

    // Expected model names (under <Fiji>/models)
    private static final String HU_ZIP_PRIMARY      = "2D_enteric_neuron_v4_1.zip";
    private static final String HU_ZIP_FALLBACK     = "2D_enteric_neuron_v4.zip";
    private static final String SUBTYPE_ZIP         = "2D_enteric_neuron_subtype_v4.zip";
    private static final String GANGLIA_DIJ_FOLDER  = "2D_Ganglia_RGB_v3.bioimage.io.model";

    /**
     * Constructs the Home pane and wires up all UI sections:
     * greeting/title/clock header, workflow-launch buttons,
     * drag-and-drop zone, tip of the day, model asset check,
     * and recent images list.
     *
     * <p>
     * The constructor also:
     * </p>
     * <ul>
     *   <li>Starts a 1 Hz Swing {@link Timer} that updates the clock
     *       and greeting label using {@link #updateClockAndGreeting()}.</li>
     *   <li>Installs a drop target on the "drop zone" panel that accepts files
     *       and opens them in ImageJ via {@link #openImage(File)}.</li>
     *   <li>Configures the "Help & Support" button to navigate to
     *       {@link UI.panes.Tools.HelpAndSupportPane}.</li>
     * </ul>
     *
     * @param navigator
     *        The global navigation helper. Used to switch to other
     *        workflow panes (Neuron workflow, Multichannel workflow, etc.)
     *        when the user clicks the quick action buttons.
     */
    public HomePane(Navigator navigator) {
        super(new BorderLayout(10,10));
        this.navigator = navigator;

        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        //  Top bar: greeting | title | clock+help
        JPanel top = new JPanel(new BorderLayout(8,8));
        top.setOpaque(false);

        greetingLabel.setFont(greetingLabel.getFont().deriveFont(Font.BOLD, 16f));
        top.add(greetingLabel, BorderLayout.WEST);

        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 20f));
        top.add(titleLabel, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        clockLabel.setFont(clockLabel.getFont().deriveFont(13f));
        right.add(clockLabel);

        JButton helpBtn = new JButton("Help & Support");
        helpBtn.addActionListener(e -> navigator.show(HelpAndSupportPane.Name));
        right.add(helpBtn);

        top.add(right, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        updateClockAndGreeting();
        clockTimer = new Timer(1000, e -> updateClockAndGreeting());
        clockTimer.setInitialDelay(0);
        clockTimer.start();

        //  Main column
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setOpaque(false);

        col.add(section("Welcome", welcomeButtons()));
        col.add(section("Open or drop", dropZone()));
        col.add(section("Tip of the day", tipOfDay()));
        col.add(section("Models & assets", modelsAndAssets()));
        col.add(sectionFill("Recent images", recents()));

        JScrollPane scroll = new JScrollPane(
                col,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    /**
     * Builds a titled "section" panel with a border and header text.
     *
     * <p>
     * Each section in the scrolling column (e.g. "Welcome", "Tip of the day")
     * is made with this helper. The returned panel:
     * </p>
     * <ul>
     *   <li>Gets an etched titled border using the provided {@code title}.</li>
     *   <li>Contains {@code content} in the center.</li>
     *   <li>Has its maximum width constrained via {@link #normalizeSectionWidth(JComponent)}
     *       so the overall column layout stays neat and doesn't stretch horizontally
     *       on wide displays.</li>
     * </ul>
     *
     * @param title
     *        Human-readable title for the border of this section.
     *
     * @param content
     *        The main component for that section, e.g. a row of buttons
     *        or a recent-files list.
     *
     * @return a {@link JPanel} with a titled border wrapping the given content.
     */
    private JComponent section(String title, JComponent content) {
        JPanel box = new JPanel(new BorderLayout());
        box.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP
        ));
        box.add(content, BorderLayout.CENTER);
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        normalizeSectionWidth(box);
        return box;
    }

    /**
     * Creates the "Welcome" row containing quick-start workflow buttons.
     *
     * <p>
     * This row includes:
     * </p>
     * <ul>
     *   <li>"Analyse Neurons" → navigates to {@link UI.panes.SettingPanes.NeuronWorkflowPane}.</li>
     *   <li>"Multichannel Workflow" → navigates to {@link UI.panes.SettingPanes.MultichannelPane}.</li>
     *   <li>"Multi-Channel (No Hu)" → navigates to {@link UI.panes.SettingPanes.MultiChannelNoHuPane}.</li>
     * </ul>
     *
     * <p>
     * It's intended as the primary "do work now" entry point for users.
     * </p>
     *
     * @return a left-aligned {@link JPanel} containing a short intro label
     *         and three navigation buttons.
     */
    private JComponent welcomeButtons() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        row.setOpaque(false);

        JLabel lead = new JLabel("Let’s analyse some images.");
        lead.setBorder(new EmptyBorder(0,0,0,6));
        row.add(lead);

        JButton neurons = new JButton("Analyse Neurons");
        neurons.addActionListener(e -> navigator.show(NeuronWorkflowPane.Name));
        row.add(neurons);

        JButton multiplex = new JButton("Multichannel Workflow");
        multiplex.addActionListener(e -> navigator.show(MultichannelPane.Name));
        row.add(multiplex);

        JButton noHu = new JButton("Multi-Channel (No Hu)");
        noHu.addActionListener(e -> navigator.show(MultiChannelNoHuPane.Name));
        row.add(noHu);

        return row;
    }

    /**
     * Creates the drag-and-drop "Open or drop" panel.
     *
     * <p>
     * This panel advertises "Drop an image file here (.tif, .lif, .czi, etc.)".
     * It has two behaviors:
     * </p>
     * <ul>
     *   <li><b>Drag & drop:</b> When the user drops a file, we accept the drop,
     *       take the first file, and call {@link #openImage(File)} to open it in ImageJ.</li>
     *   <li><b>Click:</b> Clicking the panel opens a {@link JFileChooser}
     *       via {@link #openFileDialogAndOpen()}.</li>
     * </ul>
     *
     * <p>
     * The panel uses a custom dashed rounded border ({@link DashBorder})
     * to visually communicate "drop zone".
     * </p>
     *
     * @return a {@link JPanel} that supports both file drop and click-to-open.
     */
    private JComponent dropZone() {
        JPanel drop = new JPanel(new BorderLayout());
        drop.setBorder(new DashBorder(UIManager.getColor("Label.disabledForeground")));
        drop.setBackground(UIManager.getColor("Panel.background"));
        drop.setOpaque(true);

        JLabel hint = new JLabel("Drop an image file here (.tif, .lif, .czi, etc.) to preview in ImageJ",
                SwingConstants.CENTER);
        hint.setBorder(new EmptyBorder(18, 8, 18, 8));
        drop.add(hint, BorderLayout.CENTER);

        // DnD
        new DropTarget(drop, new DropTargetAdapter() {
            @Override public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) openImage(files.get(0));
                } catch (Exception ignore) { }
            }
        });
        // Click to open
        drop.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { openFileDialogAndOpen(); }
        });

        return drop;
    }

    /**
     * Generates the "Tip of the day" section contents.
     *
     * <p>
     * A small pool of hard-coded usage tips is defined inline.
     * We pick the tip based on the current day-of-year modulo the tip list length,
     * so the user sees a rotating tip without needing persistence.
     * </p>
     *
     * <p>
     * The tip text is returned as an HTML {@link JLabel} that wraps nicely.
     * </p>
     *
     * @return a {@link JComponent} (specifically, a {@link JLabel}) containing
     *         the chosen tip with light padding.
     */
    private JComponent tipOfDay() {
        String[] tips = new String[]{
                "Use <b>Preview</b> in Neuron workflow to verify channel order before a long run.",
                "Try <b>Ganglia ▸ DEEPIMAGEJ</b> first, then analyse further with <b>IMPORT ROI</b>.",
                "Enable <b>Require microns calibration</b> to avoid mis-scaled size filters.",
                "Keep models under Fiji/Models so every pane can find them."
        };
        int idx = java.time.LocalDate.now().getDayOfYear() % tips.length;
        JLabel tip = new JLabel("<html><body style='width:100%; padding:2px 0;'>" + tips[idx] + "</body></html>");
        tip.setBorder(new EmptyBorder(6,6,6,6));
        return tip;
    }

    /**
     * Builds the "Models & assets" section UI.
     *
     * <p>
     * This section inspects the ImageJ/Fiji {@code models/} directory and reports
     * whether required model artifacts are present:
     * </p>
     * <ul>
     *   <li>Hu StarDist model ZIP (primary or fallback name).</li>
     *   <li>Neuron subtype StarDist model ZIP.</li>
     *   <li>Ganglia model (a DeepImageJ model folder).</li>
     * </ul>
     *
     * <p>
     * For each asset, a row is added indicating "Found — &lt;filename&gt;" or
     * "Missing — check &lt;Fiji&gt;/models".
     * </p>
     *
     * <p>
     * At the bottom, there's also a button to open the models folder in the OS
     * file explorer using {@link java.awt.Desktop}.
     * </p>
     *
     * @return a {@link JPanel} laid out with {@link GridBagLayout}, one row per asset,
     *         plus an "Open models folder…" button.
     */
    private JComponent modelsAndAssets() {
        JPanel g = new JPanel(new GridBagLayout());
        g.setOpaque(false);
        GridBagConstraints l = new GridBagConstraints();
        GridBagConstraints r = new GridBagConstraints();
        l.gridx=0; l.gridy=0; l.anchor=GridBagConstraints.WEST; l.insets=new Insets(3,8,3,8);
        r.gridx=1; r.gridy=0; r.anchor=GridBagConstraints.WEST; r.insets=new Insets(3,8,3,8);

        // Where models live
        File modelsDir = new File(IJ.getDirectory("imagej"), "models");

        addModelRow(g, l, r, "Hu StarDist model",
                firstExisting(modelsDir, HU_ZIP_PRIMARY, HU_ZIP_FALLBACK));

        addModelRow(g, l, r, "Ganglia model (DeepImageJ folder)",
                new File(modelsDir, GANGLIA_DIJ_FOLDER).exists()
                        ? new File(modelsDir, GANGLIA_DIJ_FOLDER).getName()
                        : null);

        addModelRow(g, l, r, "Subtype StarDist model",
                new File(modelsDir, SUBTYPE_ZIP).isFile() ? SUBTYPE_ZIP : null);

        // Open models folder (handy)
        r.gridy++; r.gridwidth = 2;
        JButton openModels = new JButton("Open models folder…");
        openModels.addActionListener(e -> {
            try { Desktop.getDesktop().open(modelsDir); } catch (Throwable ignore) { }
        });
        g.add(openModels, r);

        return g;
    }

    /**
     * Utility used by {@link #modelsAndAssets()} to add one "asset status" row
     * to the given {@link JPanel} grid.
     *
     * <p>
     * The row consists of:
     * </p>
     * <ul>
     *   <li>A left label describing the model ("Hu StarDist model").</li>
     *   <li>A right label describing whether it's found under the ImageJ models dir.</li>
     * </ul>
     *
     * @param g
     *        The parent panel using {@link GridBagLayout} to which we append this row.
     *
     * @param l
     *        The {@link GridBagConstraints} for the label column (mutated:
     *        its {@code gridy} is incremented after insertion so callers can reuse it).
     *
     * @param r
     *        The {@link GridBagConstraints} for the value/status column (mutated:
     *        its {@code gridy} is incremented after insertion so callers can reuse it).
     *
     * @param label
     *        Human-readable description of the asset being checked
     *        (e.g. "Hu StarDist model").
     *
     * @param foundName
     *        The filename or folder name if found, or {@code null} if missing.
     *        When {@code null}, the UI will display a "Missing — check &lt;Fiji&gt;/models"
     *        message instead.
     */
    private static void addModelRow(JPanel g, GridBagConstraints l, GridBagConstraints r,
                                    String label, String foundName) {
        JLabel left = new JLabel(label + ":");
        g.add(left, l);

        String text = (foundName != null)
                ? "Found — " + foundName
                : "Missing — check <Fiji>/models";
        JLabel right = new JLabel(text);
        g.add(right, r);

        l.gridy++; r.gridy++;
    }

    /**
     * Returns the first filename (from {@code names}) that exists in {@code dir}.
     *
     * <p>
     * This is used to resolve multiple possible model ZIP names.
     * For example, if we expect one of
     * {@code 2D_enteric_neuron_v4_1.zip} or {@code 2D_enteric_neuron_v4.zip},
     * this helper will return whichever actually exists.
     * </p>
     *
     * @param dir
     *        Directory to check (typically the {@code models/} folder under ImageJ/Fiji).
     *
     * @param names
     *        Candidate filenames to test, in priority order. {@code null} entries are ignored.
     *
     * @return The first candidate filename that exists as a regular file in {@code dir},
     *         or {@code null} if none are found.
     */
    private static String firstExisting(File dir, String... names) {
        for (String n : names) {
            if (n == null) continue;
            File f = new File(dir, n);
            if (f.isFile()) return f.getName();
        }
        return null;
    }

    /**
     * Creates the "Recent images" section.
     *
     * <p>
     * This section reads the persisted MRU list (see {@link #loadRecents()})
     * and renders it as a simple {@link JList}. Clicking an entry immediately
     * attempts to re-open that file via {@link #openImage(File)}.
     * </p>
     *
     * <p>
     * If there are no recent items, a placeholder label is shown instead.
     * </p>
     *
     * <p>
     * The list is styled to blend into the Home pane background and does not
     * live in its own scroll pane; instead we size it to show up to
     * {@code RECENTS_MAX} items.
     * </p>
     *
     * @return a {@link JPanel} containing either the recents list or
     *         a "No recent images yet" message.
     */
    private JComponent recents() {
        loadRecents();

        JPanel p = new JPanel(new BorderLayout());
        Color bg = HomePane.this.getBackground();
        p.setOpaque(true);
        p.setBackground(bg);

        if (recentModel.isEmpty()) {
            JLabel empty = new JLabel("No recent images yet. Open or drop a file to see it here.");
            empty.setBorder(new EmptyBorder(6,6,6,6));
            p.add(empty, BorderLayout.NORTH);
            return p;
        }

        // List styling (no scroll pane)
        recentList.setOpaque(true);
        recentList.setBackground(bg);
        recentList.setForeground(UIManager.getColor("Label.foreground"));
        recentList.setSelectionBackground(UIManager.getColor("List.selectionBackground"));
        recentList.setSelectionForeground(UIManager.getColor("List.selectionForeground"));
        recentList.setVisibleRowCount(RECENTS_MAX);        // advertise height for BoxLayout

        // Ensure unselected rows use the same dark background
        recentList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (!isSelected) {
                    lbl.setOpaque(true);
                    lbl.setBackground(list.getBackground());
                    lbl.setForeground(list.getForeground());
                }
                return lbl;
            }
        });

        recentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recentList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && recentList.getSelectedValue() != null) {
                openImage(new File(recentList.getSelectedValue()));
            }
        });


        p.add(recentList, BorderLayout.CENTER);
        return p;
    }




    /**
     * Updates the live clock label and greeting label.
     *
     * <p>
     * Called once per second by {@link #clockTimer}. The clock is formatted
     * using {@link #clockFmt}, and the greeting ("Good morning", etc.) is
     * chosen by inspecting the current hour of day.
     * </p>
     *
     * <p>
     * This method is lightweight and safe to call on the EDT.
     * </p>
     */
    private void updateClockAndGreeting() {
        LocalDateTime now = LocalDateTime.now();
        clockLabel.setText(clockFmt.format(now));
        int h = now.getHour();
        String part = (h < 5) ? "Good night"
                : (h < 12) ? "Good morning"
                : (h < 17) ? "Good afternoon"
                : "Good evening";
        greetingLabel.setText(part);
    }

    /**
     * Opens a {@link JFileChooser} to let the user pick an image file,
     * then calls {@link #openImage(File)} on the chosen file.
     *
     * <p>
     * This is used when the user clicks the drop zone instead of dragging-and-dropping.
     * </p>
     *
     * <p>
     * If the chooser is canceled, nothing happens.
     * </p>
     */
    private void openFileDialogAndOpen() {
        JFileChooser ch = new JFileChooser();
        ch.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (ch.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openImage(ch.getSelectedFile());
        }
    }

    /**
     * Opens the given image file in ImageJ/Fiji asynchronously, and records it
     * in the recent-images list.
     *
     * <p>
     * We spawn a short-lived {@link SwingWorker} to call {@link ij.IJ#open(String)}
     * so we don't block the EDT while Bio-Formats loads large microscopy data.
     * </p>
     *
     * <p>
     * After we queue the open, we also call {@link #rememberRecent(File)} to
     * update recents persistence and refresh the visible recents list.
     * </p>
     *
     * @param f
     *        The image file to open. If {@code null} or does not exist on disk,
     *        the call is ignored.
     */
    private void openImage(File f) {
        if (f == null || !f.exists()) return;
        new SwingWorker<Void,Void>() {
            @Override protected Void doInBackground() {
                try { IJ.open(f.getAbsolutePath()); } catch (Throwable ignore) {}
                return null;
            }
        }.execute();
        rememberRecent(f);
    }

    /**
     * Adds (or moves) the given file path into the MRU "recent images" list
     * stored in {@link Preferences}, trimming the list to {@code RECENTS_MAX}.
     *
     * <p>
     * Behavior:
     * </p>
     * <ul>
     *   <li>We read the existing pipe-separated list from preferences.</li>
     *   <li>We ensure the new file path is the most recent entry
     *       (removing duplicates if needed).</li>
     *   <li>We cap the list size to {@code RECENTS_MAX}.</li>
     *   <li>We write the updated list back to preferences.</li>
     *   <li>We then call {@link #loadRecents()} to refresh the on-screen list model.</li>
     * </ul>
     *
     * <p>
     * Errors (e.g. {@link SecurityException}) are silently ignored.
     * </p>
     *
     * @param f
     *        The file that was just opened.
     */
    private void rememberRecent(File f) {
        try {
            String existing = prefs.get(RECENTS_KEY, "");
            java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
            if (!existing.isEmpty()) for (String s : existing.split("\\|")) if (!s.isEmpty()) set.add(s);
            set.remove(f.getAbsolutePath()); // move to end
            set.add(f.getAbsolutePath());
            while (set.size() > RECENTS_MAX) set.remove(set.iterator().next());
            prefs.put(RECENTS_KEY, String.join("|", set));
            loadRecents();
        } catch (Throwable ignore) { }
    }

    /**
     * Loads the MRU "recent images" list from {@link Preferences}
     * into {@link #recentModel} for display in the UI.
     *
     * <p>
     * The stored format is a pipe-separated list of absolute file paths,
     * with oldest-first order in the preference string. We rebuild
     * {@link #recentModel} in newest-first order for display.
     * </p>
     *
     * <p>
     * If nothing is stored, the list model simply ends up empty.
     * </p>
     */
    private void loadRecents() {
        recentModel.clear();
        String existing = prefs.get(RECENTS_KEY, "");
        if (!existing.isEmpty()) {
            String[] items = existing.split("\\|");
            for (int i = items.length - 1; i >= 0; i--) { // newest first
                if (!items[i].isEmpty()) recentModel.addElement(items[i]);
            }
        }
    }

    /**
     * Like {@link #section(String, JComponent)}, but allows the section
     * to stretch vertically and "fill" remaining space in the scroll column.
     *
     * <p>
     * Used specifically for the "Recent images" block so that,
     * when there's extra vertical room, that section grows instead of
     * leaving a large blank gap under the other fixed-height sections.
     * </p>
     *
     * <p>
     * The returned panel:
     * </p>
     * <ul>
     *   <li>Has a titled etched border with the given {@code title}.</li>
     *   <li>Is marked opaque and sized with {@code MAX_VALUE} height so
     *       BoxLayout can allocate extra space to it.</li>
     * </ul>
     *
     * @param title
     *        Title to show in the border (e.g. "Recent images").
     *
     * @param content
     *        Content component to embed in the center of the bordered panel.
     *
     * @return a {@link JPanel} intended to expand vertically in the BoxLayout.
     */
    private JComponent sectionFill(String title, JComponent content) {
        JPanel box = new JPanel(new BorderLayout());
        box.setOpaque(true);
        box.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title,
                TitledBorder.LEFT, TitledBorder.TOP));
        box.add(content, BorderLayout.CENTER);
        box.setAlignmentX(Component.LEFT_ALIGNMENT);

        // allow this section to grow vertically to take leftover space
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return box;
    }


    /**
     * Constrains a section panel's maximum width so it doesn't sprawl horizontally.
     *
     * <p>
     * BoxLayout (Y_AXIS) can otherwise let components expand to huge widths on
     * wide monitors. Here we set:
     * </p>
     * <ul>
     *   <li>{@code setAlignmentX(LEFT_ALIGNMENT)} so BoxLayout left-aligns it, and</li>
     *   <li>{@code setMaximumSize(...)} with the preferred height but unbounded width,
     *       which effectively "locks in" the natural height while preventing the
     *       section from vertically stretching oddly.</li>
     * </ul>
     *
     * @param c
     *        The section container whose sizing hints should be normalized.
     */
    private static void normalizeSectionWidth(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension pref = c.getPreferredSize();
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
    }

    /**
     * A simple rounded, dashed border used for the drag-and-drop "Open or drop" box.
     *
     * <p>
     * This {@link LineBorder} subclass overrides {@link #paintBorder(Component, Graphics, int, int, int, int)}
     * to draw a rounded rectangle with a dashed stroke that matches the current theme.
     * </p>
     *
     * <p>
     * It's intentionally lightweight and purely cosmetic.
     * </p>
     */
    static class DashBorder extends LineBorder {
        /**
         * Creates a dashed rounded border with the given (or fallback) color.
         *
         * <p>
         * If {@code color} is {@code null}, a neutral grey is used. The border
         * thickness is fixed at ~1px, and it's configured as rounded to soften
         * the appearance of the drop zone.
         * </p>
         *
         * @param color
         *        The stroke color to use for the dashed outline, or {@code null}
         *        to use a default grey.
         */
        public DashBorder(Color color) { super(color != null ? color : new Color(140,140,140), 1, true); }
        /**
         * Paints the dashed, rounded rectangle border around the target component.
         *
         * <p>
         * We:
         * </p>
         * <ul>
         *   <li>Enable antialiasing for smoother corners.</li>
         *   <li>Use a custom {@link BasicStroke} with a dash pattern to get the
         *       "drop zone" look.</li>
         *   <li>Draw a slightly inset rounded rect so the stroke isn't clipped
         *       by the component edges.</li>
         * </ul>
         *
         * @param c
         *        The component being bordered.
         *
         * @param g
         *        The graphics context to draw into (will be temporarily cast/cloned
         *        to {@link Graphics2D} for stroke control).
         *
         * @param x
         *        X position of the border painting area.
         *
         * @param y
         *        Y position of the border painting area.
         *
         * @param w
         *        Width of the border painting area.
         *
         * @param h
         *        Height of the border painting area.
         */
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float[] dash = {6f, 6f};
            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, dash, 0f));
            g2.setColor(lineColor);
            g2.drawRoundRect(x+2, y+2, w-5, h-5, 10, 10);
            g2.dispose();
        }
    }
}
