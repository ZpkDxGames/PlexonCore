package com.zpkdxgames.plexoncore.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

public final class CoreScheduler implements AutoCloseable {
    private final Plugin plugin;
    private final ThreadPoolExecutor computeExecutor;
    private final ThreadPoolExecutor ioExecutor;
    private final LongAdder rejectedCompute = new LongAdder();
    private final LongAdder rejectedIo = new LongAdder();

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
            thread.setUncaughtExceptionHandler((t, error) -> plugin.getLogger().severe("Worker task failed: " + error.getMessage()));
            return thread;
        };
        return new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(capacity), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    public void runPrimary(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }

    public CompletableFuture<Void> runAsync(Runnable task) {
        return execute(task, computeExecutor, rejectedCompute);
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return supply(supplier, computeExecutor, rejectedCompute);
    }

    public CompletableFuture<Void> runIo(Runnable task) {
        return execute(task, ioExecutor, rejectedIo);
    }

    public <T> CompletableFuture<T> supplyIo(Supplier<T> supplier) {
        return supply(supplier, ioExecutor, rejectedIo);
    }

    private static CompletableFuture<Void> execute(Runnable task, ThreadPoolExecutor executor, LongAdder rejected) {
        try { return CompletableFuture.runAsync(task, executor); }
        catch (RejectedExecutionException ex) { rejected.increment(); return CompletableFuture.failedFuture(ex); }
    }

    private static <T> CompletableFuture<T> supply(Supplier<T> supplier, ThreadPoolExecutor executor, LongAdder rejected) {
        try { return CompletableFuture.supplyAsync(supplier, executor); }
        catch (RejectedExecutionException ex) { rejected.increment(); return CompletableFuture.failedFuture(ex); }
    }

    public TaskHandle schedulePrimary(Duration delay, Runnable task) {
        long ticks = Math.max(1, delay.toMillis() / 50L);
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
        return new TaskHandle(bukkitTask::cancel, bukkitTask::isCancelled);
    }

    public TaskHandle scheduleAsync(Duration delay, Runnable task) {
        long ticks = Math.max(1, delay.toMillis() / 50L);
        BukkitTask trigger = Bukkit.getScheduler().runTaskLater(plugin, () -> runAsync(task), ticks);
        return new TaskHandle(trigger::cancel, trigger::isCancelled);
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
    public ExecutorService executor() { return computeExecutor; }

    @Override
    public void close() {
        shutdown(computeExecutor);
        shutdown(ioExecutor);
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

    public record TaskHandle(Runnable cancelAction, Supplier<Boolean> cancelledState) {
        public void cancel() { cancelAction.run(); }
        public boolean cancelled() { return cancelledState.get(); }
    }
}
