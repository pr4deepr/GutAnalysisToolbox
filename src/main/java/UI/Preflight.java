package UI;

import ij.IJ;
import ij.Menus;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

import ij.WindowManager;
import net.haesleinhuepf.clij2.CLIJ2;

/**
 * Environment / dependency preflight for GAT.
 *
 * <p>
 * Before the UI is allowed to open, we run a series of checks to make sure
 * the user's Fiji installation is set up in a way that won't immediately
 * explode:
 * </p>
 *
 * <ul>
 *   <li>Confirm DeepImageJ has been initialised (its engine files are present).</li>
 *   <li>Confirm expected model files exist under {@code Fiji/models} (e.g. neuron + subtype StarDist zips).</li>
 *   <li>Check for required ImageJ commands / plugins (CLIJ2, DeepImageJ, MorphoLibJ, etc.).</li>
 *   <li>Log basic system info (heap size, GPU name if available).</li>
 *   <li>Handle a "first run" sentinel so we can warn the user the very first time.</li>
 * </ul>
 *
 * <p>
 * All logging goes to ImageJ's Log window via {@link ij.IJ#log(String)}.
 * We will also pop up {@link JOptionPane} dialogs if something critical is missing.
 * </p>
 *
 * <p>
 * The class is static-only.
 * </p>
 */
public final class Preflight {
    private Preflight(){}

    /**
     * Run the full preflight procedure and decide if it's safe to continue.
     *
     * <p>
     * Workflow:
     * </p>
     * <ol>
     *   <li>Open (or create) a log header and remember if the Log window was
     *       already showing.</li>
     *   <li>Check DeepImageJ initialization and perform first-run setup
     *       ({@link #firstRunAndDeepImageJ()}).</li>
     *   <li>Log system info / memory / GPU via {@link #reportSystem()}.</li>
     *   <li>Check that key model assets exist in {@code Fiji/models} via
     *       {@link #checkModels(String, String,String)}.</li>
     *   <li>Check that required plugins/commands are available via
     *       {@link #checkPlugins()}.</li>
     *   <li>If everything is OK, optionally close the Log window again if
     *       we opened it just for this run.</li>
     * </ol>
     *
     * @param expectedNeuronModel
     *        Filename (just the basename) of the neuron StarDist model we expect
     *        under {@code Fiji/models}. May be {@code null} if you only want
     *        a listing of what's there.
     *
     * @param expectedSubtypeModel
     *        Filename of the subtype StarDist model we expect. May be {@code null}.
     *
     *        @param expectedGangliaModel
     *        Filename of the ganglia model we expect. May be {@code null}.
     *
     * @return {@code true} if all checks pass and it is safe to continue
     *         launching the UI, {@code false} if we detected a blocking problem
     *         and already warned the user.
     */
    public static boolean runAll(String expectedNeuronModel, String expectedSubtypeModel, String expectedGangliaModel) {
        boolean logWasOpen = isLogOpen();
        logHeader();


        // First-run sentinel + DeepImageJ engines check
        if (!firstRunAndDeepImageJ()) return false;

        //  System / GPU / RAM report (best-effort)
        reportSystem();

        //  Check models in <Fiji>/models (no macros or IJM tables)
        if (!checkModels(expectedNeuronModel, expectedSubtypeModel,expectedGangliaModel)) return false;

        // Check required commands/plugins are present
        if (!checkPlugins()) return false;

        IJ.log("****** DONE – environment looks good. ******");

        if (!logWasOpen) {
            closeLogWindowIfOpen();
        }
        return true;
    }

    /**
     * First-run initialisation and DeepImageJ readiness check.
     *
     * <p>
     * Steps:
     * </p>
     * <ol>
     *   <li>Figure out the Fiji install directory via {@code IJ.getDirectory("imagej")}.</li>
     *   <li>Ensure a "sentinel" file exists in {@code Fiji/scripts/GAT/Tools/commands}.
     *       If it doesn't, we consider this a first run and show a small welcome /
     *       DeepImageJ notice, then create it.</li>
     *   <li>Check that the {@code engines/} folder exists in the Fiji directory.
     *       This is how DeepImageJ indicates its runtime engines have been
     *       downloaded/installed.</li>
     * </ol>
     *
     * <p>
     * If DeepImageJ has not been initialized, we log guidance and show the user
     * a dialog explaining how to run DeepImageJ once to trigger engine download.
     * In that case we return {@code false} so the main UI doesn't open yet.
     * </p>
     *
     * @return {@code true} if DeepImageJ appears ready and we were able to create
     *         the sentinel if needed; {@code false} if something critical is missing.
     */
    private static boolean firstRunAndDeepImageJ() {
        String fijiDir = IJ.getDirectory("imagej"); // ends with separator on IJ1
        if (fijiDir == null) {
            JOptionPane.showMessageDialog(null, "Could not determine Fiji directory.", "GAT", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        File commandsDir = new File(new File(new File(new File(fijiDir, "scripts"), "GAT"), "Tools"), "commands");
        commandsDir.mkdirs();
        File sentinel = new File(commandsDir, "gat_init_deepimagej_check_file");

        if (!sentinel.exists()) {
            // First time
            JOptionPane.showMessageDialog(null,
                    "Thanks for installing GAT.\nWe're going to verify DeepImageJ and required components.",
                    "GAT – First time", JOptionPane.INFORMATION_MESSAGE);
            try {
                writeText(sentinel, "file_check_deepimagej\n");
            } catch (IOException e) {
                IJ.log("Could not write first-run sentinel: " + e.getMessage());
            }
        }

        // DeepImageJ initialised? (engines folder)
        File engines = new File(fijiDir, "engines");
        if (!engines.isDirectory()) {
            String msg =
                    "DeepImageJ needs to be initialized.\n\n" +
                            "Please run: Plugins -> DeepImageJ  \n -> DeepImageJ Run\n" +
                            "This will download the \n required engine files.\n\n" +
                            "When finished, start GAT again.";
            IJ.log(msg);
            JOptionPane.showMessageDialog(null, msg, "GAT – DeepImageJ not initialized", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        IJ.log("DeepImageJ already initialized.");
        return true;
    }


    /**
     * Log basic system information to ImageJ's Log window.
     *
     * <p>
     * What we record:
     * </p>
     * <ul>
     *   <li>Current timestamp.</li>
     *   <li>Maximum / total / free JVM heap memory in GB, plus a note if the heap
     *       is relatively small (&lt; ~20 GB).</li>
     *   <li>Best-effort GPU/accelerator info via CLIJ2 (OpenCL device name),
     *       if available.</li>
     * </ul>
     *
     * <p>
     * Any failures are caught and logged as non-fatal.
     * </p>
     */
    private static void reportSystem() {
        try {
            IJ.log("****** System Config ******");
            IJ.log("Date: " + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));

            // RAM (JVM)
            long max = Runtime.getRuntime().maxMemory();
            long total = Runtime.getRuntime().totalMemory();
            long free = Runtime.getRuntime().freeMemory();
            IJ.log(String.format(java.util.Locale.US,
                    "JVM memory (GB): max=%.1f  total=%.1f  free=%.1f",
                    max / 1e9, total / 1e9, free / 1e9));
            if (max < 20L * 1024L * 1024L * 1024L) {
                IJ.log("Note: Fiji JVM has < ~20 GB max heap. For large images, consider 32 GB+.");
            }

            // GPU via CLIJ2 (best-effort)
            try {
                CLIJ2 clij2 = CLIJ2.getInstance();
                String gpuName = clij2.getGPUName();
                IJ.log("OpenCL Device: " + gpuName);
                clij2.clear();
            } catch (Throwable t) {
                IJ.log("CLIJ2 GPU info not available: " + t.getMessage());
            }
            IJ.log("***************************");
        } catch (Throwable t) {
            IJ.log("System report failed: " + t.getMessage());
        }
    }

    /**
     * Check that required model assets exist under {@code Fiji/models}.
     *
     * <p>
     * We:
     * </p>
     * <ul>
     *   <li>Resolve the {@code models/} directory under the current Fiji install.</li>
     *   <li>If {@code expectedNeuronModel} and/or {@code expectedSubtypeModel}
     *       are non-empty, verify those specific entries exist (file or folder),
     *       logging "OK" or "Missing" for each.</li>
     *   <li>If either expected model name is {@code null}/{@code ""}, we instead
     *       list candidate model entries (e.g. {@code *.zip} or folders containing
     *       DeepImageJ descriptors like {@code bioimage.io}).</li>
     * </ul>
     *
     * <p>
     * If anything critical is missing, we show a blocking {@link JOptionPane}
     * telling the user to install/copy the missing model(s) and return {@code false}.
     * Otherwise we return {@code true}.
     * </p>
     *
     * @param expectedNeuronModel
     *        Basename of the neuron model ZIP we require, or {@code null} to skip strict checking.
     *
     * @param expectedSubtypeModel
     *        Basename of the subtype model ZIP we require, or {@code null} to skip strict checking.
     *
     * @return {@code true} if models folder exists and required models are present;
     *         {@code false} (with user warning) if something is missing.
     */
    private static boolean checkModels(String expectedNeuronModel, String expectedSubtypeModel, String expectedGangliaModel) {
        String fijiDir = IJ.getDirectory("imagej");
        File modelsDir = new File(fijiDir, "models");
        if (!modelsDir.isDirectory()) {
            warnStop("Cannot find Fiji models folder:\n" + modelsDir.getAbsolutePath());
            return false;
        }

        IJ.log("****** Checking models in: " + modelsDir.getAbsolutePath() + " ******");

        // Collect available files (top-level only)
        String[] files = modelsDir.list();
        if (files == null) files = new String[0];
        Arrays.sort(files, String.CASE_INSENSITIVE_ORDER);

        // If expectations provided, verify presence
        boolean ok = true;
        if (expectedNeuronModel != null && !expectedNeuronModel.trim().isEmpty()) {
            File f = new File(modelsDir, expectedNeuronModel);
            if (!f.exists()) {
                IJ.log("Missing neuron model: " + expectedNeuronModel);
                ok = false;
            } else {
                IJ.log("Neuron model OK: " + expectedNeuronModel);
            }
        }
        if (expectedSubtypeModel != null && !expectedSubtypeModel.trim().isEmpty()) {
            File f = new File(modelsDir, expectedSubtypeModel);
            if (!f.exists()) {
                IJ.log("Missing subtype model: " + expectedSubtypeModel);
                ok = false;
            } else {
                IJ.log("Subtype model OK: " + expectedSubtypeModel);
            }
        }
        if (expectedGangliaModel != null && !expectedGangliaModel.trim().isEmpty()){
            File f = new File(modelsDir,expectedGangliaModel);
            if (!f.exists()){
                IJ.log("Missing Ganglia Model: " + expectedGangliaModel);
                ok = false;
            }else{
                IJ.log("Ganglia Model OK: " + expectedGangliaModel);
            }
        }

        // If no expectation provided, list helpful candidates
        if ((expectedNeuronModel == null || expectedNeuronModel.isEmpty())
                || (expectedSubtypeModel == null || expectedSubtypeModel.isEmpty())) {
            IJ.log("Models found:");
            for (String name : files) {
                if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")
                        || name.toLowerCase(java.util.Locale.ROOT).contains("bioimage.io")) {
                    IJ.log(" - " + name);
                }
            }
        }

        if (!ok) {
            JOptionPane.showMessageDialog(null,
                    "Cannot find one or more model files in Fiji/models.\n" +
                            "See the Log window for details and available models.",
                    "GAT – Models missing", JOptionPane.ERROR_MESSAGE);
        }
        return ok;
    }


    /**
     * Verify that required ImageJ / Fiji commands are installed and callable.
     *
     * <p>
     * We build a small map of "command name → how to install if missing"
     * covering DeepImageJ, CLIJ2, MorphoLibJ, StackReg, etc. We then compare
     * those command names against {@link ij.Menus#getCommands()}.
     * </p>
     *
     * <p>
     * For each required command:
     * </p>
     * <ul>
     *   <li>If it's found, we log "OK!".</li>
     *   <li>If not found, we log "Missing: ..." plus a hint which update site
     *       to enable (e.g. DeepImageJ, IJPB-plugins, CLIJ2, BIG-EPFL).</li>
     * </ul>
     *
     * <p>
     * After scanning, if any commands were missing, we pop up an error dialog
     * telling the user to review the Log for details and return {@code false}.
     * Otherwise we return {@code true}.
     * </p>
     *
     * @return {@code true} if all required commands/plugins seem available;
     *         {@code false} if something important is missing.
     */
    private static boolean checkPlugins() {
        IJ.log("****** Checking required plugins/commands ******");

        // command name -> guidance if missing
        LinkedHashMap<String,String> required = new LinkedHashMap<>();
        required.put("DeepImageJ Run", "Add the DeepImageJ update site: https://sites.imagej.net/DeepImageJ/");
        required.put("Command From Macro", "Enable StarDist + CSBDeep update sites.");
        // MorpholibJ: some installs expose 'Area Opening' or 'Size Opening 2D/3D'
        required.put("Area Opening", "Enable the IJPB-plugins update site (MorphoLibJ).");
        required.put("Size Opening 2D/3D", "Enable the IJPB-plugins update site (MorphoLibJ).");
        // CLIJ/CLIJ2
        required.put("CLIJ Macro Extensions", "Enable update sites for CLIJ and CLIJ2: https://clij.github.io/clij2-docs/installationInFiji");
        required.put("CLIJ2 Macro Extensions", "Enable update sites for CLIJ and CLIJ2: https://clij.github.io/clij2-docs/installationInFiji");
        // StackReg (BIG-EPFL)
        required.put("StackReg", "Enable the BIG-EPFL update site.");
        // PTBIOP (optional but recommended)
        required.put("Label Map to ROIs", "Enable the PTBIOP update site: https://biop.epfl.ch/Fiji-Update/");

        // Feature-specific commands. These are only needed for certain workflows,
        // so a missing one is a warning (logged) rather than a launch blocker.
        LinkedHashMap<String,String> optional = new LinkedHashMap<>();
        // Template Matching (calcium imaging alignment). Not in Fiji's list of
        // update sites, so it must be added manually as an unlisted site.
        optional.put("Align slices in stack...",
                "Calcium imaging alignment needs the Template Matching plugin. In "
                        + "Manage update sites, add the unlisted site: https://sites.imagej.net/Template_Matching/");

        @SuppressWarnings("rawtypes")
        Map commands = Menus.getCommands(); // command -> class name
        Set<String> keys = new HashSet<>();
        for (Object k : commands.keySet()) keys.add(String.valueOf(k));

        boolean missingAny = false;

        // helper: test either exact key or “starts with” for quirky names
        for (Map.Entry<String,String> e : required.entrySet()) {
            String want = e.getKey();
            boolean present = hasCommand(keys, want);
            if (!present) {
                missingAny = true;
                IJ.log("Missing: " + want + "  → " + e.getValue());
            } else {
                IJ.log(want + " ... OK!");
            }
        }

        // Feature-specific commands: log guidance but never block launch.
        for (Map.Entry<String,String> e : optional.entrySet()) {
            String want = e.getKey();
            if (!hasCommand(keys, want)) {
                IJ.log("Optional (not installed): " + want + "  → " + e.getValue());
            } else {
                IJ.log(want + " ... OK!");
            }
        }

        IJ.log("***********************************************");

        if (missingAny) {
            JOptionPane.showMessageDialog(null,
                    "Some required plugins are missing.\nSee the Log window for which ones and how to enable them.",
                    "GAT – Plugins missing", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    /**
     * Helper used by {@link #checkPlugins()} to test if a required command is present.
     *
     * <p>
     * We try:
     * </p>
     * <ul>
     *   <li>Exact match ({@code keys.contains(want)}).</li>
     *   <li>Case-insensitive comparisons.</li>
     *   <li>Prefix / substring matches to handle slight naming differences,
     *       spacing, or version suffixes in Fiji menus.</li>
     * </ul>
     *
     * @param keys
     *        All available command names from {@link ij.Menus#getCommands()}.
     *
     * @param want
     *        The "friendly" name we expect (e.g. "DeepImageJ Run").
     *
     * @return {@code true} if we find a good-enough match; {@code false} otherwise.
     */
    private static boolean hasCommand(Set<String> keys, String want) {
        if (keys.contains(want)) return true;
        // Try forgiving matches for names with/without spaces / suffixes
        String w = want.trim().toLowerCase(java.util.Locale.ROOT);
        for (String k : keys) {
            String kk = k.trim().toLowerCase(java.util.Locale.ROOT);
            if (kk.equals(w)) return true;
            if (kk.startsWith(w)) return true;
            if (kk.contains(w)) return true;
        }
        return false;
    }


    /**
     * Log a standard header block into the ImageJ Log window.
     *
     * <p>
     * Called at the start of {@link #runAll(String, String)} so it's easy
     * for users to see where the preflight section begins.
     * </p>
     */
    private static void logHeader() {
        IJ.log("===============================================");
        IJ.log("GAT – Environment check");
        IJ.log("===============================================");
    }

    /**
     * Convenience to both log and pop up a blocking error dialog, then continue
     * returning {@code false} to abort launch.
     *
     * <p>
     * Used when we detect something fatal like "cannot find Fiji/models" or
     * DeepImageJ engines not initialized.
     * </p>
     *
     * @param msg
     *        Human-readable explanation of what's wrong and what to do.
     */
    private static void warnStop(String msg) {
        IJ.log(msg);
        JOptionPane.showMessageDialog(null, msg, "GAT – Check failed", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Write a short text file, creating parent directories if needed.
     *
     * <p>
     * Used to drop a "first run sentinel" file so we know we already warned
     * the user about DeepImageJ initialization.
     * </p>
     *
     * @param f
     *        Destination file.
     *
     * @param text
     *        Contents to write (UTF-8).
     *
     * @throws java.io.IOException
     *         If the directory cannot be created or the file cannot be written.
     */
    private static void writeText(File f, String text) throws IOException {
        f.getParentFile().mkdirs();
        try (PrintWriter pw = new PrintWriter(f, "UTF-8")) {
            pw.print(text);
        }
    }

    /**
     * Check whether the ImageJ "Log" window is currently visible.
     *
     * <p>
     * We use this so we can politely close the Log window at the end of preflight
     * if we opened it just for this check, but leave it alone if the user
     * already had it open.
     * </p>
     *
     * @return {@code true} if a frame titled "Log" is showing, {@code false} otherwise.
     */
    private static boolean isLogOpen() {
        java.awt.Frame f = WindowManager.getFrame("Log");
        return f != null && f.isShowing();
    }


    /**
     * Close the ImageJ "Log" window if it's open.
     *
     * <p>
     * We call this at the end of {@link #runAll(String, String)} only if we
     * determined the log was not already open before we started. That way
     * we don't rudely close a window the user was intentionally using.
     * </p>
     */
    private static void closeLogWindowIfOpen() {
        java.awt.Frame f = WindowManager.getFrame("Log");
        if (f != null) {
            // dispose() closes the TextWindow cleanly
            f.dispose();
        }
    }
}
