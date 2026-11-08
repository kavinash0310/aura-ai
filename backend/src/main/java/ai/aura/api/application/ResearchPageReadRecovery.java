package ai.aura.api.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ResearchPageReadRecovery implements ApplicationRunner {
    private final ResearchPageReadAuditService audit;

    public ResearchPageReadRecovery(ResearchPageReadAuditService audit) {
        this.audit = audit;
    }

    @Override
    public void run(ApplicationArguments args) {
        audit.recoverInterruptedReads();
    }
}
