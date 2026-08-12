package xyz.kangnasi.interview.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

class BackendNacosConfigurationTest {

    @Test
    void registersTheBackendForGatewayDiscoveryByDefault() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new FileSystemResource("src/main/resources/application.yml"));
        Properties properties = factory.getObject();

        assertEquals(
                "coding-interview-practice-platform-service",
                properties.getProperty("spring.application.name")
        );
        assertEquals(
                "${NACOS_DISCOVERY_REGISTER_ENABLED:true}",
                properties.getProperty("spring.cloud.nacos.discovery.register-enabled")
        );
    }
}
