//### Problem
//Implement a small, **thread-safe** in-memory cache where each entry expires a fixed time after it was written. This is the kind of local cache you would put in front of a slow downstream call.
//
//### Requirements
//- `put(key, value)` stores a value and (re)starts its time to live.
//        - `get(key)` returns the value, or `null` if the key is absent or expired.
//        - An entry written at time `t` with TTL `d` is valid while `now < t + d`. At exactly `t + d` it is expired.
//- Many threads may call `put` and `get` concurrently. No lost updates, no exceptions, no corrupted state.
//        - Expired entries must not be returned, and should not stay in memory forever.
//        - `null` keys and values are not allowed.
//        - The clock is injectable (a `LongSupplier` returning nanoseconds) so tests do not need `Thread.sleep`.
//
//        ### Examples
//        TTL = 10 seconds.
//
//| Time | Action | Result |
//        |---|---|---|
//        | t = 0s | `put("sku-1", "TV")` | |
//        | t = 0s | `get("sku-1")` | `"TV"` |
//        | t = 9s | `get("sku-1")` | `"TV"` |
//        | t = 10s | `get("sku-1")` | `null` (expired exactly at the TTL) |
//        | t = 10s | `get("unknown")` | `null` |
//        | t = 12s | `put("sku-1", "Radio")` then `get("sku-1")` | `"Radio"` (restarts the TTL) |
//
//        ### Starter code
//```java
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public class TtlCacheDemo {



    static class TtlCache<K, V> {
        private final long ttlNanos;
        private final LongSupplier nanoTime;
        private final Map<K, Map.Entry<V, Long>> store = new java.util.concurrent.ConcurrentHashMap<>();   // add
        private final AtomicLong writes = new AtomicLong();

        TtlCache(Duration ttl) {
            this(ttl, System::nanoTime);
        }

        TtlCache(Duration ttl, LongSupplier nanoTime) {
            this.ttlNanos = ttl.toNanos();
            this.nanoTime = nanoTime;
        }

        void put(K key, V value) {
            // TODO

            if (key == null || value == null) {
                throw new NullPointerException("key and value must not be null");
            }
            long now = nanoTime.getAsLong();
            store.put(key, Map.entry(value, now + ttlNanos));       // value + deadline; restarts TTL

            if (writes.incrementAndGet() % 1024 == 0) {              // occasional cleanup of never-read keys
                store.values().removeIf(e -> now - e.getValue() >= 0);
            }
        }

        /** Returns the value, or null if absent or expired. */
        V get(K key) {
            // TODO
            if (key == null) {
                throw new NullPointerException("key must not be null");
            }
            Map.Entry<V, Long> e = store.get(key);
            if (e == null) {
                return null;
            }
            if (nanoTime.getAsLong() - e.getValue() >= 0) {          // expired at exactly t + d
                store.remove(key, e);                                // only removes this stale entry
                return null;
            }

            return e.getKey();
        }
    }

    public static void main(String[] args) {
        AtomicLong now = new AtomicLong(0);              // fake clock, in nanoseconds
        TtlCache<String, String> cache = new TtlCache<>(Duration.ofSeconds(10), now::get);

        cache.put("sku-1", "TV");
        check("TV".equals(cache.get("sku-1")), "hit at t=0");

        now.set(Duration.ofSeconds(9).toNanos());
        check("TV".equals(cache.get("sku-1")), "hit at t=9s");

        now.set(Duration.ofSeconds(10).toNanos());
        check(cache.get("sku-1") == null, "expired at t=10s");
        check(cache.get("unknown") == null, "unknown key");

        now.set(Duration.ofSeconds(12).toNanos());
        cache.put("sku-1", "Radio");
        check("Radio".equals(cache.get("sku-1")), "overwrite restarts TTL");
    }

    static void check(boolean ok, String name) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
    }
}
