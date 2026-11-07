package ai.aura.api.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/session")
public class SessionController {

    @GetMapping
    public Map<String, Boolean> session() {
        return Map.of("authenticated", true);
    }
}
