package xyz.kangnasi.interview.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import xyz.kangnasi.interview.InterviewApplication;

class NativeRuntimeHintsTest {

    @Test
    void declaresDownstreamLoadBalancerClients() {
        LoadBalancerClients clients = InterviewApplication.class.getAnnotation(LoadBalancerClients.class);

        assertNotNull(clients);
        assertEquals(
                List.of("codex-chat-service", "email-service"),
                Arrays.stream(clients.value()).map(client -> client.name()).sorted().toList()
        );
    }

    @Test
    void registersNacosDiscoveryResponseTypes() throws NoSuchMethodException {
        RuntimeHints hints = new RuntimeHints();
        new NativeRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertTrue(RuntimeHintsPredicates.reflection()
                .onConstructor(ServiceInfo.class.getDeclaredConstructor())
                .test(hints));
        assertTrue(RuntimeHintsPredicates.reflection()
                .onConstructor(Instance.class.getDeclaredConstructor())
                .test(hints));
        for (var method : List.of(
                ServiceInfo.class.getMethod("setHosts", List.class),
                ServiceInfo.class.getMethod("setName", String.class),
                Instance.class.getMethod("setIp", String.class),
                Instance.class.getMethod("setPort", int.class),
                Instance.class.getMethod("setHealthy", boolean.class)
        )) {
            assertTrue(RuntimeHintsPredicates.reflection().onMethod(method).test(hints));
        }
    }
}
