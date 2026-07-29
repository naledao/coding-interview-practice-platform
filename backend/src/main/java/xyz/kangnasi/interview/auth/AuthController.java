package xyz.kangnasi.interview.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.kangnasi.interview.common.ApiResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody(required = false) LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/send-login-code")
    public ApiResponse<Void> sendLoginCode(@RequestBody(required = false) SendLoginCodeRequest request) {
        authService.sendLoginCode(request);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<UserSummary> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(authService.currentUser(principal));
    }

    @PatchMapping("/me/nickname")
    public ApiResponse<UserSummary> updateNickname(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) UpdateNicknameRequest request
    ) {
        return ApiResponse.ok(authService.updateNickname(principal, request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        authService.logout(principal);
        return ApiResponse.ok();
    }
}
