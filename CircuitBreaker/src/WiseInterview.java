import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * WISE PAIR PROGRAMMING INTERVIEW
 * * Scenario:
 * Service A depends on downstream Service B and Service C.
 * If a downstream service fails 3 times within a 10-minute window,
 * subsequent calls to that specific service must be blocked for 5 minutes.
 * * After 5 minutes, the circuit should allow exactly ONE test request through (HALF-OPEN).
 * - If the test request succeeds, the circuit closes.
 * - If the test request fails, the circuit opens again for another 5 minutes.
 * * Rules:
 * 1. Implement the CircuitBreaker logic.
 * 2. Implement ServiceA to route calls through the correct CircuitBreaker.
 * 3. Use 'currentSimulatedTime' for all time calculations to allow instant testing.
 */
public class WiseInterview {

    // --- 1. IMPLEMENT THIS CLASS ---
    public static class CircuitBreaker {
        private final String serviceName;

        // Time constants
        private static final long WINDOW_TIME_MS = 10 * 60 * 1000; // 10 mins
        private static final long BLOCK_TIME_MS = 5 * 60 * 1000;   // 5 mins
        private static final int FAILURE_THRESHOLD = 3;

        private AtomicLong lastTripTime = new AtomicLong(0);
        // enum definition for state machine
        enum State {CLOSED, HALF_OPEN, OPEN}

        // this will hold the machine state
        // TODO: Define your state variables (e.g., OPEN, CLOSED, HALF_OPEN)
        AtomicReference<State> state = new AtomicReference<>(State.CLOSED);

        // TODO: Define a data structure to hold exact failure timestamps
        Queue<Long> timestamps = new ConcurrentLinkedQueue<>();

        public CircuitBreaker(String serviceName) {
            this.serviceName = serviceName;
        }

        /**
         * Executes the call if the circuit is CLOSED or HALF-OPEN.
         * Throws RuntimeException("Circuit OPEN") if blocked.
         */
        public String execute(Supplier<String> remoteCall, long currentSimulatedTime) {
            // TODO: Implement the State Machine
            // 1. Check if the call should be blocked based on the current state and time.

            if (!canExecute(currentSimulatedTime)) {
                throw new RuntimeException("Circuit OPEN");
            }

            try {
                String result = remoteCall.get();
                recordSuccess();
                return result;
            } catch (Exception e) {
                recordFailure(currentSimulatedTime);
                throw e;
            }

        }


        // the meaning of record success is to convert state from HALF_OPEN to CLOSED
        private void recordSuccess() {
            if(state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                timestamps.clear();
            }
        }

        // in record failure we are going either from HALF_OPEN to OPEN or from CLOSED to OPEN if threshold breaches.
        private void recordFailure(long currentSimulatedTime) {

            // From HALF_OPEN to OPEN directly
            if(state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                lastTripTime.set(currentSimulatedTime);
                timestamps.clear();
                return;
            }

            timestamps.add(currentSimulatedTime);
            while(!timestamps.isEmpty() && currentSimulatedTime - timestamps.peek() >= WINDOW_TIME_MS) {
                timestamps.poll();
            }

            // From CLOSED to OPEN if failure threshold raches
            if(timestamps.size() >= FAILURE_THRESHOLD) {
                if(state.compareAndSet(State.CLOSED, State.OPEN)) {
                    lastTripTime.set(currentSimulatedTime);
                    timestamps.clear();
                }
            }
        }

        private boolean canExecute(long currentSimulatedTime) {

            if(state.get() == State.CLOSED) return true;
            // call is CHANGED to HALF_OPEN If it was in OPEN and block time for open is passed.
            if (state.get() == State.OPEN && currentSimulatedTime - lastTripTime.get() >= BLOCK_TIME_MS)  {
                if(state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    return true;
                }
            }

            return false;

            // you will come here only if state is HALF_OPEN, that means another thread is testing the HALF_OPEN State
        }
    }

    // --- 2. IMPLEMENT THIS CLASS ---
    public static class ServiceA {

        // TODO: Create a thread-safe registry to hold Circuit Breakers for Service B and Service C
        Map<String, CircuitBreaker> circuitBreakerCache = new ConcurrentHashMap<>();

        public ServiceA() {
            circuitBreakerCache.put("Service_B", new CircuitBreaker("Service_B"));
            circuitBreakerCache.put("Service_C", new CircuitBreaker("Service_C"));
        }
        public String callService(String targetService, Supplier<String> call, long currentSimulatedTime) {
            // TODO: Get the appropriate Circuit Breaker for the targetService and execute the call
            return circuitBreakerCache.get(targetService).execute(call, currentSimulatedTime);
        }
    }

    // --- 3. INTERVIEWER TEST SUITE (DO NOT MODIFY) ---
    public static void main(String[] args) {
        ServiceA serviceA = new ServiceA();
        long startTime = 0;

        // Mock remote calls
        Supplier<String> successCall = () -> "200 OK";
        Supplier<String> failCall = () -> { throw new RuntimeException("503 Down"); };

        System.out.println("--- Starting Wise Circuit Breaker Tests ---");

        // TEST 1: Normal failures that don't breach the threshold
        try { serviceA.callService("Service_B", failCall, startTime); } catch (Exception e) {}
        try { serviceA.callService("Service_B", failCall, startTime + 1000); } catch (Exception e) {}

        System.out.println("Test 1 (2 failures): " +
                (executeSafe(() -> serviceA.callService("Service_B", successCall, startTime + 2000)).equals("200 OK") ? "PASS" : "FAIL"));

        // TEST 2: Breaching the threshold (3rd failure inside 10 mins)
        try { serviceA.callService("Service_B", failCall, startTime + 3000); } catch (Exception e) {} // 3rd failure!

        System.out.println("Test 2 (Circuit should be OPEN): " +
                (executeSafe(() -> serviceA.callService("Service_B", successCall, startTime + 4000)).contains("Circuit OPEN") ? "PASS" : "FAIL"));

        // TEST 3: Independent Services (Service C should still work!)
        System.out.println("Test 3 (Service C unaffected): " +
                (executeSafe(() -> serviceA.callService("Service_C", successCall, startTime + 5000)).equals("200 OK") ? "PASS" : "FAIL"));

        // TEST 4: 5 Minute cooldown passed (HALF-OPEN test)
        long halfOpenTime = startTime + 3000 + CircuitBreaker.BLOCK_TIME_MS + 1000; // 5 mins and 1 sec after the 3rd failure
        System.out.println("Test 4 (HALF-OPEN allows 1 success): " +
                (executeSafe(() -> serviceA.callService("Service_B", successCall, halfOpenTime)).equals("200 OK") ? "PASS" : "FAIL"));

        // TEST 5: Circuit is fully CLOSED again
        System.out.println("Test 5 (Circuit fully recovered): " +
                (executeSafe(() -> serviceA.callService("Service_B", successCall, halfOpenTime + 1000)).equals("200 OK") ? "PASS" : "FAIL"));
    }

    // Helper to catch exceptions and return the message for testing
    private static String executeSafe(Supplier<String> execution) {
        try {
            return execution.get();
        } catch (Exception e) {
            return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
    }
}