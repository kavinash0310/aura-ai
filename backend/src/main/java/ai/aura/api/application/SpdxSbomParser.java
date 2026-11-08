package ai.aura.api.application;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SpdxSbomParser {
    private static final String SUPPORTED_VERSION = "SPDX-2.3";
    private static final int MAX_PACKAGES = 10_000;
    private static final int MAX_EXTERNAL_REFERENCES = 10_000;
    private static final int MAX_AFFECTED_REFERENCES = 10_000;
    private static final int MAX_DISTINCT_CVES = 25;
    private static final Pattern EXACT_CVE = Pattern.compile("(?i)^CVE-[0-9]{4}-[0-9]{4,}$");
    private static final Pattern NVD_CVE_URL = Pattern.compile(
            "(?i)^https://nvd\\.nist\\.gov/vuln/detail/(CVE-[0-9]{4}-[0-9]{4,})/?$");

    private final JsonMapper jsonMapper;

    public SpdxSbomParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public ParsedSbom parse(byte[] input) {
        if (input == null || input.length == 0) {
            throw new InvalidSbomException("Choose a non-empty SPDX JSON SBOM.");
        }
        if (input.length > CycloneDxSbomParser.MAX_FILE_BYTES) {
            throw new InvalidSbomException("SPDX JSON SBOM files must be 2 MB or smaller.");
        }

        JsonNode root;
        try {
            root = jsonMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(input);
        } catch (JacksonException exception) {
            throw new InvalidSbomException("The file is not valid JSON.");
        }
        if (root == null || !root.isObject() || !"SPDX-2.3".equals(root.path("spdxVersion").asText())) {
            throw new InvalidSbomException("The JSON file must be an SPDX 2.3 document.");
        }

        JsonNode packages = root.path("packages");
        if (!packages.isArray()) {
            throw new InvalidSbomException("SPDX packages must be a JSON array.");
        }
        if (packages.size() > MAX_PACKAGES) {
            throw new InvalidSbomException("SBOM contains more than 10,000 packages.");
        }

        Map<String, ParsedSbom.Component> componentsById = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> referencesByCve = new LinkedHashMap<>();
        int externalReferenceCount = 0;
        int affectedReferenceCount = 0;
        for (JsonNode spdxPackage : packages) {
            if (!spdxPackage.isObject()) {
                throw new InvalidSbomException("Each SPDX package must be a JSON object.");
            }
            String spdxId = spdxPackage.path("SPDXID").asText("");
            if (spdxId.isBlank()) {
                throw new InvalidSbomException("Each SPDX package must have an SPDXID.");
            }
            String name = spdxPackage.path("name").asText("");
            String version = spdxPackage.path("versionInfo").asText("");
            JsonNode externalRefs = spdxPackage.path("externalRefs");
            if (!externalRefs.isMissingNode() && !externalRefs.isNull() && !externalRefs.isArray()) {
                throw new InvalidSbomException("SPDX package externalRefs must be a JSON array.");
            }

            String purl = "";
            if (externalRefs.isArray()) {
                for (JsonNode externalRef : externalRefs) {
                    if (!externalRef.isObject()) {
                        throw new InvalidSbomException("Each SPDX external reference must be a JSON object.");
                    }
                    externalReferenceCount++;
                    if (externalReferenceCount > MAX_EXTERNAL_REFERENCES) {
                        throw new InvalidSbomException("SBOM contains more than 10,000 external references.");
                    }
                    String category = externalRef.path("referenceCategory").asText("");
                    String type = externalRef.path("referenceType").asText("");
                    String locator = externalRef.path("referenceLocator").asText("");
                    if ("purl".equalsIgnoreCase(type) && locator.startsWith("pkg:")) {
                        purl = locator;
                    }
                    if (!"SECURITY".equalsIgnoreCase(category) || !"advisory".equalsIgnoreCase(type)) {
                        continue;
                    }
                    String cve = declaredCve(locator);
                    if (cve == null) {
                        continue;
                    }
                    LinkedHashSet<String> affectedReferences = referencesByCve.computeIfAbsent(
                            cve, ignored -> new LinkedHashSet<>());
                    if (affectedReferences.add(spdxId)) {
                        affectedReferenceCount++;
                        if (affectedReferenceCount > MAX_AFFECTED_REFERENCES) {
                            throw new InvalidSbomException("SBOM contains more than 10,000 affected package references.");
                        }
                    }
                    if (referencesByCve.size() > MAX_DISTINCT_CVES) {
                        throw new InvalidSbomException("SBOM contains more than 25 distinct CVE IDs; split it into smaller files.");
                    }
                }
            }
            ParsedSbom.Component component = new ParsedSbom.Component(spdxId, name, version, purl);
            if (componentsById.putIfAbsent(spdxId, component) != null) {
                throw new InvalidSbomException("SPDX package SPDXID values must be unique.");
            }
        }

        List<ParsedSbom.DeclaredVulnerability> vulnerabilities = new ArrayList<>();
        referencesByCve.forEach((cve, references) -> vulnerabilities.add(new ParsedSbom.DeclaredVulnerability(
                cve,
                List.copyOf(references),
                references.stream().map(componentsById::get).filter(component -> component != null).toList()
        )));
        return new ParsedSbom("SPDX JSON", SUPPORTED_VERSION, packages.size(), vulnerabilities);
    }

    private static String declaredCve(String locator) {
        String candidate = locator;
        if (!EXACT_CVE.matcher(candidate).matches()) {
            Matcher nvdUrl = NVD_CVE_URL.matcher(candidate);
            if (!nvdUrl.matches()) {
                return null;
            }
            candidate = nvdUrl.group(1);
        }
        return candidate.toUpperCase(Locale.ROOT);
    }
}
