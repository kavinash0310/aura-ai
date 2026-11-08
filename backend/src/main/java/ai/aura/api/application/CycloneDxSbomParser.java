package ai.aura.api.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class CycloneDxSbomParser {
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_COMPONENTS = 10_000;
    private static final int MAX_VULNERABILITY_RECORDS = 2_000;
    private static final int MAX_AFFECTED_REFERENCES = 10_000;
    private static final Set<String> SUPPORTED_SPEC_VERSIONS = Set.of("1.3", "1.4", "1.5", "1.6", "1.7");

    private final JsonMapper jsonMapper;

    public CycloneDxSbomParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public ParsedCycloneDxSbom parse(byte[] input) {
        if (input == null || input.length == 0) {
            throw new InvalidSbomException("Choose a non-empty CycloneDX JSON SBOM.");
        }
        if (input.length > MAX_FILE_BYTES) {
            throw new InvalidSbomException("CycloneDX JSON SBOM files must be 2 MB or smaller.");
        }

        JsonNode root;
        try {
            root = jsonMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(input);
        } catch (JacksonException exception) {
            throw new InvalidSbomException("The file is not valid JSON.");
        }
        if (root == null || !root.isObject() || !root.path("bomFormat").asText().equals("CycloneDX")) {
            throw new InvalidSbomException("The JSON file must be a CycloneDX BOM.");
        }
        String specVersion = root.path("specVersion").asText("");
        if (!SUPPORTED_SPEC_VERSIONS.contains(specVersion)) {
            throw new InvalidSbomException("Supported CycloneDX JSON versions are 1.3 through 1.7.");
        }

        ComponentCollection componentCollection = collectComponents(root);
        Map<String, ParsedCycloneDxSbom.Component> components = componentCollection.byBomRef();
        JsonNode vulnerabilities = root.path("vulnerabilities");
        if (!vulnerabilities.isMissingNode() && !vulnerabilities.isNull() && !vulnerabilities.isArray()) {
            throw new InvalidSbomException("CycloneDX vulnerabilities must be a JSON array.");
        }
        if (vulnerabilities.isArray() && vulnerabilities.size() > MAX_VULNERABILITY_RECORDS) {
            throw new InvalidSbomException("SBOM contains more than 2,000 vulnerability records.");
        }

        Map<String, LinkedHashSet<String>> refsByCve = new LinkedHashMap<>();
        int affectedReferenceCount = 0;
        if (vulnerabilities.isArray()) {
            for (JsonNode vulnerability : vulnerabilities) {
                if (!vulnerability.isObject()) {
                    throw new InvalidSbomException("Each CycloneDX vulnerability must be a JSON object.");
                }
                String cve = vulnerability.path("id").asText("").toUpperCase(Locale.ROOT);
                if (!cve.matches("CVE-[0-9]{4}-[0-9]{4,}")) {
                    continue;
                }
                LinkedHashSet<String> refs = refsByCve.computeIfAbsent(cve, ignored -> new LinkedHashSet<>());
                JsonNode affects = vulnerability.path("affects");
                if (!affects.isMissingNode() && !affects.isNull() && !affects.isArray()) {
                    throw new InvalidSbomException("CycloneDX vulnerability affects must be a JSON array.");
                }
                if (affects.isArray()) {
                    for (JsonNode affected : affects) {
                        if (!affected.isObject()) {
                            throw new InvalidSbomException("Each CycloneDX affected reference must be a JSON object.");
                        }
                        String bomRef = affected.path("ref").asText("");
                        if (!bomRef.isBlank()) {
                            if (refs.add(bomRef)) {
                                affectedReferenceCount++;
                                if (affectedReferenceCount > MAX_AFFECTED_REFERENCES) {
                                    throw new InvalidSbomException("SBOM contains more than 10,000 affected component references.");
                                }
                            }
                        }
                    }
                }
                if (refsByCve.size() > 25) {
                    throw new InvalidSbomException("SBOM contains more than 25 distinct CVE IDs; split it into smaller files.");
                }
            }
        }

        List<ParsedCycloneDxSbom.DeclaredVulnerability> parsedVulnerabilities = new ArrayList<>();
        refsByCve.forEach((cve, refs) -> {
            List<ParsedCycloneDxSbom.Component> affectedComponents = refs.stream()
                    .map(components::get)
                    .filter(component -> component != null)
                    .toList();
            parsedVulnerabilities.add(new ParsedCycloneDxSbom.DeclaredVulnerability(
                    cve, List.copyOf(refs), affectedComponents));
        });
        return new ParsedCycloneDxSbom(specVersion, componentCollection.count(), parsedVulnerabilities);
    }

    private static ComponentCollection collectComponents(JsonNode root) {
        Map<String, ParsedCycloneDxSbom.Component> components = new HashMap<>();
        ArrayDeque<JsonNode> pending = new ArrayDeque<>();
        JsonNode metadata = root.path("metadata");
        if (!metadata.isMissingNode() && !metadata.isNull() && !metadata.isObject()) {
            throw new InvalidSbomException("CycloneDX metadata must be a JSON object.");
        }
        JsonNode metadataComponent = metadata.path("component");
        if (metadataComponent.isObject()) {
            pending.add(metadataComponent);
        } else if (!metadataComponent.isMissingNode() && !metadataComponent.isNull()) {
            throw new InvalidSbomException("CycloneDX metadata.component must be a JSON object.");
        }
        JsonNode topLevel = root.path("components");
        if (topLevel.isArray()) {
            topLevel.forEach(pending::add);
        } else if (!topLevel.isMissingNode() && !topLevel.isNull()) {
            throw new InvalidSbomException("CycloneDX components must be a JSON array.");
        }

        int componentCount = 0;
        while (!pending.isEmpty()) {
            JsonNode node = pending.removeFirst();
            if (!node.isObject()) {
                throw new InvalidSbomException("Each CycloneDX component must be a JSON object.");
            }
            componentCount++;
            if (componentCount > MAX_COMPONENTS) {
                throw new InvalidSbomException("SBOM contains more than 10,000 components.");
            }
            String bomRef = node.path("bom-ref").asText("");
            if (!bomRef.isBlank()) {
                ParsedCycloneDxSbom.Component component = new ParsedCycloneDxSbom.Component(
                        bomRef,
                        node.path("name").asText(""),
                        node.path("version").asText(""),
                        node.path("purl").asText("")
                );
                if (components.putIfAbsent(bomRef, component) != null) {
                    throw new InvalidSbomException("CycloneDX component bom-ref values must be unique.");
                }
            }
            JsonNode children = node.path("components");
            if (children.isArray()) {
                children.forEach(pending::addLast);
            } else if (!children.isMissingNode() && !children.isNull()) {
                throw new InvalidSbomException("Nested CycloneDX components must be a JSON array.");
            }
            if (pending.size() > MAX_COMPONENTS) {
                throw new InvalidSbomException("SBOM contains too many nested components.");
            }
        }
        return new ComponentCollection(Map.copyOf(components), componentCount);
    }

    private record ComponentCollection(Map<String, ParsedCycloneDxSbom.Component> byBomRef, int count) {
    }
}
