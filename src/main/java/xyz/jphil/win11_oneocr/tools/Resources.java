package xyz.jphil.win11_oneocr.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Classpath text resources, so templates and stylesheets live in files rather than in Java. */
public final class Resources {

    private Resources() {}

    public static String text(String path) {
        try (var in = Resources.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IOException("resource not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
