package com.example.game3d.terrain.editor.compile;

import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.io.model.GridMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebouncedCompilerTest {
    @Test void staleAsyncResultIsDiscarded() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch accepted = new CountDownLatch(1);
        AtomicLong acceptedRevision = new AtomicLong(-1);
        DocumentCompiler compiler = (revision, document) -> {
            if (revision == 1) {
                firstStarted.countDown();
                Thread.sleep(80);
            }
            return new CompileResult(revision, null, Collections.emptyMap(),
                    Collections.emptyMap(), Collections.emptyList());
        };
        try (DebouncedCompiler debounced = new DebouncedCompiler(compiler, Duration.ZERO, Runnable::run)) {
            debounced.submit(1, DocumentFactories.blankStructure("one", GridMode.ADVANCED), result -> {
                acceptedRevision.set(result.revision()); accepted.countDown();
            });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            debounced.submit(2, DocumentFactories.blankStructure("two", GridMode.ADVANCED), result -> {
                acceptedRevision.set(result.revision()); accepted.countDown();
            });
            assertTrue(accepted.await(2, TimeUnit.SECONDS));
            assertEquals(2, acceptedRevision.get());
        }
    }
}
