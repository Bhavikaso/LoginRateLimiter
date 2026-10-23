import java.time.Duration;
import java.time.Instant;

/**
 * Shared backing store contract used by distributed rate-limiter nodes.
 *
 * <p>Implementations should provide per-user consistency guarantees so multiple
 * application instances observe and update the same user state safely.
 */
public interface SharedLoginAttemptStore {
    /**
     * Checks whether a user is currently allowed to attempt login at the provided time.
     *
     * @param userId unique user identifier
     * @param now current time supplied by the caller
     * @return {@code true} if user is allowed, otherwise {@code false}
     */
    boolean isAllowed(String userId, Instant now);

    /**
     * Records a failed login attempt and applies the configured policy thresholds.
     *
     * @param userId unique user identifier
     * @param now current time supplied by the caller
     * @param maxFailures maximum failures allowed within the sliding window before blocking
     * @param window sliding window duration used for counting failures
     * @param blockDuration duration to block once threshold is exceeded
     */
    void recordFailure(String userId, Instant now, int maxFailures, Duration window, Duration blockDuration);

    /**
     * Clears all failure and block state for a user.
     *
     * @param userId unique user identifier
     */
    void clear(String userId);

    /**
     * Returns the time until which the user is blocked.
     *
     * @param userId unique user identifier
     * @return block end time, or {@code null} if no active block exists
     */
    Instant getBlockUntil(String userId);
}
