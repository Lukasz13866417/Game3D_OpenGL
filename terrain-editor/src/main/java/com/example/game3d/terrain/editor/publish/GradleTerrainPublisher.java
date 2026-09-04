package com.example.game3d.terrain.editor.publish;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;

/** Real Build hook: invokes the project's shared atomic publishTerrainContent pipeline. */
public final class GradleTerrainPublisher {
    public int publish(Path projectRoot, Consumer<String> output) throws IOException, InterruptedException {
        return publish(projectRoot, output, new ProjectCommandRunner.CancellationToken());
    }

    public int publish(
            Path projectRoot,
            Consumer<String> output,
            ProjectCommandRunner.CancellationToken cancellation)
            throws IOException, InterruptedException {
        return new ProjectCommandRunner().run(projectRoot,
                Arrays.asList("publishTerrainContent"), output, cancellation).exitCode();
    }
}
