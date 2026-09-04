package com.example.game3d.terrain.editor.compile;

import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.resolve.TerrainDocumentRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebouncedCompilerTest {
    @Test void staleAsyncResultIsDiscarded() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch accepted = new CountDownLatch(1);
        AtomicLong acceptedRevision = new AtomicLong(-1);
        DocumentCompiler compiler = request -> {
            if (request.ticket().sequence() == 1) {
                firstStarted.countDown();
                Thread.sleep(80);
            }
            return new CompileResult(request.ticket(), request.documentRevision(), null,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList());
        };
        try (DebouncedCompiler debounced = new DebouncedCompiler(compiler, Duration.ZERO, Runnable::run)) {
            TerrainDocumentRepository empty = emptyRepository();
            debounced.submit(request(1, "one", empty), result -> {
                acceptedRevision.set(result.revision()); accepted.countDown();
            });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            debounced.submit(request(2, "two", empty), result -> {
                acceptedRevision.set(result.revision()); accepted.countDown();
            });
            assertTrue(accepted.await(2, TimeUnit.SECONDS));
            assertEquals(2, acceptedRevision.get());
        }
    }

    @Test void closeInvalidatesAlreadyQueuedResultCallback() throws Exception {
        CountDownLatch compiled = new CountDownLatch(1);
        AtomicReference<Runnable> queuedCallback = new AtomicReference<>();
        AtomicInteger accepted = new AtomicInteger();
        DocumentCompiler compiler = request -> {
            compiled.countDown();
            return new CompileResult(request.ticket(), request.documentRevision(), null,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList());
        };
        DebouncedCompiler debounced = new DebouncedCompiler(
                compiler, Duration.ZERO, queuedCallback::set);
        debounced.submit(request(1, 0, "closed", emptyRepository()),
                ignored -> accepted.incrementAndGet());
        assertTrue(compiled.await(2, TimeUnit.SECONDS));
        for (int attempt = 0; attempt < 100 && queuedCallback.get() == null; attempt++) {
            Thread.sleep(5L);
        }

        debounced.close();
        queuedCallback.get().run();

        assertEquals(0, accepted.get());
    }

    @Test void ticketSequenceRejectsReloadRaceEvenWhenDocumentRevisionIsReused()
            throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch accepted = new CountDownLatch(1);
        AtomicLong acceptedTicket = new AtomicLong(-1);
        DocumentCompiler compiler = request -> {
            if (request.ticket().sequence() == 1) {
                firstStarted.countDown();
                Thread.sleep(80L);
            }
            return new CompileResult(request.ticket(), request.documentRevision(), null,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList());
        };
        try (DebouncedCompiler debounced = new DebouncedCompiler(
                compiler, Duration.ZERO, Runnable::run)) {
            debounced.submit(request(1, 0, "before-reload", emptyRepository()),
                    result -> acceptedTicket.set(result.ticket().sequence()));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            debounced.submit(request(2, 0, "after-reload", emptyRepository()), result -> {
                acceptedTicket.set(result.ticket().sequence());
                accepted.countDown();
            });
            assertTrue(accepted.await(2, TimeUnit.SECONDS));
            assertEquals(2L, acceptedTicket.get());
        }
    }

    private static CompileRequest request(
            long sequence, String id, TerrainDocumentRepository repository) {
        return request(sequence, sequence, id, repository);
    }

    private static CompileRequest request(
            long sequence, long revision, String id, TerrainDocumentRepository repository) {
        return new CompileRequest(new CompileTicket(new UUID(1L, 2L), sequence),
                revision, DocumentFactories.blankStructure(id, GridMode.ADVANCED),
                repository);
    }

    private static TerrainDocumentRepository emptyRepository() {
        return new TerrainDocumentRepository() {
            @Override public com.example.game3d.terrain.io.model.StructureDocument findStructure(String id) {
                return null;
            }
            @Override public com.example.game3d.terrain.io.model.LevelDocument findLevel(String id) {
                return null;
            }
        };
    }
}
