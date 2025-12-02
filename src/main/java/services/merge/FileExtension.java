package services.merge;

import java.nio.file.Path;

public enum FileExtension {
    CSV("csv");
//    TXT("txt"),
//    TSV("tsv");

    private final String ext; // without dot

    FileExtension(String ext) { this.ext = ext.toLowerCase(); }

    public String ext() { return ext; }

    /** Returns true if the path's file name ends with this extension (case-insensitive). */
    public boolean matches(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        return name.endsWith("." + ext);
    }

    /** Build "<base>.<ext>" */
    public String withExt(String base) {
        return base + "." + ext;
    }
}
