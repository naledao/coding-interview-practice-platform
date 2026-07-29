package xyz.kangnasi.interview.auth;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LoginFailureLimiter {

    private final Map<String, FailureRecord> failures = new ConcurrentHashMap<>();

    public void ensureNotLocked(String email) {
        FailureRecord record = failures.get(email);
        if (record == null || record.lockedUntil() == null) {
            return;
        }

        if (record.lockedUntil().isAfter(Instant.now())) {
            throw new LoginLockedException(record.lockedUntil());
        }

        failures.remove(email);
    }

    public void recordFailure(String email, int maxAttempts, long lockSeconds) {
        Instant now = Instant.now();
        FailureRecord record = failures.compute(email, (key, current) -> {
            int attempts = current == null ? 1 : current.attempts() + 1;
            Instant lockedUntil = attempts >= maxAttempts ? now.plusSeconds(lockSeconds) : null;
            return new FailureRecord(attempts, lockedUntil);
        });

        if (record.lockedUntil() != null && record.lockedUntil().isAfter(now)) {
            throw new LoginLockedException(record.lockedUntil());
        }
    }

    public void reset(String email) {
        failures.remove(email);
    }

    private record FailureRecord(int attempts, Instant lockedUntil) {
    }
}
