import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class TokenRateLimiter {

    private static final long maxCapacity = 1000;
    private AtomicLong availableTokens = new AtomicLong(2);
    private AtomicLong lastTripTime = new AtomicLong(0);

    private static final long tokensPerSecond = 5;

    public boolean throttle(long currentSimulatedTime) {
        refillToken(currentSimulatedTime);

        while(true) {
            long currentTokens = availableTokens.get();
            if (currentTokens < 1) {
                return false;
            }

            if (availableTokens.compareAndSet(currentTokens, currentTokens - 1))
                return true;

        }

    }

    private void refillToken(long currentSimulatedTime) {

        while (true) {
            long lastTripTimeLocal = lastTripTime.get();
            long availableTokenLocal = availableTokens.get();
            long elapsedTime = (currentSimulatedTime - lastTripTimeLocal);
            long tokensToAdd = elapsedTime * tokensPerSecond / 1000;
            if (tokensToAdd > 0) {
                if (availableTokens.compareAndSet(availableTokenLocal, Math.min(maxCapacity, availableTokenLocal + tokensToAdd)))
                    lastTripTime.compareAndSet(lastTripTimeLocal, currentSimulatedTime);
            } else  {
                return;
            }
        }

    }
}


class ServiceA {

    private Map<String, TokenRateLimiter> cache = new ConcurrentHashMap<>();

    public boolean callAPI(String clientID, long currentSimulatedTime) {
        TokenRateLimiter tokenRateLimiter = cache.computeIfAbsent(clientID, id -> new TokenRateLimiter());
        return tokenRateLimiter.throttle(currentSimulatedTime);
    }


}

class Client {
    public static void main(String gg[]) {

        long currentSimulatedTime = 0;


        ServiceA serviceA = new ServiceA();
        serviceA.callAPI("firstclient", currentSimulatedTime);
        serviceA.callAPI("firstclient", currentSimulatedTime);
        if(serviceA.callAPI("firstclient", currentSimulatedTime + 1000)) {
            System.out.println("passed 1st check");
        }

    }

}
