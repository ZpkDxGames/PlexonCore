package com.zpkdxgames.plexoncore.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

public final class CoreScheduler implements AutoCloseable {
    private final Plugin plugin;
    private final ThreadPoolExecutor computeExecutor;
    private final ThreadPoolExecutor ioExecutor;
    private final LongAdder rejectedCompute = new LongAdder();
    private final LongAdder rejectedIo = new LongAdder();
    private final LongAdder failedTasks = new LongAdder();
    private final ConcurrentHashMap<Plugin, Set<CompletableFuture<?>>> ownerFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Plugin, Set<TaskHandle>> ownerScheduled = new ConcurrentHashMap<>();
    private final AtomicReference<FailureRecord> lastFailure = new AtomicReference<>();
    private final AtomicReference<Instant> lastSuccessfulTask = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public CoreScheduler(Plugin plugin, int workerThreads, int queueCapacity) {
        this(plugin, workerThreads, queueCapacity, 1, Math.max(256, queueCapacity / 2));
    }

    public CoreScheduler(Plugin plugin, int computeThreads, int computeQueueCapacity, int ioThreads, int ioQueueCapacity) {
        this.plugin = Objects.requireNonNull(plugin);
        this.computeExecutor = createExecutor("PlexonCore-Compute-", Math.max(1, Math.min(computeThreads, 32)), Math.max(64, computeQueueCapacity));
        this.ioExecutor = createExecutor("PlexonCore-IO-", Math.max(1, Math.min(ioThreads, 8)), Math.max(64, ioQueueCapacity));
    }

    private ThreadPoolExecutor createExecutor(String prefix, int threads, int capacity) {
        AtomicInteger ids = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, prefix + ids.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, error) -> recordFailure("worker-uncaught", error));
            return thread;
        };
        return new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(capacity), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    public void runPrimary(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (Bukkit.isPrimaryThread()) runObserved("primary", null, task);
        else Bukkit.getScheduler().runTask(plugin, () -> runObserved("primary", null, task));
    }

    public void runPrimary(Plugin owner, Runnable task) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(task, "task");
        if (!owner.isEnabled()) return;
        if (Bukkit.isPrimaryThread()) runObserved("primary", owner, task);
        else Bukkit.getScheduler().runTask(plugin, () -> runObserved("primary", owner, task));
    }

    public CompletableFuture<Void> runAsync(Runnable task) {
        return execute(null, "compute", task, computeExecutor, rejectedCompute);
    }

    public CompletableFuture<Void> runAsync(Plugin owner, Runnable task) {
        return execute(Objects.requireNonNull(owner, "owner"), "compute", task, computeExecutor, rejectedCompute);
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return supply(null, "compute", supplier, computeExecutor, rejectedCompute);
    }

    public <T> CompletableFuture<T> supplyAsync(Plugin owner, Supplier<T> supplier) {
        return supply(Objects.requireNonNull(owner, "owner"), "compute", supplier, computeExecutor, rejectedCompute);
    }

    public CompletableFuture<Void> runIo(Runnable task) {
        return execute(null, "io", task, ioExecutor, rejectedIo);
    }

    public CompletableFuture<Void> runIo(Plugin owner, Runnable task) {
        return execute(Objects.requireNonNull(owner, "owner"), "io", task, ioExecutor, rejectedIo);
    }

    public <T> CompletableFuture<T> supplyIo(Supplier<T> supplier) {
        return supply(null, "io", supplier, ioExecutor, rejectedIo);
    }

    public <T> CompletableFuture<T> supplyIo(Plugin owner, Supplier<T> supplier) {
        return supply(Objects.requireNonNull(owner, "owner"), "io", supplier, ioExecutor, rejectedIo);
    }

    private CompletableFuture<Void> execute(Plugin owner, String lane, Runnable task,
                                            ThreadPoolExecutor executor, LongAdder rejected) {
        Objects.requireNonNull(task, "task");
        if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("CoreScheduler is closed"));
        if (owner != null && !owner.isEnabled()) return CompletableFuture.failedFuture(new IllegalStateException("Task owner is disabled: " + owner.getName()));
        CompletableFuture<Void> future;
        try {
            future = CompletableFuture.runAsync(() -> {
                if (owner != null && !owner.isEnabled()) throw new OwnerDisabledException(owner.getName());
                task.run();
            }, executor);
        } catch (RejectedExecutionException ex) {
            rejected.increment();
            recordFailure(lane + "-rejected", ex);
            return CompletableFuture.failedFuture(ex);
        }
        return observeFuture(owner, lane, future);
    }

    private <T> CompletableFuture<T> supply(Plugin owner, String lane, Supplier<T> supplier,
                                            ThreadPoolExecutor executor, LongAdder rejected) {
        Objects.requireNonNull(supplier, "supplier");
        if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("CoreScheduler is closed"));
        if (owner != null && !owner.isEnabled()) return CompletableFuture.failedFuture(new IllegalStateException("Task owner is disabled: " + owner.getName()));
        CompletableFuture<T> future;
        try {
            future = CompletableFuture.supplyAsync(() -> {
                if (owner != null && !owner.isEnabled()) throw new OwnerDisabledException(owner.getName());
                return supplier.get();
            }, executor);
        } catch (RejectedExecutionException ex) {
            rejected.increment();
            recordFailure(lane + "-rejected", ex);
            return CompletableFuture.failedFuture(ex);
        }
        return observeFuture(owner, lane, future);
    }

    /**
     * Returns a completion future that becomes visible to callers only after Core has committed
     * owner bookkeeping and scheduler health. This prevents a successful join from racing a stale
     * DEGRADED health snapshot.
     */
    private <T> CompletableFuture<T> observeFuture(Plugin owner, String lane, CompletableFuture<T> source) {
        if (owner != null) ownerFutures.computeIfAbsent(owner, ignored -> ConcurrentHashMap.newKeySet()).add(source);
        CompletableFuture<T> observed = new CompletableFuture<>();
        source.whenComplete((value, error) -> {
            if (owner != null) {
                Set<CompletableFuture<?>> set = ownerFutures.get(owner);
                if (set != null) {
                    set.remove(source);
                    if (set.isEmpty()) ownerFutures.remove(owner, set);
                }
            }
            if (error == null) {
                markSuccess();
                observed.complete(value);
                return;
            }
            Throwable root = unwrap(error);
            if (!(root instanceof OwnerDisabledException) && !source.isCancelled()) recordFailure(lane, root);
            if (source.isCancelled()) observed.cancel(false);
            else observed.completeExceptionally(root);
        });
        return observed;
    }

    public TaskHandle schedulePrimary(Duration delay, Runnable task) {
        return schedulePrimaryObserved(null, delay, task).legacyHandle();
    }

    public TaskHandle schedulePrimary(Plugin owner, Duration delay, Runnable task) {
        return schedulePrimaryObserved(Objects.requireNonNull(owner, "owner"), delay, task).legacyHandle();
    }

    public ObservedTaskHandle schedulePrimaryObserved(Plugin owner, Duration delay, Runnable task) {
        return scheduleObserved(owner, delay, task, false);
    }

    public TaskHandle scheduleAsync(Duration delay, Runnable task) {
        return scheduleAsyncObserved(null, delay, task).legacyHandle();
    }

    public TaskHandle scheduleAsync(Plugin owner, Duration delay, Runnable task) {
        return scheduleAsyncObserved(Objects.requireNonNull(owner, "owner"), delay, task).legacyHandle();
    }

    public ObservedTaskHandle scheduleAsyncObserved(Plugin owner, Duration delay, Runnable task) {
        return scheduleObserved(owner, delay, task, true);
    }

    private ObservedTaskHandle scheduleObserved(Plugin owner, Duration delay, Runnable task, boolean async) {
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(task, "task");
        if (closed.get()) throw new IllegalStateException("CoreScheduler is closed");
        if (owner != null && !owner.isEnabled()) throw new IllegalStateException("Task owner is disabled: " + owner.getName());
        CompletableFuture<Void> completion = new CompletableFuture<>();
        AtomicReference<TaskHandle> handleRef = new AtomicReference<>();
        Runnable trigger = () -> {
            TaskHandle handle = handleRef.get();
            untrackScheduled(owner, handle);
            if (owner != null && !owner.isEnabled()) {
                completion.completeExceptionally(new OwnerDisabledException(owner.getName()));
                return;
            }
            if (async) {
                CompletableFuture<Void> future = owner == null ? runAsync(task) : runAsync(owner, task);
                future.whenComplete((ignored, error) -> complete(completion, error));
            } else {
                try {
                    runObserved("scheduled-primary", owner, task);
                    completion.complete(null);
                } catch (Throwable error) {
                    completion.completeExceptionally(error);
                }
            }
        };
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, trigger, delayTicks(delay));
        TaskHandle handle = new TaskHandle(() -> {
            bukkitTask.cancel();
            completion.cancel(false);
            untrackScheduled(owner, handleRef.get());
        }, bukkitTask::isCancelled);
        handleRef.set(handle);
        trackScheduled(owner, handle);
        return new ObservedTaskHandle(handle, completion);
    }

    /** Repeating pooled trigger. Each async execution has its own observed completion path. */
    public TaskHandle scheduleRepeatingAsync(Plugin owner, Duration initialDelay, Duration period, Runnable task) {
        Objects.requireNonNull(initialDelay, "initialDelay");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(task, "task");
        if (closed.get()) throw new IllegalStateException("CoreScheduler is closed");
        if (owner != null && !owner.isEnabled()) throw new IllegalStateException("Task owner is disabled: " + owner.getName());
        AtomicReference<TaskHandle> handleRef = new AtomicReference<>();
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (owner != null && !owner.isEnabled()) {
                TaskHandle handle = handleRef.get();
                if (handle != null) handle.cancel();
                return;
            }
            if (owner == null) runAsync(task);
            else runAsync(owner, task);
        }, delayTicks(initialDelay), Math.max(1L, delayTicks(period)));
        TaskHandle handle = new TaskHandle(() -> {
            bukkitTask.cancel();
            untrackScheduled(owner, handleRef.get());
        }, bukkitTask::isCancelled);
        handleRef.set(handle);
        trackScheduled(owner, handle);
        return handle;
    }

    public int purgeOwner(Plugin owner) {
        if (owner == null) return 0;
        int cancelled = 0;
        Set<TaskHandle> scheduled = ownerScheduled.remove(owner);
        if (scheduled != null) {
            for (TaskHandle handle : Set.copyOf(scheduled)) {
                if (!handle.cancelled()) {
                    handle.cancel();
                    cancelled++;
                }
            }
        }
        Set<CompletableFuture<?>> futures = ownerFutures.remove(owner);
        if (futures != null) {
            for (CompletableFuture<?> future : Set.copyOf(futures)) {
                if (!future.isDone() && future.cancel(true)) cancelled++;
            }
        }
        return cancelled;
    }

    private void trackScheduled(Plugin owner, TaskHandle handle) {
        if (owner != null && handle != null) ownerScheduled.computeIfAbsent(owner, ignored -> ConcurrentHashMap.newKeySet()).add(handle);
    }

    private void untrackScheduled(Plugin owner, TaskHandle handle) {
        if (owner == null || handle == null) return;
        Set<TaskHandle> set = ownerScheduled.get(owner);
        if (set != null) {
            set.remove(handle);
            if (set.isEmpty()) ownerScheduled.remove(owner, set);
        }
    }

    private void runObserved(String lane, Plugin owner, Runnable task) {
        if (owner != null && !owner.isEnabled()) return;
        try {
            task.run();
            markSuccess();
        } catch (Throwable error) {
            recordFailure(lane, error);
            throw error;
        }
    }

    private void recordFailure(String lane, Throwable error) {
        failedTasks.increment();
        Throwable root = unwrap(error);
        lastFailure.set(new FailureRecord(lane, root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage(), Instant.now()));
        java.util.logging.Logger logger = plugin.getLogger();
        if (logger != null) logger.warning("Core scheduler task failure [" + lane + "]: " + root.getClass().getSimpleName() + ": " + root.getMessage());
    }

    private void markSuccess() { lastSuccessfulTask.set(Instant.now()); }

    private static void complete(CompletableFuture<Void> target, Throwable error) {
        if (error == null) target.complete(null);
        else target.completeExceptionally(unwrap(error));
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    static long delayTicks(Duration delay) {
        long millis = Objects.requireNonNull(delay, "delay").toMillis();
        if (millis <= 0L) return 1L;
        long ticks = millis / 50L;
        if (millis % 50L != 0L) ticks++;
        return Math.max(1L, ticks);
    }

    public int queueSize() { return computeExecutor.getQueue().size(); }
    public int computeQueueSize() { return computeExecutor.getQueue().size(); }
    public int ioQueueSize() { return ioExecutor.getQueue().size(); }
    public int activeWorkers() { return computeExecutor.getActiveCount(); }
    public int activeIoWorkers() { return ioExecutor.getActiveCount(); }
    public int workerCount() { return computeExecutor.getCorePoolSize(); }
    public int ioWorkerCount() { return ioExecutor.getCorePoolSize(); }
    public long rejectedComputeTasks() { return rejectedCompute.sum(); }
    public long rejectedIoTasks() { return rejectedIo.sum(); }
    public long failedTasks() { return failedTasks.sum(); }
    public int ownerTaskCount() {
        return ownerFutures.values().stream().mapToInt(Set::size).sum()
                + ownerScheduled.values().stream().mapToInt(Set::size).sum();
    }
    public ExecutorService executor() { return computeExecutor; }

    public SchedulerHealth health() {
        FailureRecord failure = lastFailure.get();
        Instant success = lastSuccessfulTask.get();
        boolean recovered = failure == null || success != null && success.isAfter(failure.at());
        return new SchedulerHealth(recovered ? HealthState.READY : HealthState.DEGRADED,
                failedTasks.sum(), rejectedCompute.sum(), rejectedIo.sum(),
                failure == null ? null : failure.detail(), failure == null ? null : failure.at());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        for (Plugin owner : Set.copyOf(ownerScheduled.keySet())) purgeOwner(owner);
        shutdown(computeExecutor);
        shutdown(ioExecutor);
        ownerFutures.clear();
        ownerScheduled.clear();
    }

    private static void shutdown(ThreadPoolExecutor executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    /** API 2.0 compatibility handle retained unchanged. */
    public record TaskHandle(Runnable cancelAction, Supplier<Boolean> cancelledState) {
        public TaskHandle {
            Objects.requireNonNull(cancelAction, "cancelAction");
            Objects.requireNonNull(cancelledState, "cancelledState");
        }
        public void cancel() { cancelAction.run(); }
        public boolean cancelled() { return cancelledState.get(); }
    }

    /** API 2.1 additive handle with completion/failure visibility. */
    public record ObservedTaskHandle(TaskHandle legacyHandle, CompletableFuture<Void> completion) {
        public ObservedTaskHandle {
            Objects.requireNonNull(legacyHandle, "legacyHandle");
            Objects.requireNonNull(completion, "completion");
        }
        public void cancel() { legacyHandle.cancel(); }
        public boolean cancelled() { return legacyHandle.cancelled(); }
    }

    public enum HealthState { READY, DEGRADED }
    public record SchedulerHealth(HealthState state, long failedTasks, long rejectedComputeTasks,
                                  long rejectedIoTasks, String lastFailure, Instant lastFailureAt) {}
    private record FailureRecord(String lane, String detail, Instant at) {}
    private static final class OwnerDisabledException extends RuntimeException {
        private OwnerDisabledException(String owner) { super("Task owner disabled: " + owner); }
    }
}
