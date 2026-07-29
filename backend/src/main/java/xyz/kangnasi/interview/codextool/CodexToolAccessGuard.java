package xyz.kangnasi.interview.codextool;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.kangnasi.interview.common.AppException;

@Component
public class CodexToolAccessGuard {

    private final String toolToken;

    public CodexToolAccessGuard(@Value("${app.codex.tool-token:}") String toolToken) {
        this.toolToken = toolToken == null ? "" : toolToken.trim();
    }

    public void requireAllowed(HttpServletRequest request) {
        if (isLoopback(request.getRemoteAddr())) {
            return;
        }
        String requestToken = extractToken(request);
        if (!toolToken.isBlank() && toolToken.equals(requestToken)) {
            return;
        }
        throw AppException.forbidden("Codex 工具仅允许本机访问");
    }

    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        String legacyToken = request.getHeader("X-Codex-Tool-Token");
        return legacyToken == null ? "" : legacyToken.trim();
    }

    private boolean isLoopback(String remoteAddr) {
        return "127.0.0.1".equals(remoteAddr)
                || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                || "::1".equals(remoteAddr);
    }
}
