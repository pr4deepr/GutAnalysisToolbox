package UI.util;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ConfigIO {
    private ConfigIO() {}

    public static Properties read(File f) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f.toPath())) {
            p.load(in);
        }
        return p;
    }

    public static void write(Properties p, File f) throws IOException {
        try (OutputStream out = Files.newOutputStream(f.toPath())) {
            p.store(out, "Enteric analysis settings");
        }
    }

    public static boolean has(Properties p, String k) {
        return p.getProperty(k) != null;
    }

    public static void putBool(Properties p, String k, boolean v) { p.setProperty(k, Boolean.toString(v)); }
    public static void putInt (Properties p, String k, int v)    { p.setProperty(k, Integer.toString(v)); }
    public static void putDbl (Properties p, String k, double v) { p.setProperty(k, Double.toString(v)); }
    public static void putStr (Properties p, String k, String v) { if (v != null) p.setProperty(k, v); }

    public static boolean getBool(Properties p, String k, boolean def) {
        String s = p.getProperty(k); return (s==null) ? def : Boolean.parseBoolean(s);
    }
    public static int getInt(Properties p, String k, int def) {
        String s = p.getProperty(k);
        try { return (s==null) ? def : Integer.parseInt(s); } catch (Exception e) { return def; }
    }
    public static double getDbl(Properties p, String k, double def) {
        String s = p.getProperty(k);
        try { return (s==null) ? def : Double.parseDouble(s); } catch (Exception e) { return def; }
    }
    public static String getStr(Properties p, String k, String def) {
        String s = p.getProperty(k); return (s==null) ? def : s;
    }

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
