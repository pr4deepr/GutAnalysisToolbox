package services.multiplex.config;

import java.io.File;

/**
 * Immutable configuration object for the multiplex registration workflow.
 * <p>
 * This class captures all parameters that were previously provided interactively
 * to the ImageJ macro. It is built via the nested {@link Builder}, which
 * validates required inputs and supplies reasonable defaults.
 * </p>
 *
 * <h3>Fields and their meaning</h3>
 * <ul>
 *   <li>{@code imageFolder} — root directory containing input .tif/.tiff files
 *       for all multiplexing rounds.</li>
 *   <li>{@code commonMarker} — case-insensitive marker name (e.g. "Hu", "DAPI")
 *       that must be present in each round and is used as the registration reference.</li>
 *   <li>{@code multiplexRounds} — number of multiplexing rounds (Layer1..LayerN)
 *       to process.</li>
 *   <li>{@code layerKeyword} — keyword used in filenames to distinguish rounds,
 *       e.g. "Layer" or "Round". Normalized to lowercase with spaces removed.</li>
 *   <li>{@code saveFolder} — base directory where outputs are written. A
 *       "Results" subfolder will be created inside. If not set explicitly,
 *       defaults to {@code imageFolder}.</li>
 *   <li>{@code fineTuneParams} — whether to allow user-specified overrides
 *       for feature-matching parameters (SIFT/MOPS).</li>
 *   <li>{@code minimalInlierRatio} — tuning parameter for SIFT matching;
 *       minimum proportion of inliers for accepting a transformation.</li>
 *   <li>{@code stepsPerScaleOctave} — tuning parameter for SIFT matching;
 *       number of steps per scale octave. Higher = more features, slower.</li>
 * </ul>
 *
 * <p>
 * Instances are immutable and safe to share. All values are normalized
 * (e.g. {@code commonMarker} and {@code layerKeyword} lowercased).
 * </p>
 *
 * <h3>Usage example</h3>
 *
 * <pre>{@code
 * MultiplexConfig cfg = new MultiplexConfig.Builder()
 *     .imageFolder(new File("/path/to/images"))
 *     .commonMarker("Hu")
 *     .multiplexRounds(3)
 *     .layerKeyword("Layer")
 *     .saveFolder(new File("/path/to/save"))
 *     .fineTuneParams(true)
 *     .minimalInlierRatio(0.6)
 *     .stepsPerScaleOctave(5)
 *     .build();
 * }</pre>
 */
public final class MultiplexConfig {

    private final File imageFolder;
    private final String commonMarker;
    private final int multiplexRounds;
    private final String layerKeyword;
    private final File saveFolder;
    private final boolean fineTuneParams;
    private final double minimalInlierRatio;
    private final int stepsPerScaleOctave;

    private MultiplexConfig(Builder b) {
        this.imageFolder = b.imageFolder;
        this.commonMarker = b.commonMarker.toLowerCase().trim();
        this.multiplexRounds = b.multiplexRounds;
        this.layerKeyword = b.layerKeyword.toLowerCase().replace(" ", "");
        this.saveFolder = (b.saveFolder != null) ? b.saveFolder : b.imageFolder;
        this.fineTuneParams = b.fineTuneParams;
        this.minimalInlierRatio = b.minimalInlierRatio;
        this.stepsPerScaleOctave = b.stepsPerScaleOctave;
    }

    /** @return root folder containing input image files. */
    public File imageFolder() { return imageFolder; }

    /** @return normalized (lowercased, trimmed) common marker string. */
    public String commonMarker() { return commonMarker; }

    /** @return number of multiplexing rounds to process. */
    public int multiplexRounds() { return multiplexRounds; }

    /** @return normalized keyword used to distinguish rounds in filenames. */
    public String layerKeyword() { return layerKeyword; }

    /** @return save folder where a "Results" subdirectory will be created. */
    public File saveFolder() { return saveFolder; }

    /** @return true if user opted to fine-tune matching parameters. */
    public boolean fineTuneParams() { return fineTuneParams; }

    /** @return minimum inlier ratio for SIFT correspondence acceptance. */
    public double minimalInlierRatio() { return minimalInlierRatio; }

    /** @return number of steps per scale octave for SIFT feature extraction. */
    public int stepsPerScaleOctave() { return stepsPerScaleOctave; }

    /**
     * Builder for {@link MultiplexConfig}.
     * <p>
     * Provides a fluent API for setting configuration options. Some
     * parameters have defaults:
     * <ul>
     *   <li>{@code commonMarker} = "hu"</li>
     *   <li>{@code multiplexRounds} = 2</li>
     *   <li>{@code layerKeyword} = "layer"</li>
     *   <li>{@code saveFolder} = {@code imageFolder}</li>
     *   <li>{@code fineTuneParams} = false</li>
     *   <li>{@code minimalInlierRatio} = 0.50</li>
     *   <li>{@code stepsPerScaleOctave} = 3</li>
     * </ul>
     *
     * <p>
     * Validation is performed in {@link #build()}:
     * <ul>
     *   <li>{@code imageFolder} must be a non-null existing directory.</li>
     *   <li>{@code multiplexRounds} must be &gt;= 1.</li>
     * </ul>
     */
    public static class Builder {
        private File imageFolder;
        private String commonMarker = "hu";
        private int multiplexRounds = 2;
        private String layerKeyword = "layer";
        private File saveFolder;
        private boolean fineTuneParams = false;
        private double minimalInlierRatio = 0.50;
        private int stepsPerScaleOctave = 3;

        /** @param f directory containing .tif/.tiff files. */
        public Builder imageFolder(File f) { this.imageFolder = f; return this; }

        /** @param s marker substring to search for in filenames. */
        public Builder commonMarker(String s) { this.commonMarker = s; return this; }

        /** @param n number of multiplexing rounds (must be &gt;= 1). */
        public Builder multiplexRounds(int n) { this.multiplexRounds = n; return this; }

        /** @param s keyword used in filenames to distinguish rounds. */
        public Builder layerKeyword(String s) { this.layerKeyword = s; return this; }

        /** @param f directory to save outputs; defaults to imageFolder. */
        public Builder saveFolder(File f) { this.saveFolder = f; return this; }

        /** @param b true to allow fine-tuning of feature-matching parameters. */
        public Builder fineTuneParams(boolean b) { this.fineTuneParams = b; return this; }

        /** @param d minimum inlier ratio for SIFT (0–1). */
        public Builder minimalInlierRatio(double d) { this.minimalInlierRatio = d; return this; }

        /** @param i number of steps per scale octave for SIFT. */
        public Builder stepsPerScaleOctave(int i) { this.stepsPerScaleOctave = i; return this; }

        /**
         * Validate parameters and build an immutable {@link MultiplexConfig}.
         *
         * @return new MultiplexConfig instance
         * @throws IllegalArgumentException if imageFolder is null/invalid or multiplexRounds &lt; 1
         */
        public MultiplexConfig build() {
            if (imageFolder == null || !imageFolder.isDirectory()) {
                throw new IllegalArgumentException("imageFolder must be a directory");
            }
            if (multiplexRounds < 1) {
                throw new IllegalArgumentException("multiplexRounds must be >= 1");
            }
            return new MultiplexConfig(this);
        }
    }
}
