package xyz.kangnasi.interview.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.kangnasi.interview.common.AppException;
import xyz.kangnasi.interview.user.UserRepository;
import xyz.kangnasi.interview.user.UserStatus;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            TokenBlacklist tokenBlacklist,
            UserRepository userRepository
    ) {
        this.jwtService = jwtService;
        this.tokenBlacklist = tokenBlacklist;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        authenticateBearerToken(request);
        filterChain.doFilter(request, response);
    }

    private void authenticateBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            return;
        }

        try {
            JwtClaims claims = jwtService.parse(token);
            if (tokenBlacklist.isRevoked(claims.tokenId())) {
                return;
            }

            userRepository.findByEmail(claims.email())
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                    .ifPresent(user -> {
                        UserPrincipal principal = UserPrincipal.from(user, claims);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        } catch (AppException exception) {
            SecurityContextHolder.clearContext();
        }
    }
}
