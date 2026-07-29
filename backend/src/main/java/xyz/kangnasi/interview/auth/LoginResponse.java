package xyz.kangnasi.interview.auth;

public record LoginResponse(String token, UserSummary user) {
}
