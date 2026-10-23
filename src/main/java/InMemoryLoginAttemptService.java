import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-node in-memory {@link LoginAttemptService}.
 *
 * <p>Stores per-user failure timestamps and optional block-until state inside
 * the current JVM. This implementation is thread-safe within one process.
 */
public class InMemoryLoginAttemptService implements LoginAttemptService {
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(30);

    private final Map<String, UserState> states = new ConcurrentHashMap<>();

    @Override
    /** {@inheritDoc} */
    public boolean isAllowed(String userId) {
        UserState state = states.computeIfAbsent(userId, id -> new UserState());
        synchronized (state) {
            Instant now = Instant.now();
            if (state.blockUntil != null && now.isBefore(state.blockUntil)) {
                return false;
            }
            if (state.blockUntil != null && !now.isBefore(state.blockUntil)) {
                state.blockUntil = null;
            }
            pruneOldFailures(state, now);
            return true;
        }
    }

    @Override
    /** {@inheritDoc} */
    public void recordFailure(String userId) {
        UserState state = states.computeIfAbsent(userId, id -> new UserState());
        synchronized (state) {
            Instant now = Instant.now();
            if (state.blockUntil != null && now.isBefore(state.blockUntil)) {
                return;
            }

            pruneOldFailures(state, now);
            state.failures.addLast(now);

            if (state.failures.size() > MAX_FAILURES) {
                state.blockUntil = now.plus(BLOCK_DURATION);
                state.failures.clear();
            }
        }
    }

    @Override
    /** {@inheritDoc} */
    public void recordSuccess(String userId) {
        UserState state = states.computeIfAbsent(userId, id -> new UserState());
        synchronized (state) {
            state.failures.clear();
            state.blockUntil = null;
        }
    }

    @Override
    /** {@inheritDoc} */
    public Instant getBlockUntil(String userId) {
        UserState state = states.get(userId);
        if (state == null) {
            return null;
        }
        synchronized (state) {
            return state.blockUntil;
        }
    }

    /**
     * Removes failures older than the configured sliding window.
     *
     * @param state mutable per-user state
     * @param now current timestamp used as pruning reference
     */
    private void pruneOldFailures(UserState state, Instant now) {
        Instant threshold = now.minus(WINDOW);
        while (!state.failures.isEmpty() && state.failures.peekFirst().isBefore(threshold)) {
            state.failures.removeFirst();
        }
    }

    /**
     * Mutable per-user limiter state.
     */
    private static final class UserState {
        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant blockUntil;
    }
}
