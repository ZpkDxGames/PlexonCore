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
import java.util.function.Supplier;

public final class CoreScheduler implements AutoCloseable {
    private final Plugin plugin;
    private final ThreadPoolExecutor executor;

    public CoreScheduler(Plugin plugin, int workerThreads, int queueCapacity) {
        this.plugin = Objects.requireNonNull(plugin);
        int workers = Math.max(1, Math.min(workerThreads, 32));
        int capacity = Math.max(64, queueCapacity);
        AtomicInteger ids = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "PlexonCore-Worker-" + ids.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, error) -> plugin.getLogger().severe("Async task failed: " + error.getMessage()));
            return thread;
        };
        this.executor = new ThreadPoolExecutor(workers, workers, 30, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(capacity), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    public void runPrimary(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }

    public CompletableFuture<Void> runAsync(Runnable task) {
        try { return CompletableFuture.runAsync(task, executor); }
        catch (RejectedExecutionException ex) { return CompletableFuture.failedFuture(ex); }
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        try { return CompletableFuture.supplyAsync(supplier, executor); }
        catch (RejectedExecutionException ex) { return CompletableFuture.failedFuture(ex); }
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

    public int queueSize() { return executor.getQueue().size(); }
    public int activeWorkers() { return executor.getActiveCount(); }
    public int workerCount() { return executor.getCorePoolSize(); }
    public ExecutorService executor() { return executor; }

    @Override
    public void close() {
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
