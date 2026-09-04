package com.example.game3d.terrain.editor.compile;

import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Single background compiler that ticket-tags output and definitively drops stale callbacks. */
public final class DebouncedCompiler implements AutoCloseable {
    private final DocumentCompiler compiler;
    private final java.util.concurrent.Executor resultExecutor;
    private final long debounceMillis;
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> pending;
    private CompileTicket newestTicket;
    private boolean closed;

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

    DebouncedCompiler(DocumentCompiler compiler, Duration debounce,
                      java.util.concurrent.Executor resultExecutor,
                      ScheduledExecutorService executor) {
        this.compiler = Objects.requireNonNull(compiler, "compiler");
        this.debounceMillis = Objects.requireNonNull(debounce, "debounce").toMillis();
        this.resultExecutor = Objects.requireNonNull(resultExecutor, "resultExecutor");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public synchronized void submit(CompileRequest request,
                                    Consumer<CompileResult> consumer) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(consumer, "consumer");
        if (closed) return;
        newestTicket = request.ticket();
        if (pending != null) pending.cancel(true);
        pending = executor.schedule(() -> {
            if (!isCurrent(request.ticket())) return;
            CompileResult result;
            try {
                result = compiler.compile(request);
            } catch (Exception error) {
                com.example.game3d.terrain.io.validation.ValidationProblem problem =
                        new com.example.game3d.terrain.io.validation.ValidationProblem(
                                com.example.game3d.terrain.io.validation.ValidationProblem.Severity.ERROR,
                                "$", "Compiler failed: " + error.getMessage());
                result = new CompileResult(request.ticket(), request.documentRevision(), null,
                        Collections.emptyMap(), Collections.emptyMap(),
                        Collections.singletonList(problem), error.toString());
            }
            if (!isCurrent(request.ticket())) return;
            CompileResult accepted = result;
            resultExecutor.execute(() -> {
                if (isCurrent(request.ticket())) consumer.accept(accepted);
            });
        }, debounceMillis, TimeUnit.MILLISECONDS);
    }

    private synchronized boolean isCurrent(CompileTicket ticket) {
        return !closed && ticket.equals(newestTicket);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        newestTicket = null;
        if (pending != null) pending.cancel(true);
        executor.shutdownNow();
    }
}
