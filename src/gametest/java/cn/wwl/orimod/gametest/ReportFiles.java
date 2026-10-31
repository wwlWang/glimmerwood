package cn.wwl.orimod.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reports must work in a fresh export, including caller-supplied nested output directories. */
final class ReportFiles {
    private ReportFiles() {}
    static Path output(Path path) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        return path;
    }
}
