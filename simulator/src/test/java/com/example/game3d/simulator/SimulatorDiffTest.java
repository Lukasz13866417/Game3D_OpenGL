package com.example.game3d.simulator;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SimulatorDiffTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void identicalTraceReturnsSuccess() throws Exception {
        Path left = write("left.ndjson",
                "{\"type\":\"header\",\"schema\":3}\n"
                        + "{\"type\":\"tick\",\"tick\":1}\n");
        Path right = write("right.ndjson",
                "{\"type\":\"header\",\"schema\":3}\n"
                        + "{\"type\":\"tick\",\"tick\":1}\n");

        assertEquals(0, SimulatorMain.diff(left, right));
    }

    @Test
    public void changedTickReturnsNonzeroSoCliCanFailAutomation() throws Exception {
        Path left = write("left.ndjson",
                "{\"type\":\"header\",\"schema\":3}\n"
                        + "{\"type\":\"tick\",\"tick\":1,\"stateHash\":\"10\"}\n");
        Path right = write("right.ndjson",
                "{\"type\":\"header\",\"schema\":3}\n"
                        + "{\"type\":\"tick\",\"tick\":1,\"stateHash\":\"11\"}\n");

        assertNotEquals(0, SimulatorMain.diff(left, right));
    }

    @Test
    public void truncatedTraceReturnsNonzero() throws Exception {
        Path left = write("left.ndjson",
                "{\"type\":\"header\",\"schema\":3}\n"
                        + "{\"type\":\"tick\",\"tick\":1}\n");
        Path right = write("right.ndjson",
                "{\"type\":\"header\",\"schema\":3}\n");

        assertNotEquals(0, SimulatorMain.diff(left, right));
    }

    private Path write(String name, String contents) throws Exception {
        Path path = temporaryFolder.newFile(name).toPath();
        Files.write(path, contents.getBytes(StandardCharsets.UTF_8));
        return path;
    }
}
