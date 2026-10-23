import java.time.Duration;
import java.time.Instant;

/**
 * Distributed {@link LoginAttemptService} implementation backed by a
 * {@link SharedLoginAttemptStore}.
 *
 * <p>This class applies a fixed policy and delegates persistence/atomicity to
 * the shared store so multiple nodes can enforce the same per-user limits.
 */
public class DistributedLoginAttemptService implements LoginAttemptService {
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(30);

    private final SharedLoginAttemptStore store;

    /**
     * Creates a distributed service using the provided shared store.
     *
     * @param store shared store used across service instances
     */
    public DistributedLoginAttemptService(SharedLoginAttemptStore store) {
        this.store = store;
    }

    @Override
    /** {@inheritDoc} */
    public boolean isAllowed(String userId) {
        return store.isAllowed(userId, Instant.now());
    }

    @Override
    /** {@inheritDoc} */
    public void recordFailure(String userId) {
        store.recordFailure(userId, Instant.now(), MAX_FAILURES, WINDOW, BLOCK_DURATION);
    }

    @Override
    /** {@inheritDoc} */
    public void recordSuccess(String userId) {
        store.clear(userId);
    }

    @Override
    /** {@inheritDoc} */
    public Instant getBlockUntil(String userId) {
        return store.getBlockUntil(userId);
    }
}
