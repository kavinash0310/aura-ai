package ai.aura.api.api;

import ai.aura.api.application.ResearchDocumentQuestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentQuestionController {
    private final ResearchDocumentQuestionService questions;

    public DocumentQuestionController(ResearchDocumentQuestionService questions) {
        this.questions = questions;
    }

    @PostMapping("/answer")
    public DocumentQuestionResponse answer(@Valid @RequestBody DocumentQuestionRequest request) {
        return questions.answer(request);
    }
}
