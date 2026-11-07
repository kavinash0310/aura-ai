package ai.aura.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AuraApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuraApiApplication.class, args);
    }
}
