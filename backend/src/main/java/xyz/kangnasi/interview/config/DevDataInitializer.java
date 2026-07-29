package xyz.kangnasi.interview.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.kangnasi.interview.user.AppUser;
import xyz.kangnasi.interview.user.UserRepository;
import xyz.kangnasi.interview.user.UserRole;

@Configuration
public class DevDataInitializer {

    @Bean
    CommandLineRunner seedUsers(UserRepository userRepository) {
        return args -> {
            seedUser(userRepository, "admin@example.com", "管理员", UserRole.ADMIN);
            seedUser(userRepository, "user1@example.com", "用户", UserRole.USER);
        };
    }

    private void seedUser(UserRepository userRepository, String email, String nickname, UserRole role) {
        userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(AppUser.create(email, nickname, role)));
    }
}
