package UI.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;   // Java 8 friendly
import java.util.Properties;

/**
 * Lightweight persistence for tuning / calibration "knobs"
 * chosen by the user (e.g. best Hu probability, best ganglia expansion µm).
 *
 * <p>
 * Backed by a {@code settings.properties} file under {@code ~/.gat/} by default,
 * or by {@code GAT_SETTINGS} env var if that is set.
 * </p>
 *
 * <p>
 * Keys (all optional Doubles):
 * </p>
 * <ul>
 *   <li>{@code huTrainingRescale}</li>
 *   <li>{@code huProb}, {@code huNms}</li>
 *   <li>{@code subtypeProb}, {@code subtypeNms}</li>
 *   <li>{@code gangliaExpandUm}</li>
 *   <li>{@code overlapFrac}</li>
 * </ul>
 *
 * <p>
 * Instances are created via {@link #loadOrDefaults()}, which resolves the path
 * and then calls {@link #load()} to populate fields if the file exists.
 * </p>
 */
public final class GatSettings {
    // Java 8: use Paths.get(...)
    private static final Path DEFAULT_PATH = Paths.get(
            System.getProperty("user.home"), ".gat", "settings.properties");

    public Double huTrainingRescale;   // e.g. 1.00
    public Double huProb;              // 0..1
    public Double huNms;               // 0..1
    public Double subtypeProb;         // 0..1
    public Double subtypeNms;          // 0..1
    public Double gangliaExpandUm;     // microns
    public Double overlapFrac;         // 0..1

    private final Path path;

    private GatSettings(Path path) { this.path = path; }

    /**
     * Create or load a {@code GatSettings} instance.
     *
     * <p>
     * We pick the settings file path by:
     * </p>
     * <ol>
     *   <li>If {@code GAT_SETTINGS} env var is set and non-blank, use that.</li>
     *   <li>Otherwise, fall back to {@code ~/.gat/settings.properties}.</li>
     * </ol>
     *
     * <p>
     * We then construct a new {@code GatSettings} bound to that path and call
     * {@link #load()} on it. If the file doesn't exist, {@link #load()} is harmless,
     * so you'll just get an object with {@code null} fields.
     * </p>
     *
     * @return A {@code GatSettings} instance whose fields (huProb, etc.)
     *         are populated if a settings file was found.
     */
    public static GatSettings loadOrDefaults() {
        String env = System.getenv("GAT_SETTINGS");
        // Java 8 friendly “blank” check
        boolean hasEnv = env != null && !env.trim().isEmpty();
        Path p = hasEnv ? new File(env).toPath() : DEFAULT_PATH;

        GatSettings s = new GatSettings(p);
        s.load(); // harmless if file missing
        return s;
    }

    /**
     * Export the current tuning values to a {@link Properties} object.
     *
     * <p>
     * Null fields are skipped. These properties can then be written to disk
     * or embedded into a run config.
     * </p>
     *
     * @return A new {@link Properties} containing any non-null fields such as
     *         {@code huTrainingRescale}, {@code huProb}, etc.
     */
    public Properties toProperties() {
        Properties pr = new Properties();
        put(pr, "huTrainingRescale", huTrainingRescale);
        put(pr, "huProb",            huProb);
        put(pr, "huNms",             huNms);
        put(pr, "subtypeProb",       subtypeProb);
        put(pr, "subtypeNms",        subtypeNms);
        put(pr, "gangliaExpandUm",   gangliaExpandUm);
        put(pr, "overlapFrac",       overlapFrac);
        return pr;
    }

    /**
     * Save the current tuning values to a specific destination path.
     *
     * <p>
     * We ensure that the parent directories exist, then serialize
     * the {@link #toProperties()} result as UTF-8 text with a short header comment.
     * </p>
     *
     * @param dest
     *        Path to write to. Often this will just be our internal {@code path},
     *        but callers can also export to another file.
     *
     * @throws IOException
     *         If we fail to create parent directories or write the file.
     */
    public void saveTo(Path dest) throws IOException {
        if (dest.getParent() != null) Files.createDirectories(dest.getParent());
        try (Writer w = new OutputStreamWriter(
                Files.newOutputStream(dest.toFile().toPath()), StandardCharsets.UTF_8)) {
            toProperties().store(w, "GAT tuning config");
        }
    }

    /**
     * Convenience setter for {@link #huTrainingRescale}.
     *
     * @param v
     *        The chosen rescale factor to match training pixel size (e.g. 1.00).
     */
    public void setHuTrainingRescale(double v){ this.huTrainingRescale = v; }
    /**
     * Convenience setter for {@link #huProb}.
     *
     * @param v
     *        The chosen neuron/Hu probability threshold (0..1).
     */
    public void setHuProb(double v){ this.huProb = v; }
    /**
     * Convenience setter for {@link #subtypeProb}.
     *
     * @param v
     *        The chosen subtype probability threshold (0..1).
     */
    public void setSubtypeProb(double v){ this.subtypeProb = v; }
    /**
     * Convenience setter for {@link #gangliaExpandUm}.
     *
     * @param v
     *        The spatial expansion distance in microns to dilate neuron ROIs
     *        when estimating ganglia.
     */
    public void setGangliaExpandUm(double v){ this.gangliaExpandUm = v; }
    /**
     * Convenience setter for {@link #overlapFrac}.
     *
     * @param v
     *        The fraction-of-overlap (NMS-style) to keep in tuning steps,
     *        0..1.
     */
    public void setOverlapFrac(double v){ this.overlapFrac = v; }

    /**
     * Internal: attempt to populate this instance's fields by loading
     * the on-disk {@code settings.properties}.
     *
     * <p>
     * If the file doesn't exist or can't be read, this silently returns.
     * If keys are present, we parse them into Doubles and assign to:
     * huTrainingRescale, huProb, huNms, subtypeProb, subtypeNms,
     * gangliaExpandUm, overlapFrac.
     * </p>
     *
     * <p>
     * This method is called from {@link #loadOrDefaults()} immediately
     * after constructing the {@code GatSettings}.
     * </p>
     */
    private void load() {
        if (path == null || !Files.isRegularFile(path)) return;
        Properties pr = new Properties();
        try (Reader r = new InputStreamReader(
                Files.newInputStream(path.toFile().toPath()), StandardCharsets.UTF_8)) {
            pr.load(r);
        } catch (IOException ignored) { }
        huTrainingRescale = getDouble(pr, "huTrainingRescale");
        huProb            = getDouble(pr, "huProb");
        huNms             = getDouble(pr, "huNms");
        subtypeProb       = getDouble(pr, "subtypeProb");
        subtypeNms        = getDouble(pr, "subtypeNms");
        gangliaExpandUm   = getDouble(pr, "gangliaExpandUm");
        overlapFrac       = getDouble(pr, "overlapFrac");
    }

    /**
     * Helper for {@link #toProperties()}:
     * put a Double into a {@link Properties} only if it's non-null.
     *
     * @param pr
     *        Target {@link Properties}.
     *
     * @param k
     *        Key name.
     *
     * @param v
     *        Value to set. If {@code null}, nothing is added.
     */
    private static void put(Properties pr, String k, Double v){
        if (v != null) pr.setProperty(k, Double.toString(v));
    }

    /**
     * Parse a Double from a {@link Properties} map.
     *
     * @param pr
     *        Source properties.
     *
     * @param k
     *        Key to read.
     *
     * @return The parsed Double value, or {@code null} on missing/invalid.
     */
    private static Double getDouble(Properties pr, String k){
        String s = pr.getProperty(k);
        if (s == null) return null;
        try { return Double.valueOf(s.trim()); } catch (Exception ignored){ return null; }
    }
}
