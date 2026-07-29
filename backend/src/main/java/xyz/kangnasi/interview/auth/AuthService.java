package xyz.kangnasi.interview.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.kangnasi.interview.common.AppException;
import xyz.kangnasi.interview.user.AppUser;
import xyz.kangnasi.interview.user.UserRepository;
import xyz.kangnasi.interview.user.UserRole;
import xyz.kangnasi.interview.user.UserStatus;

@Service
public class AuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final LoginCodeStore loginCodeStore;
    private final MailLoginCodeSender mailLoginCodeSender;
    private final LoginFailureLimiter loginFailureLimiter;
    private final SecureRandom secureRandom = new SecureRandom();
    private final int loginCodeTtlMinutes;
    private final boolean devLoginCodeEnabled;
    private final String devLoginCode;
    private final int maxLoginFailures;
    private final long loginFailureLockSeconds;

    public AuthService(
            UserRepository userRepository,
            JwtService jwtService,
            TokenBlacklist tokenBlacklist,
            LoginCodeStore loginCodeStore,
            MailLoginCodeSender mailLoginCodeSender,
            LoginFailureLimiter loginFailureLimiter,
            @Value("${app.auth.login-code-ttl-minutes}") int loginCodeTtlMinutes,
            @Value("${app.auth.dev-login-code-enabled:false}") boolean devLoginCodeEnabled,
            @Value("${app.auth.dev-login-code:}") String devLoginCode,
            @Value("${app.auth.max-login-failures:5}") int maxLoginFailures,
            @Value("${app.auth.login-failure-lock-seconds:300}") long loginFailureLockSeconds
    ) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.tokenBlacklist = tokenBlacklist;
        this.loginCodeStore = loginCodeStore;
        this.mailLoginCodeSender = mailLoginCodeSender;
        this.loginFailureLimiter = loginFailureLimiter;
        this.loginCodeTtlMinutes = loginCodeTtlMinutes;
        this.devLoginCodeEnabled = devLoginCodeEnabled;
        this.devLoginCode = devLoginCode;
        this.maxLoginFailures = maxLoginFailures;
        this.loginFailureLockSeconds = loginFailureLockSeconds;
    }

    public void sendLoginCode(SendLoginCodeRequest request) {
        String email = normalizeEmail(request == null ? null : request.email());
        String code = devLoginCodeEnabled && !isBlank(devLoginCode) ? devLoginCode : generateNumericCode();

        if (!devLoginCodeEnabled) {
            mailLoginCodeSender.send(email, code, loginCodeTtlMinutes);
        }

        loginCodeStore.put(email, code, Instant.now().plusSeconds(loginCodeTtlMinutes * 60L));
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        if (request == null || isBlank(request.email()) || isBlank(request.code())) {
            throw AppException.badRequest("请输入邮箱和验证码");
        }

        String email = normalizeEmail(request.email());

        loginFailureLimiter.ensureNotLocked(email);
        if (!loginCodeStore.consume(email, request.code().trim())) {
            loginFailureLimiter.recordFailure(email, maxLoginFailures, loginFailureLockSeconds);
            throw AppException.unauthorized("验证码错误或已过期");
        }
        loginFailureLimiter.reset(email);

        AppUser user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(AppUser.create(email, "用户", UserRole.USER)));

        if (user.getStatus() == UserStatus.DISABLED) {
            throw AppException.forbidden("账号已被禁用");
        }

        return new LoginResponse(jwtService.generateToken(user), UserSummary.from(user));
    }

    @Transactional(readOnly = true)
    public UserSummary currentUser(UserPrincipal principal) {
        if (principal == null) {
            throw AppException.unauthorized("未登录或登录已过期");
        }

        AppUser user = userRepository.findById(principal.id())
                .filter(found -> found.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> AppException.unauthorized("未登录或登录已过期"));

        return UserSummary.from(user);
    }

    @Transactional
    public UserSummary updateNickname(UserPrincipal principal, UpdateNicknameRequest request) {
        if (principal == null) {
            throw AppException.unauthorized("未登录或登录已过期");
        }
        if (request == null || isBlank(request.nickname())) {
            throw AppException.badRequest("昵称不能为空");
        }

        String nickname = request.nickname().trim();
        if (nickname.length() > 64) {
            throw AppException.badRequest("昵称不能超过64个字符");
        }

        AppUser user = userRepository.findById(principal.id())
                .filter(found -> found.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> AppException.unauthorized("未登录或登录已过期"));

        user.changeNickname(nickname);
        return UserSummary.from(user);
    }

    public void logout(UserPrincipal principal) {
        if (principal != null) {
            tokenBlacklist.revoke(principal.tokenId(), principal.tokenExpiresAt());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeEmail(String email) {
        if (isBlank(email)) {
            throw AppException.badRequest("邮箱格式不正确");
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw AppException.badRequest("邮箱格式不正确");
        }
        return normalized;
    }

    private String generateNumericCode() {
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }
}
