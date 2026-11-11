package ai.aura.api.application;

import ai.aura.api.api.DocumentQuestionRequest;
import ai.aura.api.api.DocumentQuestionResponse;
import ai.aura.api.domain.ResearchDocument;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ResearchDocumentQuestionService {
    private static final int MAX_SOURCES = 5;
    private static final int CONTEXT_BEFORE = 200;
    private static final int CONTEXT_AFTER = 300;

    private final ResearchDocumentService documents;
    private final AiReportClient aiReportClient;

    public ResearchDocumentQuestionService(ResearchDocumentService documents, AiReportClient aiReportClient) {
        this.documents = documents;
        this.aiReportClient = aiReportClient;
    }

    public DocumentQuestionResponse answer(DocumentQuestionRequest request) {
        String question = request.question().strip();
        String searchQuery = request.searchQuery().strip();
        if (question.isBlank() || question.length() > 500) {
            throw new InvalidDocumentException("Enter a question of up to 500 characters.");
        }
        if (searchQuery.length() < 2 || searchQuery.length() > 100
                || !searchQuery.matches("[\\p{L}\\p{N} .,_-]+")) {
            throw new InvalidDocumentException("Select results from a valid document phrase search.");
        }
        List<DocumentQuestionRequest.Selection> selections = request.selections();
        if (selections == null || selections.isEmpty() || selections.size() > MAX_SOURCES) {
            throw new InvalidDocumentException("Choose one to five document search results to answer from.");
        }
        Set<UUID> selectedIds = new HashSet<>();
        List<AiDocumentQuestionRequest.Source> sources = new ArrayList<>();
        Map<String, DocumentQuestionResponse.Citation> citations = new LinkedHashMap<>();
        for (DocumentQuestionRequest.Selection selection : selections) {
            if (!selectedIds.add(selection.documentId())) {
                throw new InvalidDocumentException("Choose each document search result only once.");
            }
            ResearchDocument document = documents.get(selection.documentId());
            String content = document.getContent();
            int start = selection.startOffset();
            int end = selection.endOffset();
            if (start < 0 || end <= start || end - start != searchQuery.length()
                    || end > content.length()
                    || !content.regionMatches(true, start, searchQuery, 0, searchQuery.length())) {
                throw new InvalidDocumentException("A selected search result no longer matches its document citation.");
            }

            int pageStart = content.lastIndexOf('\f', Math.max(-1, start - 1)) + 1;
            int nextPageBreak = content.indexOf('\f', end);
            int pageEnd = nextPageBreak < 0 ? content.length() : nextPageBreak;
            int excerptStart = Math.max(pageStart, start - CONTEXT_BEFORE);
            int excerptEnd = Math.min(pageEnd, end + CONTEXT_AFTER);
            Integer page = "application/pdf".equals(document.getMediaType())
                    ? 1 + (int) content.substring(0, start).chars().filter(value -> value == '\f').count()
                    : null;
            String citation = "aura-doc://" + document.getId() + "#chars=" + start + "-" + end;
            if (page != null) {
                citation = "aura-doc://" + document.getId() + "#page=" + page + "&chars=" + start + "-" + end;
            }
            String id = document.getId().toString();
            sources.add(new AiDocumentQuestionRequest.Source(
                    id, document.getTitle(), document.getOriginalFilename(),
                    document.getVersionNumber(), page, start, end, excerptStart, excerptEnd,
                    content.substring(excerptStart, excerptEnd)
            ));
            citations.put(id, new DocumentQuestionResponse.Citation(
                    id, document.getTitle(), document.getOriginalFilename(), document.getVersionNumber(),
                    page, start, end, citation
            ));
        }

        AiDocumentQuestionResponse generated = aiReportClient.answerDocuments(
                new AiDocumentQuestionRequest(question, List.copyOf(sources))
        );
        Set<String> referencedIds = generated.evidence().stream()
                .flatMap(item -> item.sourceIds().stream())
                .collect(java.util.stream.Collectors.toSet());
        if (!citations.keySet().containsAll(referencedIds)) {
            throw new AiServiceUnavailableException();
        }
        List<DocumentQuestionResponse.Citation> usedCitations = citations.entrySet().stream()
                .filter(entry -> referencedIds.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        List<DocumentQuestionResponse.EvidenceItem> evidence = generated.evidence().stream()
                .map(item -> new DocumentQuestionResponse.EvidenceItem(
                        item.text(), item.sourceIds(), item.evidenceQuotes().stream()
                        .map(quote -> new DocumentQuestionResponse.EvidenceQuote(quote.sourceId(), quote.quote()))
                        .toList()
                ))
                .toList();
        return new DocumentQuestionResponse(
                generated.generatedAt(), generated.provider(), generated.model(), generated.question(),
                generated.answer(), evidence, usedCitations, generated.limitations(), generated.coverageNote(),
                generated.modelRun()
        );
    }
}
