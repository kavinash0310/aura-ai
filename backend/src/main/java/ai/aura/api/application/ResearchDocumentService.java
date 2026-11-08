package ai.aura.api.application;

import ai.aura.api.domain.ResearchDocument;
import ai.aura.api.persistence.ResearchDocumentRepository;
import ai.aura.api.persistence.ResearchDocumentSearch;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ResearchDocumentService {
    public static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final long MAX_DOCUMENTS = 100;
    private static final int MAX_TITLE_LENGTH = 300;
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final int MAX_SEARCH_RESULTS = 10;

    private final ResearchDocumentRepository repository;
    private final ResearchDocumentSearch documentSearch;
    private final PdfTextExtractor pdfTextExtractor;

    public ResearchDocumentService(ResearchDocumentRepository repository, ResearchDocumentSearch documentSearch,
                                   PdfTextExtractor pdfTextExtractor) {
        this.repository = repository;
        this.documentSearch = documentSearch;
        this.pdfTextExtractor = pdfTextExtractor;
    }

    @Transactional
    public ResearchDocument importDocument(MultipartFile file, String suppliedTitle) {
        if (repository.count() >= MAX_DOCUMENTS) {
            throw new InvalidDocumentException("The local library holds up to 100 documents. Delete one before importing another.");
        }
        if (file == null || file.isEmpty()) throw new InvalidDocumentException("Choose a non-empty .txt, .md, or .pdf file.");
        if (file.getSize() > MAX_FILE_BYTES) throw new InvalidDocumentException("Document files must be 1 MB or smaller.");
        String filename = safeFilename(file.getOriginalFilename());
        String extension = extension(filename);
        String mediaType = switch (extension) {
            case "txt" -> "text/plain; charset=utf-8";
            case "md" -> "text/markdown; charset=utf-8";
            case "pdf" -> "application/pdf";
            default -> throw new InvalidDocumentException("Only UTF-8 .txt, .md, and text-based .pdf files are supported.");
        };
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new InvalidDocumentException("The uploaded document could not be read.");
        }
        if (bytes.length > MAX_FILE_BYTES) throw new InvalidDocumentException("Document files must be 1 MB or smaller.");
        String content = extension.equals("pdf")
                ? pdfTextExtractor.extract(bytes)
                : decodeUtf8(bytes).replace("\r\n", "\n").replace('\r', '\n');
        if (content.isBlank() || content.codePoints().anyMatch(character -> Character.isISOControl(character)
                && character != '\n' && character != '\t' && character != '\f')) {
            throw new InvalidDocumentException("The document must contain readable text.");
        }
        String title = suppliedTitle == null || suppliedTitle.isBlank()
                ? defaultTitle(filename)
                : suppliedTitle.strip();
        if (title.length() > MAX_TITLE_LENGTH || title.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidDocumentException("Document title must be 1 to 300 readable characters.");
        }
        return repository.save(ResearchDocument.create(title, filename, mediaType, sha256(content), content, Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<ResearchDocument> list() {
        return repository.findAllByOrderByUploadedAtDesc(PageRequest.of(0, 100));
    }

    @Transactional(readOnly = true)
    public ResearchDocument get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResearchDocumentNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<ResearchDocument> search(String query) {
        String normalized = query == null ? "" : query.strip();
        if (normalized.length() < 2 || normalized.length() > 100
                || !normalized.matches("[\\p{L}\\p{N} .,_-]+")) {
            throw new InvalidDocumentException("Search must be 2 to 100 letters, numbers, spaces, periods, commas, underscores, or hyphens.");
        }
        return documentSearch.searchExactPhrase(normalized, PageRequest.of(0, MAX_SEARCH_RESULTS));
    }

    @Transactional
    public void delete(UUID id) {
        ResearchDocument document = get(id);
        repository.delete(document);
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new InvalidDocumentException("Documents must use valid UTF-8 encoding.");
        }
    }

    private static String safeFilename(String supplied) {
        if (supplied == null || supplied.isBlank()) throw new InvalidDocumentException("The uploaded file needs a name.");
        String base = supplied.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).strip();
        if (base.isBlank() || base.equals(".") || base.equals("..") || base.length() > MAX_FILENAME_LENGTH
                || base.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidDocumentException("The uploaded file name is invalid or too long.");
        }
        return base;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String defaultTitle(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
