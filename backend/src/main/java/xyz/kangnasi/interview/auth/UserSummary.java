package xyz.kangnasi.interview.auth;

import xyz.kangnasi.interview.user.AppUser;
import xyz.kangnasi.interview.user.UserRole;

public record UserSummary(Long id, String email, String nickname, UserRole role) {

    public static UserSummary from(AppUser user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getNickname(), user.getRole());
    }
}
