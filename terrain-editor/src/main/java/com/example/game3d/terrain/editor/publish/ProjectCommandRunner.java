package com.example.game3d.terrain.editor.publish;

import com.example.game3d.terrain.editor.persistence.ProjectSettings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Runs a Gradle project command with streamed output and cooperative process cancellation. */
public final class ProjectCommandRunner {
    public CommandResult run(
            Path projectRoot,
            List<String> gradleArguments,
            Consumer<String> output,
            CancellationToken cancellation) throws IOException, InterruptedException {
        if (!ProjectSettings.isProjectRoot(projectRoot)) {
            throw new IllegalArgumentException(
                    "Choose a project root containing gradlew and settings.gradle.kts");
        }
        if (gradleArguments == null || gradleArguments.isEmpty()) {
            throw new IllegalArgumentException("A Gradle task or argument is required");
        }
        if (cancellation == null) {
            throw new IllegalArgumentException("cancellation == null");
        }
        List<String> command = new ArrayList<>(gradleArguments.size() + 3);
        command.add(projectRoot.resolve("gradlew").toString());
        command.add("--console=plain");
        // Keep every build process owned by this invocation. A shared Gradle daemon cannot be
        // reliably terminated as part of this runner's process tree when the editor closes.
        command.add("--no-daemon");
        command.addAll(gradleArguments);
        return runCommand(projectRoot, command, output, cancellation);
    }

    /** Runs an explicit argument vector without involving a shell. */
    public CommandResult runCommand(
            Path workingDirectory,
            List<String> command,
            Consumer<String> output,
            CancellationToken cancellation) throws IOException, InterruptedException {
        if (workingDirectory == null || !java.nio.file.Files.isDirectory(workingDirectory)) {
            throw new IllegalArgumentException("Working directory is not a directory");
        }
        if (command == null || command.isEmpty()
                || command.stream().anyMatch(value -> value == null || value.isEmpty())) {
            throw new IllegalArgumentException("A complete command argument vector is required");
        }
        if (cancellation == null) {
            throw new IllegalArgumentException("cancellation == null");
        }
        Consumer<String> sink = output == null ? ignored -> { } : output;
        Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        boolean attached = false;
        boolean completed = false;
        try {
            cancellation.attach(process);
            attached = true;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sink.accept(line);
                }
            } catch (IOException readFailure) {
                if (!cancellation.isCancelled()) {
                    throw readFailure;
                }
            }
            int exitCode = process.waitFor();
            completed = true;
            return new CommandResult(exitCode, cancellation.isCancelled());
        } catch (InterruptedException interrupted) {
            terminateProcessTree(process);
            Thread.currentThread().interrupt();
            throw interrupted;
        } finally {
            if (attached) {
                cancellation.detach(process);
            }
            if ((!completed || cancellation.isCancelled()) && process.isAlive()) {
                terminateProcessTree(process);
            }
        }
    }

    /** Best-effort, nonblocking process-tree termination; descendants are always targeted first. */
    private static void terminateProcessTree(Process process) {
        ProcessHandle root = process.toHandle();
        Map<Long, ProcessHandle> descendants = new LinkedHashMap<>();
        collectDescendants(root, descendants);

        // Give every known child a graceful termination request before stopping its parent.
        for (ProcessHandle child : descendants.values()) {
            destroy(child, false);
        }
        destroy(root, false);

        // One bounded, nonblocking rescan catches children created during the first pass while
        // the attached process was still alive. Do not wait on the UI cancellation caller.
        collectDescendants(root, descendants);
        for (ProcessHandle child : descendants.values()) {
            if (child.isAlive()) destroy(child, true);
        }
        if (root.isAlive()) destroy(root, true);
    }

    private static void collectDescendants(
            ProcessHandle root, Map<Long, ProcessHandle> destination) {
        try {
            root.descendants().forEach(handle -> destination.putIfAbsent(handle.pid(), handle));
        } catch (RuntimeException unavailable) {
            // Process-tree enumeration is best effort; the attached root is still terminated.
        }
    }

    private static void destroy(ProcessHandle handle, boolean forcibly) {
        try {
            if (!handle.isAlive()) return;
            if (forcibly) handle.destroyForcibly();
            else handle.destroy();
        } catch (RuntimeException unavailable) {
            // Cancellation must remain safe to invoke from JavaFX shutdown paths.
        }
    }

    public static final class CommandResult {
        private final int exitCode;
        private final boolean cancelled;

        CommandResult(int exitCode, boolean cancelled) {
            this.exitCode = exitCode;
            this.cancelled = cancelled;
        }

        public int exitCode() {
            return exitCode;
        }

        public boolean cancelled() {
            return cancelled;
        }

        public boolean successful() {
            return !cancelled && exitCode == 0;
        }
    }

    public static final class CancellationToken {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private Process process;

        public boolean isCancelled() {
            return cancelled.get();
        }

        public void cancel() {
            Process attached;
            synchronized (this) {
                cancelled.set(true);
                attached = process;
            }
            if (attached != null && attached.isAlive()) {
                terminateProcessTree(attached);
            }
        }

        synchronized void attach(Process value) {
            if (process != null) {
                throw new IllegalStateException(
                        "A cancellation token can control only one active command");
            }
            process = value;
            if (cancelled.get() && value.isAlive()) {
                terminateProcessTree(value);
            }
        }

        synchronized void detach(Process value) {
            if (process == value) {
                process = null;
            }
        }
    }
}
