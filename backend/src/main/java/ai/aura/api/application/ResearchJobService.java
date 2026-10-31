package ai.aura.api.application;

import ai.aura.api.domain.ResearchJob;
import ai.aura.api.domain.ResearchMode;
import ai.aura.api.persistence.ResearchJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ResearchJobService {

    private final ResearchJobRepository repository;

    public ResearchJobService(ResearchJobRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ResearchJob create(String subject, ResearchMode mode) {
        return repository.save(ResearchJob.requested(subject, mode));
    }

    @Transactional(readOnly = true)
    public ResearchJob get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResearchJobNotFoundException(id));
    }
}
