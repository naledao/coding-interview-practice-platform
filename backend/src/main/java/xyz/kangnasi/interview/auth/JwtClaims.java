package xyz.kangnasi.interview.auth;

import java.time.Instant;
import xyz.kangnasi.interview.user.UserRole;

public record JwtClaims(
        Long userId,
        String email,
        UserRole role,
        String tokenId,
        Instant issuedAt,
        Instant expiresAt
) {
}
