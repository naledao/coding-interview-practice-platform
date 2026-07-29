package xyz.kangnasi.interview.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import xyz.kangnasi.interview.common.AppException;
import xyz.kangnasi.interview.user.AppUser;
import xyz.kangnasi.interview.user.UserRole;

@Service
public class JwtService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long tokenTtlSeconds;
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder decoder = Base64.getUrlDecoder();

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${app.security.jwt-secret}") String secret,
            @Value("${app.security.token-ttl-seconds}") long tokenTtlSeconds
    ) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("app.security.jwt-secret must be at least 32 characters");
        }
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    public String generateToken(AppUser user) {
        try {
            Instant now = Instant.now();
            Instant expiresAt = tokenTtlSeconds > 0 ? now.plusSeconds(tokenTtlSeconds) : null;

            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", user.getEmail());
            payload.put("uid", user.getId());
            payload.put("role", user.getRole().name());
            payload.put("jti", UUID.randomUUID().toString());
            payload.put("iat", now.getEpochSecond());
            if (expiresAt != null) {
                payload.put("exp", expiresAt.getEpochSecond());
            }

            String encodedHeader = encodeJson(header);
            String encodedPayload = encodeJson(payload);
            String content = encodedHeader + "." + encodedPayload;
            return content + "." + encoder.encodeToString(sign(content));
        } catch (Exception exception) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "生成登录凭证失败");
        }
    }

    public JwtClaims parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw invalidToken();
            }

            String content = parts[0] + "." + parts[1];
            byte[] providedSignature = decoder.decode(parts[2]);
            if (!MessageDigest.isEqual(sign(content), providedSignature)) {
                throw invalidToken();
            }

            String payloadJson = new String(decoder.decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> payload = objectMapper.readValue(payloadJson, MAP_TYPE);

            Long userId = readLong(payload, "uid");
            String email = readString(payload, "sub");
            UserRole role = UserRole.valueOf(readString(payload, "role"));
            String tokenId = readString(payload, "jti");
            Instant issuedAt = Instant.ofEpochSecond(readLong(payload, "iat"));
            Instant expiresAt = tokenTtlSeconds > 0 ? Instant.ofEpochSecond(readLong(payload, "exp")) : null;

            if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
                throw invalidToken();
            }

            return new JwtClaims(userId, email, role, tokenId, issuedAt, expiresAt);
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidToken();
        }
    }

    private String encodeJson(Map<String, Object> json) throws Exception {
        return encoder.encodeToString(objectMapper.writeValueAsBytes(json));
    }

    private byte[] sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
    }

    private String readString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        throw invalidToken();
    }

    private Long readLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        throw invalidToken();
    }

    private AppException invalidToken() {
        return AppException.unauthorized("未登录或登录已过期");
    }
}
