package xyz.kangnasi.interview.auth;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import xyz.kangnasi.interview.user.AppUser;
import xyz.kangnasi.interview.user.UserRole;

public record UserPrincipal(
        Long id,
        String email,
        String nickname,
        UserRole role,
        String tokenId,
        Instant tokenExpiresAt
) {

    public static UserPrincipal from(AppUser user, JwtClaims claims) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                claims.tokenId(),
                claims.expiresAt()
        );
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
