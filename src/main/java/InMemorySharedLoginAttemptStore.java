import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link SharedLoginAttemptStore}.
 *
 * <p>Useful for tests and local demos where multiple service instances share
 * the same store object in one JVM.
 */
public class InMemorySharedLoginAttemptStore implements SharedLoginAttemptStore {
    private final Map<String, UserState> states = new ConcurrentHashMap<>();

    @Override
    /** {@inheritDoc} */
    public boolean isAllowed(String userId, Instant now) {
        UserState state = states.computeIfAbsent(userId, id -> new UserState());
        synchronized (state) {
            if (state.blockUntil != null && now.isBefore(state.blockUntil)) {
                return false;
            }
            if (state.blockUntil != null && !now.isBefore(state.blockUntil)) {
                state.blockUntil = null;
            }
            return true;
        }
    }

    @Override
    /** {@inheritDoc} */
    public void recordFailure(String userId, Instant now, int maxFailures, Duration window, Duration blockDuration) {
        UserState state = states.computeIfAbsent(userId, id -> new UserState());
        synchronized (state) {
            if (state.blockUntil != null && now.isBefore(state.blockUntil)) {
                return;
            }

            Instant threshold = now.minus(window);
            while (!state.failures.isEmpty() && state.failures.peekFirst().isBefore(threshold)) {
                state.failures.removeFirst();
            }

            state.failures.addLast(now);
            if (state.failures.size() > maxFailures) {
                state.blockUntil = now.plus(blockDuration);
                state.failures.clear();
            }
        }
    }

    @Override
    /** {@inheritDoc} */
    public void clear(String userId) {
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
     * Mutable per-user state used by the shared in-memory store.
     */
    private static final class UserState {
        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant blockUntil;
    }
}
