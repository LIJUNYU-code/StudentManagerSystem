package com.example.demo.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LocalExpiringCache} 单测：只覆盖「过期、容量、删除」这三件它真正负责的事。
 */
class LocalExpiringCacheTest {

    @Test
    @DisplayName("put 后能 get 到值")
    void putThenGet() {
        LocalExpiringCache cache = new LocalExpiringCache(100);
        cache.put("k", "v", 60_000);
        assertEquals("v", cache.get("k"));
    }

    @Test
    @DisplayName("不存在的 key 返回 null")
    void getMissingReturnsNull() {
        LocalExpiringCache cache = new LocalExpiringCache(100);
        assertNull(cache.get("nope"));
    }

    @Test
    @DisplayName("TTL 到期后 get 返回 null，并顺手删除该条目")
    void expiredEntryIsRemoved() throws Exception {
        LocalExpiringCache cache = new LocalExpiringCache(100);
        cache.put("k", "v", 30);
        Thread.sleep(80);
        assertNull(cache.get("k"));
        assertEquals(0, cache.size(), "惰性删除应把过期项清掉");
    }

    @Test
    @DisplayName("ttl <= 0 直接忽略，不写入")
    void nonPositiveTtlIgnored() {
        LocalExpiringCache cache = new LocalExpiringCache(100);
        cache.put("k", "v", 0);
        cache.put("k2", "v", -5);
        assertNull(cache.get("k"));
        assertNull(cache.get("k2"));
    }

    @Test
    @DisplayName("key 或 value 为 null 时忽略，不抛异常")
    void nullArgsIgnored() {
        LocalExpiringCache cache = new LocalExpiringCache(100);
        cache.put(null, "v", 1000);
        cache.put("k", null, 1000);
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("remove 后立刻读不到")
    void removeWorks() {
        LocalExpiringCache cache = new LocalExpiringCache(100);
        cache.put("k", "v", 60_000);
        cache.remove("k");
        assertNull(cache.get("k"));
    }

    @Test
    @DisplayName("超过容量上限会淘汰，size 不会无限增长")
    void evictsWhenFull() {
        LocalExpiringCache cache = new LocalExpiringCache(10);
        for (int i = 0; i < 50; i++) {
            cache.put("k" + i, "v" + i, 60_000);
        }
        // 淘汰是「先清过期项，仍超限则删掉一半」，所以 size 必然 <= 上限
        assertTrue(cache.size() <= 10, "size 应被控制在容量上限内，实际=" + cache.size());
        assertNotNull(cache.get("k49"), "最后写入的条目应还在");
    }

    @Test
    @DisplayName("淘汰优先清理已过期条目")
    void evictPrefersExpired() throws Exception {
        LocalExpiringCache cache = new LocalExpiringCache(4);
        cache.put("old1", "v", 20);
        cache.put("old2", "v", 20);
        Thread.sleep(60);
        cache.put("new1", "v", 60_000);
        cache.put("new2", "v", 60_000);
        cache.put("new3", "v", 60_000);
        // 触发淘汰时 old1/old2 已过期，应被 removeIf 清掉，新写入的都保留
        assertEquals("v", cache.get("new1"));
        assertEquals("v", cache.get("new2"));
        assertEquals("v", cache.get("new3"));
        assertNull(cache.get("old1"));
    }
}
