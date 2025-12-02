package services.merge;

import java.nio.file.Path;

/**
 * Strategy that returns the "Experiment" label for a given CSV file.
 */
@FunctionalInterface
public interface LabelStrategy {
    /**
     * @param root Root directory of the merge (results root).
     * @param csvFile Path to the CSV file being merged.
     * @return The label string to prepend as the "Experiment" column value.
     */
    String labelFor(Path root, Path csvFile);
}
