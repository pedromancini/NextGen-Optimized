package com.nextgen.optimizer.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

/**
 * Executes PowerShell commands via {@link ProcessBuilder}.
 * <p>
 * Three execution modes are provided:
 * <ul>
 *   <li>{@link #execute(String)} — asynchronous, returns a {@link CompletableFuture}.</li>
 *   <li>{@link #executeSync(String)} — blocking with a 30-second timeout.</li>
 *   <li>{@link #executeElevated(String)} — launches a new elevated PowerShell process.</li>
 * </ul>
 * All methods swallow exceptions and return an empty string on failure so that
 * callers never need to handle {@code null} or unexpected errors.
 */
public class PowerShellService {

    private static final long DEFAULT_TIMEOUT_SECONDS = 30;
    private static final String POWERSHELL_EXE = "powershell.exe";

    public static final class CommandResult {
        private final String output;
        private final int exitCode;
        private final boolean timedOut;

        private CommandResult(String output, int exitCode, boolean timedOut) {
            this.output = output == null ? "" : output;
            this.exitCode = exitCode;
            this.timedOut = timedOut;
        }

        public String output() {
            return output;
        }

        public int exitCode() {
            return exitCode;
        }

        public boolean timedOut() {
            return timedOut;
        }

        public boolean isSuccess() {
            return !timedOut && exitCode == 0;
        }
    }

    /**
     * Runs a PowerShell command asynchronously.
     *
     * @param command the PowerShell command / script to execute.
     * @return a future that resolves to the combined stdout output, or {@code ""} on failure.
     */
    public CompletableFuture<String> execute(String command) {
        return CompletableFuture.supplyAsync(() -> executeSync(command));
    }

    /**
     * Runs a PowerShell command synchronously, blocking for up to 30 seconds.
     *
     * @param command the PowerShell command / script to execute.
     * @return the combined stdout output, or {@code ""} on failure / timeout.
     */
    public String executeSync(String command) {
        return executeResult(command).output();
    }

    public CommandResult executeResult(String command) {
        return executeResult(command, DEFAULT_TIMEOUT_SECONDS);
    }

    public CommandResult executeResult(String command, long timeoutSeconds) {
        if (command == null || command.isBlank()) {
            return new CommandResult("", 0, false);
        }

        try {
            String silentCommand = "$ProgressPreference = 'SilentlyContinue'; $WarningPreference = 'SilentlyContinue'; $ErrorActionPreference = 'SilentlyContinue'; " + command;
            ProcessBuilder pb = new ProcessBuilder(
                    POWERSHELL_EXE,
                    "-NoProfile",
                    "-NonInteractive",
                    "-NoLogo",
                    "-WindowStyle", "Hidden",
                    "-ExecutionPolicy", "Bypass",
                    "-Command", silentCommand
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readProcessOutput(process));

            boolean finished = process.waitFor(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
            if (!finished) {
                System.err.println("[PowerShellService] Command timed out after " +
                        timeoutSeconds + "s: " + truncate(command, 120));
                process.destroyForcibly();
                return new CommandResult(readFutureOutput(outputFuture), -1, true);
            }

            int exitCode = process.exitValue();
            String output = readFutureOutput(outputFuture);
            if (exitCode != 0) {
                System.err.println("[PowerShellService] Command exited with code " + exitCode +
                        ": " + truncate(command, 120));
            }

            return new CommandResult(output, exitCode, false);

        } catch (IOException e) {
            System.err.println("[PowerShellService] IO error executing command: " + e.getMessage());
            return new CommandResult("", -1, false);
        } catch (InterruptedException e) {
            System.err.println("[PowerShellService] Command interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return new CommandResult("", -1, false);
        } catch (Exception e) {
            System.err.println("[PowerShellService] Unexpected error: " + e.getMessage());
            return new CommandResult("", -1, false);
        }
    }

    /**
     * Runs a PowerShell command in an elevated (admin) process using
     * {@code Start-Process -Verb RunAs}.
     * <p>
     * <b>Note:</b> This will trigger a UAC prompt on the user's machine.
     * The output of the elevated process is captured via a temporary redirect
     * so the caller can still read results.
     *
     * @param command the PowerShell command / script to execute with elevation.
     * @return a future that resolves to the stdout output, or {@code ""} on failure.
     */
    public CompletableFuture<String> executeElevated(String command) {
        return CompletableFuture.supplyAsync(() -> {
            if (command == null || command.isBlank()) {
                return "";
            }

            try {
                // Create a temp file to capture output from the elevated process
                java.nio.file.Path tempOutput = java.nio.file.Files.createTempFile("nextgen_ps_", ".txt");
                tempOutput.toFile().deleteOnExit();

                // Wrap the original command so its output is redirected to the temp file
                String wrappedCommand = String.format(
                        "try { %s } catch { $_.Exception.Message } | Out-File -FilePath '%s' -Encoding UTF8",
                        command, tempOutput.toAbsolutePath()
                );

                // Escape single quotes for the outer Start-Process argument
                String escapedCommand = wrappedCommand.replace("'", "''");

                String elevatedScript = String.format(
                        "Start-Process -FilePath '%s' -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-Command','%s' " +
                        "-Verb RunAs -Wait -WindowStyle Hidden",
                        POWERSHELL_EXE, escapedCommand
                );

                ProcessBuilder pb = new ProcessBuilder(
                        POWERSHELL_EXE,
                        "-NoProfile",
                        "-ExecutionPolicy", "Bypass",
                        "-Command", elevatedScript
                );
                pb.redirectErrorStream(true);

                Process process = pb.start();
                boolean finished = process.waitFor(60, TimeUnit.SECONDS);
                if (!finished) {
                    System.err.println("[PowerShellService] Elevated command timed out.");
                    process.destroyForcibly();
                    return "";
                }

                // Read the captured output
                if (java.nio.file.Files.exists(tempOutput)) {
                    String result = java.nio.file.Files.readString(tempOutput, StandardCharsets.UTF_8).trim();
                    java.nio.file.Files.deleteIfExists(tempOutput);
                    return result;
                }
                return "";

            } catch (IOException e) {
                System.err.println("[PowerShellService] IO error in elevated command: " + e.getMessage());
                return "";
            } catch (InterruptedException e) {
                System.err.println("[PowerShellService] Elevated command interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
                return "";
            } catch (Exception e) {
                System.err.println("[PowerShellService] Unexpected error in elevated command: " + e.getMessage());
                return "";
            }
        });
    }

    /**
     * Truncates a string for logging purposes.
     */
    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private static String readProcessOutput(Process process) {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.replace("\u0007", "");
                if (output.length() > 0) {
                    output.append(System.lineSeparator());
                }
                output.append(line);
            }
        } catch (IOException e) {
            System.err.println("[PowerShellService] Error reading command output: " + e.getMessage());
        }
        return output.toString();
    }

    private static String readFutureOutput(CompletableFuture<String> outputFuture) {
        try {
            return outputFuture.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException | TimeoutException e) {
            return "";
        }
    }
}
