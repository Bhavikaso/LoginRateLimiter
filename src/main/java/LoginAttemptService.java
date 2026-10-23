import java.time.Instant;

/**
 * Defines rate-limiting operations for login attempts per user.
 *
 * <p>Implementations enforce a policy for failed attempts and temporary blocking.
 * The interface intentionally hides storage details so callers can use either
 * in-memory or distributed implementations.
 */
public interface LoginAttemptService {
    /**
     * Checks whether a user is currently allowed to attempt login.
     *
     * @param userId unique user identifier
     * @return {@code true} if login attempts are currently allowed; {@code false} if blocked
     */
    boolean isAllowed(String userId);

    /**
     * Records a failed login attempt for a user.
     *
     * @param userId unique user identifier
     */
    void recordFailure(String userId);

    /**
     * Records a successful login and clears any accumulated failure state.
     *
     * @param userId unique user identifier
     */
    void recordSuccess(String userId);

    /**
     * Returns the time until which a user is blocked.
     *
     * @param userId unique user identifier
     * @return block end time, or {@code null} if the user is not blocked
     */
    Instant getBlockUntil(String userId);
}
