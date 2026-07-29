package xyz.kangnasi.interview.auth;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TokenBlacklist {

    private static final Instant NEVER_EXPIRES = Instant.MAX;

    private final Map<String, Instant> revokedTokens = new ConcurrentHashMap<>();

    public void revoke(String tokenId, Instant expiresAt) {
        cleanup();
        if (tokenId != null) {
            Instant revokedUntil = expiresAt == null ? NEVER_EXPIRES : expiresAt;
            if (revokedUntil.isAfter(Instant.now())) {
                revokedTokens.put(tokenId, revokedUntil);
            }
        }
    }

    public boolean isRevoked(String tokenId) {
        cleanup();
        return tokenId != null && revokedTokens.containsKey(tokenId);
    }

    private void cleanup() {
        Instant now = Instant.now();
        revokedTokens.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }
}
