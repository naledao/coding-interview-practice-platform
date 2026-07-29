package xyz.kangnasi.interview.auth;

import java.time.Instant;

public class LoginLockedException extends RuntimeException {

    private final Instant lockedUntil;

    public LoginLockedException(Instant lockedUntil) {
        super("登录失败次数过多，请稍后再试");
        this.lockedUntil = lockedUntil;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
