package com.hmdp.shared;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * 秒杀链路JVM问题注入开关 — 纯静态标志位，被hmdp-service和hmdp-listener引用。
 *
 * <p>每个bug用AtomicBoolean控制，默认关闭。开启后对应的秒杀代码路径会触发问题。
 * 与JvmController的区别：这里的bug嵌入在真实业务代码中。</p>
 */
public final class SeckillBugFlags {

    private SeckillBugFlags() {}

    // ===== CPU 方向 =====
    public static final AtomicBoolean CPU_REGEX = new AtomicBoolean(false);
    public static final AtomicBoolean CPU_SPIN = new AtomicBoolean(false);

    // ===== Memory 方向 =====
    public static final AtomicBoolean MEMORY_LEAK = new AtomicBoolean(false);
    public static final Map<Long, Object> LEAK_CACHE = new ConcurrentHashMap<>();
    public static final AtomicBoolean THREADLOCAL_LEAK = new AtomicBoolean(false);
    public static final ThreadLocal<byte[]> LEAK_THREADLOCAL = new ThreadLocal<>();

    // ===== Thread 方向 =====
    public static final AtomicBoolean DEADLOCK = new AtomicBoolean(false);
    public static final AtomicBoolean THREAD_EXPLOSION = new AtomicBoolean(false);

    // ===== GC 方向 =====
    public static final AtomicBoolean GC_ALLOC = new AtomicBoolean(false);

    // ===== 正则预编译 =====
    public static final Pattern BACKTRACK_PATTERN = Pattern.compile("^(\\d+)+$");
}
