package UI.util;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;
/**
 * Utility methods for reading, writing, and validating small .cfg-style workflow
 * config files, plus simple "save config" / "load config" flows with a file chooser.
 *
 * <p>
 * A "config" in this context is just a {@link java.util.Properties} file on disk.
 * We use them to persist panel settings between runs or to share tuning results.
 * </p>
 *
 * <p>
 * This class is static-only.
 * </p>
 */
public final class ConfigIO {
    private ConfigIO() {}

    /**
     * Read a {@link Properties} file from disk.
     *
     * @param f
     *        The .cfg (or .properties) file to read.
     *
     * @return A {@link Properties} populated with all keys/values in {@code f}.
     *
     * @throws IOException
     *         If the file cannot be opened or read.
     */
    public static Properties read(File f) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f.toPath())) {
            p.load(in);
        }
        return p;
    }

    /**
     * Write a {@link Properties} object to disk.
     *
     * <p>
     * The file is written using {@link Properties#store(OutputStream, String)},
     * and we add a simple header comment ("Enteric analysis settings").
     * </p>
     *
     * @param p
     *        The properties to serialize.
     *
     * @param f
     *        Destination file. If it already exists, it will be overwritten.
     *
     * @throws IOException
     *         If writing fails for any reason.
     */
    public static void write(Properties p, File f) throws IOException {
        try (OutputStream out = Files.newOutputStream(f.toPath())) {
            p.store(out, "Enteric analysis settings");
        }
    }

    /**
     * Convenience: true if {@code p} has a non-null value for key {@code k}.
     *
     * @param p
     *        Properties bag to check.
     *
     * @param k
     *        Property key.
     *
     * @return {@code true} if {@code p.getProperty(k)} is not {@code null};
     *         {@code false} otherwise.
     */
    public static boolean has(Properties p, String k) {
        return p.getProperty(k) != null;
    }

    /**
     * Store a boolean value in the {@link Properties}.
     *
     * @param p
     *        Target properties.
     *
     * @param k
     *        Key to write.
     *
     * @param v
     *        Boolean value to write (saved as "true"/"false").
     */
    public static void putBool(Properties p, String k, boolean v) { p.setProperty(k, Boolean.toString(v)); }
    /**
     * Store an integer value in the {@link Properties}.
     *
     * @param p
     *        Target properties.
     *
     * @param k
     *        Key to write.
     *
     * @param v
     *        Integer value to write (saved via {@code Integer.toString(v)}).
     */
    public static void putInt (Properties p, String k, int v)    { p.setProperty(k, Integer.toString(v)); }
    /**
     * Store a double value in the {@link Properties}.
     *
     * @param p
     *        Target properties.
     *
     * @param k
     *        Key to write.
     *
     * @param v
     *        Double value to write (saved via {@code Double.toString(v)}).
     */
    public static void putDbl (Properties p, String k, double v) { p.setProperty(k, Double.toString(v)); }
    /**
     * Store a string in the {@link Properties}, if not {@code null}.
     *
     * @param p
     *        Target properties.
     *
     * @param k
     *        Key to write.
     *
     * @param v
     *        String value. If {@code v} is {@code null}, nothing is written.
     */
    public static void putStr (Properties p, String k, String v) { if (v != null) p.setProperty(k, v); }

    /**
     * Get a boolean property with a default.
     *
     * @param p
     *        Properties to read from.
     *
     * @param k
     *        Key to fetch.
     *
     * @param def
     *        Default value to return if absent or unparsable.
     *
     * @return Parsed boolean or {@code def} if missing.
     */
    public static boolean getBool(Properties p, String k, boolean def) {
        String s = p.getProperty(k); return (s==null) ? def : Boolean.parseBoolean(s);
    }

    /**
     * Get an integer property with a default.
     *
     * @param p
     *        Properties to read from.
     *
     * @param k
     *        Key to fetch.
     *
     * @param def
     *        Default if missing or malformed.
     *
     * @return Parsed int, or {@code def} if missing / invalid.
     */
    public static int getInt(Properties p, String k, int def) {
        String s = p.getProperty(k);
        try { return (s==null) ? def : Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    /**
     * Get a double property with a default.
     *
     * @param p
     *        Properties to read from.
     *
     * @param k
     *        Key to fetch.
     *
     * @param def
     *        Default if missing or malformed.
     *
     * @return Parsed double, or {@code def} if missing / invalid.
     */
    public static double getDbl(Properties p, String k, double def) {
        String s = p.getProperty(k);
        try { return (s==null) ? def : Double.parseDouble(s); } catch (Exception e) { return def; }
    }
    /**
     * Get a string property with a default.
     *
     * @param p
     *        Properties to read from.
     *
     * @param k
     *        Key to fetch.
     *
     * @param def
     *        Default string if the property is not present.
     *
     * @return The stored string, or {@code def} if missing.
     */
    public static String getStr(Properties p, String k, String def) {
        String s = p.getProperty(k); return (s==null) ? def : s;
    }

    /**
     * "Save config" UI flow.
     *
     * <p>
     * 1. Prompts the user for a .cfg output file using a {@link JFileChooser}.
     * 2. Builds a {@link Properties} snapshot via {@code toProps.get()}.
     * 3. Stamps the given {@code workflowTag} (and {@code cfgVersion=1} if not present).
     * 4. Writes it to disk using {@link #write(Properties, File)}.
     * </p>
     *
     * <p>
     * If the user cancels the file chooser, nothing happens.
     * If anything fails, an error dialog is shown.
     * </p>
     *
     * @param parent
     *        Parent component for dialogs (may be {@code null}).
     *
     * @param workflowTag
     *        Short string identifying which workflow/pane this config belongs to
     *        (e.g. "neuron", "multichannel"). Used later by {@link #loadConfig}
     *        to warn if the config is loaded in the wrong pane.
     *
     * @param toProps
     *        Supplier that returns a {@link Properties} filled with the current UI state.
     *        If it returns {@code null}, an empty {@link Properties} is used.
     */
    public static void saveConfig(Component parent,
                                  String workflowTag,
                                  Supplier<Properties> toProps) {
        File f = pickConfigFile(parent, true);
        if (f == null) return;

        try {
            Properties p = toProps.get();
            if (p == null) p = new Properties();
            // stamp workflow + version
            p.setProperty("workflow", workflowTag);
            p.putIfAbsent("cfgVersion", "1");
            write(p, f);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                    "Save failed: " + ex.getMessage(),
                    "Save config", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * "Load config" UI flow.
     *
     * <p>
     * 1. Prompts the user to pick a .cfg file via {@link JFileChooser}.
     * 2. Reads it with {@link #read(File)}.
     * 3. Checks that its {@code workflow} property matches
     *    {@code expectedWorkflowTag} OR equals "test".
     *    <br>
     *    (Configs generated by tuning tools are stamped as workflow "test"
     *     so they're allowed everywhere.)
     * </p>
     *
     * <p>
     * If the workflow tag doesn't match, we show a warning dialog and
     * <b>do not</b> apply anything. Otherwise we call {@code applyProps.accept(cfg)}.
     * </p>
     *
     * @param parent
     *        Parent component for modal dialogs (may be {@code null}).
     *
     * @param expectedWorkflowTag
     *        The workflow tag that this pane expects (for safety).
     *
     * @param applyProps
     *        Callback to push the loaded {@link Properties} values back
     *        into the calling panel's UI.
     */
    public static void loadConfig(Component parent,
                                  String expectedWorkflowTag,
                                  Consumer<Properties> applyProps) {
        File f = pickConfigFile(parent, false);
        if (f == null) return;

        try {
            Properties cfg = read(f);
            String wf = String.valueOf(cfg.getOrDefault("workflow", ""));
            boolean ok = expectedWorkflowTag.equals(wf) || "test".equalsIgnoreCase(wf);

            if (!ok){
                JOptionPane.showMessageDialog(
                        parent,
                        "This config was created for \n "
                                + (wf.isEmpty() ? "a different/older workflow"
                                : ("the '" + wf + "' workflow"))
                                + ".\n Please open it from\n the matching tab.",
                        "Wrong workflow",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            Dimension old = (Dimension) UIManager.get("OptionPane.minimumSize");
            UIManager.put("OptionPane.minimumSize", new Dimension(400, 150));
            try {
                JOptionPane.showMessageDialog(parent,
                        "Loaded configuration from:\n" + f.getAbsolutePath(),
                        "Config loaded",
                        JOptionPane.INFORMATION_MESSAGE);
            } finally {
                UIManager.put("OptionPane.minimumSize", old);
            }


            applyProps.accept(cfg);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                    "Load failed: " + ex.getMessage(),
                    "Load config", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Internal helper for {@link #saveConfig} / {@link #loadConfig}.
     *
     * <p>
     * Shows a {@link JFileChooser} with a {@code .cfg} file filter:
     * </p>
     * <ul>
     *   <li>If {@code save} is true, shows "Save" dialog and appends ".cfg"
     *       if the user didn't include an extension.</li>
     *   <li>If {@code save} is false, shows "Open" dialog and just returns
     *       the chosen file.</li>
     * </ul>
     *
     * <p>
     * Returns {@code null} if the chooser is canceled.
     * </p>
     *
     * @param parent
     *        Parent component for modality (can be {@code null}).
     *
     * @param save
     *        {@code true} for "Save config", {@code false} for "Load config".
     *
     * @return The file chosen by the user (possibly with ".cfg" auto-added),
     *         or {@code null} if the user canceled.
     */

    private static File pickConfigFile(Component parent, boolean save) {
        JFileChooser ch = new JFileChooser();
        ch.setDialogTitle(save ? "Save config" : "Load config");
        ch.setFileFilter(new FileNameExtensionFilter("Config (*.cfg)", "cfg"));

        int rv = save ? ch.showSaveDialog(parent) : ch.showOpenDialog(parent);
        if (rv != JFileChooser.APPROVE_OPTION) return null;

        File f = ch.getSelectedFile();
        if (save) {
            String name = f.getName().toLowerCase();
            if (!name.endsWith(".cfg")) {
                f = new File(f.getParentFile(), f.getName() + ".cfg");
            }
        }
        return f;
    }
}
