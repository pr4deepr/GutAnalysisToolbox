package services.merge;


import java.nio.file.Path;

/**
 * Matches the macro behavior:
 * - If the CSV file's parent directory is named "spatial_analysis",
 *   use the parent-of-parent directory name as the label (the image folder).
 * - Otherwise use the immediate parent directory name.
 */
public class DefaultLabelStrategy implements LabelStrategy {

    @Override
    public String labelFor(Path root, Path csvFile) {
        Path parent = csvFile.getParent();
        if (parent == null) return root.getFileName().toString();

        String parentName = parent.getFileName() != null ? parent.getFileName().toString() : parent.toString();
        if ("spatial_analysis".equalsIgnoreCase(parentName)) {
            Path imageFolder = parent.getParent();
            if (imageFolder != null && imageFolder.getFileName() != null) {
                return imageFolder.getFileName().toString();
            }
        }
        return parent.getFileName() != null ? parent.getFileName().toString() : parent.toString();
    }
}
