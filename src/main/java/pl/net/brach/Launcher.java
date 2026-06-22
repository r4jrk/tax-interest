package pl.net.brach;

/**
 * Entry point for the packaged (non-modular) application.
 *
 * <p>A class that extends {@code javafx.application.Application} cannot be the main class of a
 * classpath launch — the JVM rejects it with "JavaFX runtime components are missing". This thin
 * launcher does not extend {@code Application}, so jpackage/fat-jar launches work.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        TaxInterest.main(args);
    }
}
