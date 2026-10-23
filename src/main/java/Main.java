/**
 * Demonstrates single-node and shared-store login rate limiter behavior.
 */
public class Main {
    /**
     * Runs local demo scenarios.
     *
     * @param args unused CLI arguments
     */
    public static void main(String[] args) {

        String userId = "user-123";

        System.out.println("Stateful in-memory service demo:");
        LoginAttemptService stateful = new InMemoryLoginAttemptService();
        runScenario(stateful, userId);

        System.out.println();
        System.out.println("Distributed service demo using shared in-memory store (simulates multi-instance):");
        SharedLoginAttemptStore sharedStore = new InMemorySharedLoginAttemptStore();
        LoginAttemptService nodeA = new DistributedLoginAttemptService(sharedStore);
        LoginAttemptService nodeB = new DistributedLoginAttemptService(sharedStore);
        runDistributedScenario(nodeA, nodeB, userId + "-dist");

        System.out.println();
        System.out.println("Redis demo is optional. Set REDIS_HOST and REDIS_PORT to run real Redis store.");
    }

    /**
     * Exercises one service instance through failures and reset.
     *
     * @param service login limiter implementation under test
     * @param userId user id to simulate
     */
    private static void runScenario(LoginAttemptService service, String userId) {
        System.out.println("Initial allowed: " + service.isAllowed(userId));
        for (int i = 1; i <= 6; i++) {
            service.recordFailure(userId);
            System.out.println("After failure " + i + ", allowed=" + service.isAllowed(userId)
                    + ", blockUntil=" + service.getBlockUntil(userId));
        }
        service.recordSuccess(userId);
        System.out.println("After success reset, allowed=" + service.isAllowed(userId));
    }

    /**
     * Simulates two app nodes sharing state for one user.
     *
     * @param nodeA first node
     * @param nodeB second node
     * @param userId user id to simulate
     */
    private static void runDistributedScenario(LoginAttemptService nodeA, LoginAttemptService nodeB, String userId) {
        System.out.println("Initial allowed (A): " + nodeA.isAllowed(userId));

        nodeA.recordFailure(userId);
        nodeB.recordFailure(userId);
        nodeA.recordFailure(userId);
        nodeB.recordFailure(userId);
        nodeA.recordFailure(userId);

        System.out.println("After 5 distributed failures, allowed (A): " + nodeA.isAllowed(userId));

        nodeB.recordFailure(userId);
        System.out.println("After 6th failure from B, allowed (A): " + nodeA.isAllowed(userId));
        System.out.println("BlockUntil from A: " + nodeA.getBlockUntil(userId));
        System.out.println("BlockUntil from B: " + nodeB.getBlockUntil(userId));
    }
}