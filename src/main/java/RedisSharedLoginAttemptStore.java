import redis.clients.jedis.JedisPooled;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Redis-backed {@link SharedLoginAttemptStore} implementation.
 *
 * <p>Uses a Lua script to perform read-prune-add-block logic atomically per
 * user, avoiding race conditions across multiple application nodes.
 */
public class RedisSharedLoginAttemptStore implements SharedLoginAttemptStore {
    private static final String LUA_RECORD_FAILURE =
            "local blockUntil = redis.call('GET', KEYS[2]) " +
            "if blockUntil and tonumber(blockUntil) > tonumber(ARGV[1]) then return 0 end " +
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', tonumber(ARGV[1]) - tonumber(ARGV[2])) " +
            "redis.call('ZADD', KEYS[1], tonumber(ARGV[1]), ARGV[1] .. '-' .. ARGV[5]) " +
            "local count = redis.call('ZCARD', KEYS[1]) " +
            "if count > tonumber(ARGV[3]) then " +
            "  local newBlockUntil = tonumber(ARGV[1]) + tonumber(ARGV[4]) " +
            "  redis.call('SET', KEYS[2], newBlockUntil, 'PX', tonumber(ARGV[4])) " +
            "  redis.call('DEL', KEYS[1]) " +
            "  return newBlockUntil " +
            "else " +
            "  local ttl = tonumber(ARGV[2]) + 60000 " +
            "  redis.call('PEXPIRE', KEYS[1], ttl) " +
            "  return 1 " +
            "end";

    private final JedisPooled jedis;
    private final String keyPrefix;

    /**
     * Creates a Redis-backed shared store.
     *
     * @param jedis pooled Redis client
     * @param keyPrefix key namespace prefix; defaults to {@code login:attempt} when null
     */
    public RedisSharedLoginAttemptStore(JedisPooled jedis, String keyPrefix) {
        this.jedis = jedis;
        this.keyPrefix = keyPrefix == null ? "login:attempt" : keyPrefix;
    }

    @Override
    /** {@inheritDoc} */
    public boolean isAllowed(String userId, Instant now) {
        long nowMs = now.toEpochMilli();
        String blockUntilRaw = jedis.get(blockKey(userId));
        if (blockUntilRaw == null) {
            return true;
        }
        return Long.parseLong(blockUntilRaw) <= nowMs;
    }

    @Override
    /** {@inheritDoc} */
    public void recordFailure(String userId, Instant now, int maxFailures, Duration window, Duration blockDuration) {
        long nowMs = now.toEpochMilli();
        long windowMs = window.toMillis();
        long blockMs = blockDuration.toMillis();
        List<String> keys = Arrays.asList(failureZsetKey(userId), blockKey(userId));
        List<String> args = Arrays.asList(
                String.valueOf(nowMs),
                String.valueOf(windowMs),
                String.valueOf(maxFailures),
                String.valueOf(blockMs),
                String.valueOf(System.nanoTime())
        );
        jedis.eval(LUA_RECORD_FAILURE, keys, args);
    }

    @Override
    /** {@inheritDoc} */
    public void clear(String userId) {
        jedis.del(failureZsetKey(userId), blockKey(userId));
    }

    @Override
    /** {@inheritDoc} */
    public Instant getBlockUntil(String userId) {
        String blockUntilRaw = jedis.get(blockKey(userId));
        if (blockUntilRaw == null) {
            return null;
        }
        return Instant.ofEpochMilli(Long.parseLong(blockUntilRaw));
    }

    /**
     * Returns the Redis sorted-set key storing failure timestamps for a user.
     */
    private String failureZsetKey(String userId) {
        return keyPrefix + ":failures:" + userId;
    }

    /**
     * Returns the Redis key storing block-until epoch milliseconds for a user.
     */
    private String blockKey(String userId) {
        return keyPrefix + ":blockUntil:" + userId;
    }
}
