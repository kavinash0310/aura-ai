package ai.aura.api.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "aura.auth")
@Validated
public record AuraAuthProperties(
        @NotBlank String username,
        @NotBlank @Size(min = 24) String password
) {
}
