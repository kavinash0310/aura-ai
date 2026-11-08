package ai.aura.api.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ResearchSearchRecovery implements ApplicationRunner {

    private final ResearchJobService jobs;

    public ResearchSearchRecovery(ResearchJobService jobs) {
        this.jobs = jobs;
    }

    @Override
    public void run(ApplicationArguments args) {
        jobs.recoverInterruptedSearches();
    }
}
