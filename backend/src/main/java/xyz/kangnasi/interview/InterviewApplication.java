package xyz.kangnasi.interview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.ImportRuntimeHints;
import xyz.kangnasi.interview.config.NativeRuntimeHints;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ImportRuntimeHints(NativeRuntimeHints.class)
public class InterviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewApplication.class, args);
    }
}
