package com.hmdp.controller;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.map.MapUtil;
import com.hmdp.dto.Result;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/jvm")
public class JvmController {

    // ===== CPU 问题状态 =====
    private final AtomicBoolean cpuLoopRunning = new AtomicBoolean(false);
    private Thread cpuLoopThread;

    private final AtomicBoolean cpuRegexRunning = new AtomicBoolean(false);
    private Thread cpuRegexThread;

    // ===== 内存泄漏收集器 =====
    private static final List<byte[]> leakList = new ArrayList<>();

    // ===== ThreadLocal 泄漏 =====
    private static final ThreadLocal<byte[]> leakingThreadLocal = new ThreadLocal<>();

    // ===== 线程爆炸 =====
    private final AtomicInteger threadExplosionCount = new AtomicInteger(0);
    private volatile boolean threadExplosionRunning = false;

    // ===== 死锁状态 =====
    private volatile boolean deadlockActive = false;

    // ===== 慢请求计数 =====
    private final AtomicInteger slowRtCount = new AtomicInteger(0);

    // ===== Full GC 计数 =====
    private final AtomicInteger fullGcCount = new AtomicInteger(0);

    // ============== CPU 问题 ==============

    /**
     * 死循环消耗 CPU。GET /jvm/cpu-loop
     */
    @GetMapping("/cpu-loop")
    public Result cpuLoop() {
        if (cpuLoopRunning.compareAndSet(false, true)) {
            cpuLoopThread = new Thread(() -> {
                while (cpuLoopRunning.get()) {
                    // 纯 CPU 空转
                }
            }, "jvm-cpu-loop");
            cpuLoopThread.setDaemon(true);
            cpuLoopThread.start();
            return Result.ok("CPU loop started on thread: jvm-cpu-loop");
        }
        return Result.fail("CPU loop is already running");
    }

    /**
     * 正则灾难性回溯。GET /jvm/cpu-regex
     */
    @GetMapping("/cpu-regex")
    public Result cpuRegex() {
        if (cpuRegexRunning.compareAndSet(false, true)) {
            cpuRegexThread = new Thread(() -> {
                // JDK8 没有 String.repeat，手动拼接10000个a
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 10000; i++) {
                    sb.append('a');
                }
                String malicious = sb.append('!').toString();
                while (cpuRegexRunning.get()) {
                    malicious.matches("^(a+)+$");
                }
            }, "jvm-cpu-regex");
            cpuRegexThread.setDaemon(true);
            cpuRegexThread.start();
            return Result.ok("CPU regex backtracking started on thread: jvm-cpu-regex");
        }
        return Result.fail("CPU regex is already running");
    }

    // ============== 内存泄漏 ==============

    /**
     * 向静态 List 添加 10MB 大对象，模拟内存泄漏。GET /jvm/memory-leak
     */
    @GetMapping("/memory-leak")
    public Result memoryLeak() {
        byte[] chunk = new byte[10 * 1024 * 1024]; // 10MB
        leakList.add(chunk);
        return Result.ok(MapUtil.builder()
                .put("message", "Added 10MB to leak list")
                .put("totalChunks", leakList.size())
                .put("estimatedMB", leakList.size() * 10)
                .map());
    }

    /**
     * ThreadLocal 不清理，模拟内存泄漏。GET /jvm/threadlocal-leak
     */
    @GetMapping("/threadlocal-leak")
    public Result threadlocalLeak() {
        int size = 5 * 1024 * 1024; // 5MB
        leakingThreadLocal.set(new byte[size]);
        return Result.ok("Set 5MB to ThreadLocal on thread: " + Thread.currentThread().getName());
    }

    // ============== 死锁 ==============

    /**
     * 创建两个线程互相等待，触发死锁。GET /jvm/deadlock
     */
    @GetMapping("/deadlock")
    public Result deadlock() {
        if (deadlockActive) {
            return Result.fail("Deadlock is already active");
        }

        Object lockA = new Object();
        Object lockB = new Object();

        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                synchronized (lockB) {
                    // never reach
                }
            }
        }, "jvm-deadlock-A");

        Thread t2 = new Thread(() -> {
            synchronized (lockB) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                synchronized (lockA) {
                    // never reach
                }
            }
        }, "jvm-deadlock-B");

        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();
        deadlockActive = true;
        return Result.ok("Deadlock created between threads: jvm-deadlock-A and jvm-deadlock-B");
    }

    // ============== 线程爆炸 ==============

    /**
     * 用 CachedThreadPool 创建大量线程。GET /jvm/thread-explosion
     */
    @GetMapping("/thread-explosion")
    public Result threadExplosion() {
        int count = 2000;
        threadExplosionRunning = true;
        ExecutorService executor = Executors.newCachedThreadPool();
        for (int i = 0; i < count; i++) {
            int idx = i;
            executor.submit(() -> {
                threadExplosionCount.incrementAndGet();
                while (threadExplosionRunning) {
                    try { Thread.sleep(10_000); } catch (InterruptedException ignored) { break; }
                }
            });
        }
        return Result.ok(
                MapUtil.builder()
                        .put("message", "Submitted " + count + " threads via CachedThreadPool")
                        .put("activeCount", threadExplosionCount.get())
                        .build()
        );
    }

    // ============== Full GC ==============

    /**
     * 分配大量大对象触发 GC。POST /jvm/full-gc
     * body: {"sizeMB": 50, "count": 20}
     */
    @PostMapping("/full-gc")
    public Result fullGc(@RequestBody Map<String, Integer> params) {
        int sizeMB = params.getOrDefault("sizeMB", 50);
        int count = params.getOrDefault("count", 20);

        List<byte[]> temp = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            temp.add(new byte[sizeMB * 1024 * 1024]);
        }
        fullGcCount.addAndGet(count);
        long totalMB = (long) sizeMB * count;
        // temp goes out of scope here, making objects eligible for GC
        temp.clear();
        System.gc(); // suggest GC
        return Result.ok(
                MapUtil.builder()
                        .put("message", "Allocated " + count + " chunks of " + sizeMB + "MB each (" + totalMB + "MB total), then suggested GC")
                        .put("totalAllocations", fullGcCount.get())
                        .build()
        );
    }

    // ============== 状态 & 重置 ==============

    /**
     * 查看当前问题状态。GET /jvm/status
     */
    @GetMapping("/status")
    public Result status() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("cpuLoopRunning", cpuLoopRunning.get());
        s.put("cpuRegexRunning", cpuRegexRunning.get());
        s.put("memoryLeakChunks", leakList.size());
        s.put("memoryLeakEstimatedMB", leakList.size() * 10);
        s.put("threadlocalLeak", "check via heapdump");
        s.put("deadlockActive", deadlockActive);
        s.put("threadExplosionActive", threadExplosionRunning);
        s.put("threadExplosionCount", threadExplosionCount.get());
        s.put("fullGcAllocations", fullGcCount.get());
        s.put("slowRtCount", slowRtCount.get());
        return Result.ok(s);
    }

    /**
     * 重置所有可重置的问题。POST /jvm/reset
     */
    @PostMapping("/reset")
    public Result reset() {
        // 停止 CPU 循环
        if (cpuLoopRunning.get()) {
            cpuLoopRunning.set(false);
            if (cpuLoopThread != null) {
                cpuLoopThread.interrupt();
                cpuLoopThread = null;
            }
        }

        // 停止 CPU 正则
        if (cpuRegexRunning.get()) {
            cpuRegexRunning.set(false);
            if (cpuRegexThread != null) {
                cpuRegexThread.interrupt();
                cpuRegexThread = null;
            }
        }

        // 清理内存泄漏列表
        int leakSize = leakList.size();
        leakList.clear();

        // 停止线程爆炸
        threadExplosionRunning = false;
        threadExplosionCount.set(0);

        // 重置死锁标记（但已存在的死锁线程无法自动恢复，需要重启 JVM）
        deadlockActive = false;

        // 重置计数器
        slowRtCount.set(0);
        fullGcCount.set(0);

        Map<Object, Object> details = MapUtil.builder()
                .put("cpuLoopStopped", true)
                .put("cpuRegexStopped", true)
                .put("memoryLeakClearedChunks", leakSize)
                .put("threadExplosionStopped", true)
                .put("deadlockMarkReset", true)
                .put("countersReset", true)
                .put("note", "deadlock threads may persist until JVM restart; threadlocal-leak objects will be GC'd when threads die")
                .build();

        return Result.ok(
                MapUtil.builder()
                .put("message", "All resettable problems have been cleared")
                .put("details", details)
                .build()
        );
    }
}
