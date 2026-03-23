package com.wpanther.pdfsigning.infrastructure.adapter.out.pdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility for temp file lifecycle management during PDF processing.
 *
 * <p>Intentionally omits {@code deleteOnExit()} registration: in high-throughput
 * scenarios each call would add a reference to the JVM shutdown hook registry
 * that is never removed, causing a slow memory leak.</p>
 */
final class PdfTempFiles {

    private static final Logger log = LoggerFactory.getLogger(PdfTempFiles.class);

    private PdfTempFiles() {}

    @FunctionalInterface
    interface ThrowingFunction<T> {
        T apply(Path path) throws IOException;
    }

    /**
     * Creates a temp file, passes it to {@code fn}, then deletes it in a finally block.
     *
     * <p>Delete failures are logged but suppressed so they never mask the primary exception
     * thrown by {@code fn}.</p>
     *
     * @param prefix filename prefix passed to {@link Files#createTempFile}
     * @param fn     work to perform with the temp file path
     * @return whatever {@code fn} returns
     * @throws IOException if {@code fn} throws, or if the temp file cannot be created
     */
    static <T> T withTempFile(String prefix, ThrowingFunction<T> fn) throws IOException {
        Path tmp = Files.createTempFile(prefix, ".pdf");
        try {
            return fn.apply(tmp);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException e) {
                log.warn("Failed to delete temp file {}: {}", tmp, e.getMessage());
            }
        }
    }
}
