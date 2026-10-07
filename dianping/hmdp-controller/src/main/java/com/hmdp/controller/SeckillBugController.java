package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.shared.SeckillBugFlags;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 秒杀链路JVM问题注入开关 — REST端点。
 *
 * <p>开关的实际标志位定义在 hmdp-domain 的 {@link SeckillBugFlags} 中，
 * 本Controller提供HTTP接口用于在压测过程中按需开启/关闭各bug。</p>
 */
@RestController
@RequestMapping("/jvm/seckill-bug")
public class SeckillBugController {

    @PostMapping("/cpu-regex/on")
    public Result cpuRegexOn() {
        SeckillBugFlags.CPU_REGEX.set(true);
        return Result.ok("CPU_REGEX enabled");
    }

    @PostMapping("/cpu-spin/on")
    public Result cpuSpinOn() {
        SeckillBugFlags.CPU_SPIN.set(true);
        return Result.ok("CPU_SPIN enabled");
    }

    @PostMapping("/memory-leak/on")
    public Result memoryLeakOn() {
        SeckillBugFlags.MEMORY_LEAK.set(true);
        return Result.ok("MEMORY_LEAK enabled");
    }

    @PostMapping("/threadlocal-leak/on")
    public Result threadlocalLeakOn() {
        SeckillBugFlags.THREADLOCAL_LEAK.set(true);
        return Result.ok("THREADLOCAL_LEAK enabled");
    }

    @PostMapping("/deadlock/on")
    public Result deadlockOn() {
        SeckillBugFlags.DEADLOCK.set(true);
        return Result.ok("DEADLOCK enabled");
    }

    @PostMapping("/thread-explosion/on")
    public Result threadExplosionOn() {
        SeckillBugFlags.THREAD_EXPLOSION.set(true);
        return Result.ok("THREAD_EXPLOSION enabled");
    }

    @PostMapping("/gc-alloc/on")
    public Result gcAllocOn() {
        SeckillBugFlags.GC_ALLOC.set(true);
        return Result.ok("GC_ALLOC enabled");
    }

    // ---- 关闭 ----

    @PostMapping("/cpu-regex/off")
    public Result cpuRegexOff() {
        SeckillBugFlags.CPU_REGEX.set(false);
        return Result.ok("CPU_REGEX disabled");
    }

    @PostMapping("/cpu-spin/off")
    public Result cpuSpinOff() {
        SeckillBugFlags.CPU_SPIN.set(false);
        return Result.ok("CPU_SPIN disabled");
    }

    @PostMapping("/memory-leak/off")
    public Result memoryLeakOff() {
        SeckillBugFlags.MEMORY_LEAK.set(false);
        SeckillBugFlags.LEAK_CACHE.clear();
        return Result.ok("MEMORY_LEAK disabled, cache cleared");
    }

    @PostMapping("/threadlocal-leak/off")
    public Result threadlocalLeakOff() {
        SeckillBugFlags.THREADLOCAL_LEAK.set(false);
        return Result.ok("THREADLOCAL_LEAK disabled");
    }

    @PostMapping("/deadlock/off")
    public Result deadlockOff() {
        SeckillBugFlags.DEADLOCK.set(false);
        return Result.ok("DEADLOCK disabled");
    }

    @PostMapping("/thread-explosion/off")
    public Result threadExplosionOff() {
        SeckillBugFlags.THREAD_EXPLOSION.set(false);
        return Result.ok("THREAD_EXPLOSION disabled");
    }

    @PostMapping("/gc-alloc/off")
    public Result gcAllocOff() {
        SeckillBugFlags.GC_ALLOC.set(false);
        return Result.ok("GC_ALLOC disabled");
    }

    @PostMapping("/all/off")
    public Result allOff() {
        SeckillBugFlags.CPU_REGEX.set(false);
        SeckillBugFlags.CPU_SPIN.set(false);
        SeckillBugFlags.MEMORY_LEAK.set(false);
        SeckillBugFlags.LEAK_CACHE.clear();
        SeckillBugFlags.THREADLOCAL_LEAK.set(false);
        SeckillBugFlags.DEADLOCK.set(false);
        SeckillBugFlags.THREAD_EXPLOSION.set(false);
        SeckillBugFlags.GC_ALLOC.set(false);
        return Result.ok("All seckill bugs disabled");
    }

    @GetMapping("/status")
    public Result status() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("cpuRegex", SeckillBugFlags.CPU_REGEX.get());
        s.put("cpuSpin", SeckillBugFlags.CPU_SPIN.get());
        s.put("memoryLeak", SeckillBugFlags.MEMORY_LEAK.get());
        s.put("memoryLeakCacheSize", SeckillBugFlags.LEAK_CACHE.size());
        s.put("threadlocalLeak", SeckillBugFlags.THREADLOCAL_LEAK.get());
        s.put("deadlock", SeckillBugFlags.DEADLOCK.get());
        s.put("threadExplosion", SeckillBugFlags.THREAD_EXPLOSION.get());
        s.put("gcAlloc", SeckillBugFlags.GC_ALLOC.get());
        return Result.ok(s);
    }
}
