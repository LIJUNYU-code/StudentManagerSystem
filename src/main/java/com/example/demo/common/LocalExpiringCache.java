package com.example.demo.common;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 极简本地过期缓存（进程内，单实例）。
 * <p>
 * 为什么自己写而不引 Caffeine：这里只需要「put / get / remove + TTL + 容量上限」四件事，
 * 一个类 80 行就能讲清楚。引入 Caffeine 会多一个依赖、多一套 API 要学，
 * 对当前项目规模没有收益。真到需要统计命中率、异步刷新、权重淘汰时再换。
 * <p>
 * 与 Redis 的分工：Redis 是<b>跨实例共享</b>的权威存储，这里是<b>单实例兜底</b>。
 * 进程重启后本地内容全部丢失，这是设计上接受的——它只在 Redis 故障期间顶班。
 * <p>
 * 线程安全：靠 {@link ConcurrentHashMap} 保证单次操作原子；容量控制是尽力而为，
 * 并发下可能短暂超出上限几个条目，不影响正确性（这些值都有 TTL，会自己过期）。
 */
public class LocalExpiringCache {

    /** 一条缓存记录：值 + 绝对过期时刻（毫秒时间戳） */
    private static final class Entry {
        private final String value;
        private final long expireAt;

        private Entry(String value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    /** 容量上限：防止 Redis 长时间不可用时本地内存被无界增长吃掉 */
    private final int maxSize;

    public LocalExpiringCache(int maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * 写入一个带 TTL 的值。
     *
     * @param ttlMillis 存活毫秒数；&lt;= 0 时直接忽略（已经过期的东西没有缓存价值）
     */
    public void put(String key, String value, long ttlMillis) {
        if (key == null || value == null || ttlMillis <= 0) {
            return;
        }
        if (store.size() >= maxSize) {
            evict();
        }
        store.put(key, new Entry(value, System.currentTimeMillis() + ttlMillis));
    }

    /** 读取；已过期返回 null 并顺手删除（惰性清理，不需要后台线程）。 */
    public String get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt <= System.currentTimeMillis()) {
            store.remove(key, entry);
            return null;
        }
        return entry.value;
    }

    public void remove(String key) {
        store.remove(key);
    }

    /** 当前条目数（含尚未清理的过期项），仅用于测试与观测。 */
    public int size() {
        return store.size();
    }

    /**
     * 淘汰：先清过期项，仍超限则按迭代顺序删掉一部分。
     * <p>
     * 不按「最久未使用」精确排序——ConcurrentHashMap 没有顺序信息，
     * 为了这个场景维护双向链表不值得。这里的目标只是「别让内存无限涨」。
     */
    private void evict() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(en -> en.getValue().expireAt <= now);
        if (store.size() < maxSize) {
            return;
        }
        int toRemove = Math.max(1, store.size() - maxSize / 2);
        Iterator<Map.Entry<String, Entry>> it = store.entrySet().iterator();
        while (it.hasNext() && toRemove-- > 0) {
            it.next();
            it.remove();
        }
    }
}
