package xyz.kangnasi.interview.auth;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LoginCodeStore {

    private final Map<String, LoginCode> codes = new ConcurrentHashMap<>();

    public void put(String email, String code, Instant expiresAt) {
        codes.put(email, new LoginCode(code, expiresAt));
    }

    public boolean consume(String email, String code) {
        LoginCode stored = codes.get(email);
        if (stored == null || !stored.expiresAt().isAfter(Instant.now()) || !stored.code().equals(code)) {
            return false;
        }
        codes.remove(email);
        return true;
    }

    private record LoginCode(String code, Instant expiresAt) {
    }
}
