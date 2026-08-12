package xyz.kangnasi.interview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ImportRuntimeHints;
import xyz.kangnasi.interview.config.NativeRuntimeHints;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableDiscoveryClient
@EnableFeignClients
@LoadBalancerClients({
        @LoadBalancerClient(name = "codex-chat-service"),
        @LoadBalancerClient(name = "email-service")
})
@ImportRuntimeHints(NativeRuntimeHints.class)
public class InterviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewApplication.class, args);
    }
}
