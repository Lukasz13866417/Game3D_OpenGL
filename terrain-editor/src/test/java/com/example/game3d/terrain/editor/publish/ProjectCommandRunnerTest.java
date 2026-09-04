package com.example.game3d.terrain.editor.publish;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectCommandRunnerTest {
    @TempDir Path directory;

    @Test void streamsOutputAndKeepsArgumentsSeparated() throws Exception {
        Path project = fakeProject("#!/bin/sh\nprintf '%s\\n' \"$@\"\n");
        List<String> output = new ArrayList<>();

        ProjectCommandRunner.CommandResult result = new ProjectCommandRunner().run(
                project, Arrays.asList("taskName", "--args=value with spaces"),
                output::add, new ProjectCommandRunner.CancellationToken());

        assertTrue(result.successful());
        assertFalse(result.cancelled());
        assertEquals(Arrays.asList(
                "--console=plain", "--no-daemon",
                "taskName", "--args=value with spaces"), output);
    }

    @Test void cancellationTerminatesTheAttachedProcess() throws Exception {
        Path project = fakeProject("#!/bin/sh\necho started\nexec sleep 30\n");
        CountDownLatch started = new CountDownLatch(1);
        ProjectCommandRunner.CancellationToken cancellation =
                new ProjectCommandRunner.CancellationToken();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ProjectCommandRunner.CommandResult> future = executor.submit(() ->
                    new ProjectCommandRunner().run(project,
                            Arrays.asList("waitTask"),
                            line -> started.countDown(), cancellation));
            assertTrue(started.await(5, TimeUnit.SECONDS));

            cancellation.cancel();
            ProjectCommandRunner.CommandResult result = future.get(5, TimeUnit.SECONDS);

            assertTrue(result.cancelled());
            assertFalse(result.successful());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void directCommandPreservesArgumentsWithoutShellExpansionAndTokenIsReusable()
            throws Exception {
        Path touchedByShellExpansion = directory.resolve("must-not-exist");
        Path command = executable("arguments.sh", "#!/bin/sh\nprintf '%s\\n' \"$@\"\n");
        ProjectCommandRunner runner = new ProjectCommandRunner();
        ProjectCommandRunner.CancellationToken cancellation =
                new ProjectCommandRunner.CancellationToken();
        List<String> output = new ArrayList<>();
        String suspicious = "$(touch " + touchedByShellExpansion + ")";

        ProjectCommandRunner.CommandResult first = runner.runCommand(
                directory, Arrays.asList(command.toString(), "value with spaces", suspicious),
                output::add, cancellation);
        ProjectCommandRunner.CommandResult second = runner.runCommand(
                directory, Arrays.asList(command.toString(), "second command"),
                output::add, cancellation);

        assertTrue(first.successful());
        assertTrue(second.successful());
        assertEquals(Arrays.asList("value with spaces", suspicious, "second command"), output);
        assertFalse(Files.exists(touchedByShellExpansion));
    }

    @Test void directCommandReportsNonZeroExitWithoutCallingItCancellation() throws Exception {
        Path command = executable("failure.sh", "#!/bin/sh\necho diagnostic\nexit 7\n");
        List<String> output = new ArrayList<>();

        ProjectCommandRunner.CommandResult result = new ProjectCommandRunner().runCommand(
                directory, List.of(command.toString()), output::add,
                new ProjectCommandRunner.CancellationToken());

        assertEquals(7, result.exitCode());
        assertFalse(result.cancelled());
        assertFalse(result.successful());
        assertEquals(List.of("diagnostic"), output);
    }

    @Test void cancellationTerminatesADirectCommand() throws Exception {
        Path command = executable(
                "direct-wait.sh", "#!/bin/sh\necho started\nexec sleep 30\n");
        CountDownLatch started = new CountDownLatch(1);
        ProjectCommandRunner.CancellationToken cancellation =
                new ProjectCommandRunner.CancellationToken();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ProjectCommandRunner.CommandResult> future = executor.submit(() ->
                    new ProjectCommandRunner().runCommand(directory,
                            List.of(command.toString()),
                            line -> started.countDown(), cancellation));
            assertTrue(started.await(5, TimeUnit.SECONDS));

            cancellation.cancel();
            ProjectCommandRunner.CommandResult result = future.get(5, TimeUnit.SECONDS);

            assertTrue(result.cancelled());
            assertFalse(result.successful());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void cancellationTerminatesSpawnedDescendantsBeforeReturning() throws Exception {
        Path command = executable("child-process.sh", "#!/bin/sh\n"
                + "sleep 30 &\n"
                + "child=$!\n"
                + "echo $child\n"
                + "wait $child\n");
        CountDownLatch childStarted = new CountDownLatch(1);
        AtomicLong childPid = new AtomicLong(-1L);
        ProjectCommandRunner.CancellationToken cancellation =
                new ProjectCommandRunner.CancellationToken();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ProjectCommandRunner.CommandResult> future = executor.submit(() ->
                    new ProjectCommandRunner().runCommand(directory,
                            List.of(command.toString()), line -> {
                                childPid.set(Long.parseLong(line));
                                childStarted.countDown();
                            }, cancellation));
            assertTrue(childStarted.await(5, TimeUnit.SECONDS));
            assertTrue(childPid.get() > 0L);

            cancellation.cancel();
            ProjectCommandRunner.CommandResult result = future.get(5, TimeUnit.SECONDS);

            assertTrue(result.cancelled());
            assertFalse(result.successful());
            assertProcessGone(childPid.get());
        } finally {
            cancellation.cancel();
            executor.shutdownNow();
            ProcessHandle.of(childPid.get()).ifPresent(handle -> {
                if (handle.isAlive()) handle.destroyForcibly();
            });
        }
    }

    private Path fakeProject(String script) throws Exception {
        Files.write(directory.resolve("settings.gradle.kts"),
                new byte[0]);
        executable("gradlew", script);
        return directory;
    }

    private Path executable(String name, String script) throws Exception {
        Path wrapper = directory.resolve(name);
        Files.write(wrapper, script.getBytes(StandardCharsets.UTF_8));
        assertTrue(wrapper.toFile().setExecutable(true));
        return wrapper;
    }

    private static void assertProcessGone(long pid) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (!isAlive(pid)) return;
            Thread.sleep(10L);
        }
        assertFalse(isAlive(pid), "Spawned child process is still alive: " + pid);
    }

    private static boolean isAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }
}
