package com.nextgen.optimizer;

/**
 * Non-JavaFX launcher class to avoid module system issues.
 * This is the actual main class that starts the application.
 */
public class Launcher {
    public static void main(String[] args) {
        // OSHI logs every unsupported sensor query; keep the console quiet.
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "error");
        App.main(args);
    }
}
