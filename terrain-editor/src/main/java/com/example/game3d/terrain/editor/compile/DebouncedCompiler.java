package com.example.game3d.terrain.editor.compile;

import com.example.game3d.terrain.io.model.TerrainSourceDocument;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Single background compiler that revision-tags output and discards stale completions. */
public final class DebouncedCompiler implements AutoCloseable {
    private final DocumentCompiler compiler;
    private final java.util.concurrent.Executor resultExecutor;
    private final long debounceMillis;
    private final ScheduledExecutorService executor;
    private final AtomicLong newestRevision = new AtomicLong();
    private ScheduledFuture<?> pending;

    public DebouncedCompiler(DocumentCompiler compiler, Duration debounce,
                             java.util.concurrent.Executor resultExecutor) {
        this.compiler = compiler;
        this.debounceMillis = debounce.toMillis();
        this.resultExecutor = resultExecutor;
        this.executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "terrain-editor-compiler");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public synchronized void submit(long revision, TerrainSourceDocument document,
                                    Consumer<CompileResult> consumer) {
        newestRevision.set(revision);
        if (pending != null) pending.cancel(false);
        pending = executor.schedule(() -> {
            CompileResult result;
            try {
                result = compiler.compile(revision, document);
            } catch (Exception error) {
                com.example.game3d.terrain.io.validation.ValidationProblem problem =
                        new com.example.game3d.terrain.io.validation.ValidationProblem(
                                com.example.game3d.terrain.io.validation.ValidationProblem.Severity.ERROR,
                                "$", "Compiler failed: " + error.getMessage());
                result = new CompileResult(revision, null, Collections.emptyMap(),
                        Collections.emptyMap(), Collections.singletonList(problem));
            }
            if (newestRevision.get() != revision) return;
            CompileResult accepted = result;
            resultExecutor.execute(() -> {
                if (newestRevision.get() == revision) consumer.accept(accepted);
            });
        }, debounceMillis, TimeUnit.MILLISECONDS);
    }

    @Override public synchronized void close() {
        if (pending != null) pending.cancel(false);
        executor.shutdownNow();
    }
}
