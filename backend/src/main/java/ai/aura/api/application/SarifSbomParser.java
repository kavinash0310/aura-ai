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
import java.util.regex.Pattern;

@Component
public class SarifSbomParser {
    private static final String SUPPORTED_VERSION = "2.1.0";
    private static final int MAX_RUNS = 100;
    private static final int MAX_RESULTS = 10_000;
    private static final int MAX_DISTINCT_CVES = 25;
    private static final int MAX_AFFECTED_REFERENCES = 10_000;
    private static final Pattern EXACT_CVE = Pattern.compile("(?i)^CVE-[0-9]{4}-[0-9]{4,}$");

    private final JsonMapper jsonMapper;

    public SarifSbomParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public ParsedSbom parse(byte[] input) {
        if (input == null || input.length == 0) {
            throw new InvalidSbomException("Choose a non-empty SARIF JSON report.");
        }
        if (input.length > CycloneDxSbomParser.MAX_FILE_BYTES) {
            throw new InvalidSbomException("SARIF JSON reports must be 2 MB or smaller.");
        }

        JsonNode root;
        try {
            root = jsonMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(input);
        } catch (JacksonException exception) {
            throw new InvalidSbomException("The file is not valid JSON.");
        }
        if (root == null || !root.isObject() || !SUPPORTED_VERSION.equals(root.path("version").asText())) {
            throw new InvalidSbomException("The JSON file must be a SARIF 2.1.0 log.");
        }

        JsonNode runs = root.path("runs");
        if (!runs.isArray()) {
            throw new InvalidSbomException("SARIF runs must be a JSON array.");
        }
        if (runs.size() > MAX_RUNS) {
            throw new InvalidSbomException("SARIF log contains more than 100 runs.");
        }

        int resultCount = 0;
        int affectedReferenceCount = 0;
        Map<String, LinkedHashSet<String>> referencesByCve = new LinkedHashMap<>();
        Map<String, ParsedSbom.Component> findingsByReference = new LinkedHashMap<>();
        for (int runIndex = 0; runIndex < runs.size(); runIndex++) {
            JsonNode run = runs.get(runIndex);
            if (!run.isObject()) {
                throw new InvalidSbomException("Each SARIF run must be a JSON object.");
            }
            List<String> ruleIds = driverRuleIds(run);
            JsonNode results = run.path("results");
            if (!results.isMissingNode() && !results.isNull() && !results.isArray()) {
                throw new InvalidSbomException("SARIF run results must be a JSON array.");
            }
            if (!results.isArray()) {
                continue;
            }
            for (int resultIndex = 0; resultIndex < results.size(); resultIndex++) {
                JsonNode result = results.get(resultIndex);
                if (!result.isObject()) {
                    throw new InvalidSbomException("Each SARIF result must be a JSON object.");
                }
                resultCount++;
                if (resultCount > MAX_RESULTS) {
                    throw new InvalidSbomException("SARIF log contains more than 10,000 results.");
                }

                String ruleId = result.path("ruleId").asText("");
                if (ruleId.isBlank()) {
                    JsonNode ruleIndexNode = result.path("ruleIndex");
                    if (ruleIndexNode.isIntegralNumber()) {
                        int ruleIndexValue = ruleIndexNode.asInt(-1);
                        if (ruleIndexValue >= 0 && ruleIndexValue < ruleIds.size()) {
                            ruleId = ruleIds.get(ruleIndexValue);
                        }
                    }
                }
                if (!EXACT_CVE.matcher(ruleId).matches()) {
                    continue;
                }

                String cve = ruleId.toUpperCase(Locale.ROOT);
                String findingReference = "run-" + (runIndex + 1) + "/result-" + (resultIndex + 1);
                String level = result.path("level").asText("");
                findingsByReference.put(findingReference, new ParsedSbom.Component(
                        findingReference, cve, level, ""));
                LinkedHashSet<String> references = referencesByCve.computeIfAbsent(cve, ignored -> new LinkedHashSet<>());
                if (references.add(findingReference)) {
                    affectedReferenceCount++;
                    if (affectedReferenceCount > MAX_AFFECTED_REFERENCES) {
                        throw new InvalidSbomException("SARIF log contains more than 10,000 CVE finding references.");
                    }
                }
                if (referencesByCve.size() > MAX_DISTINCT_CVES) {
                    throw new InvalidSbomException("SARIF log contains more than 25 distinct CVE IDs; split it into smaller files.");
                }
            }
        }

        List<ParsedSbom.DeclaredVulnerability> vulnerabilities = new ArrayList<>();
        referencesByCve.forEach((cve, references) -> vulnerabilities.add(new ParsedSbom.DeclaredVulnerability(
                cve,
                List.copyOf(references),
                references.stream().map(findingsByReference::get).filter(component -> component != null).toList()
        )));
        return new ParsedSbom("SARIF JSON", SUPPORTED_VERSION, resultCount, vulnerabilities);
    }

    private static List<String> driverRuleIds(JsonNode run) {
        JsonNode tool = run.path("tool");
        if (tool.isMissingNode() || tool.isNull()) {
            return List.of();
        }
        if (!tool.isObject()) {
            throw new InvalidSbomException("SARIF run tool must be a JSON object.");
        }
        JsonNode driver = tool.path("driver");
        if (driver.isMissingNode() || driver.isNull()) {
            return List.of();
        }
        if (!driver.isObject()) {
            throw new InvalidSbomException("SARIF tool driver must be a JSON object.");
        }
        JsonNode rules = driver.path("rules");
        if (!rules.isMissingNode() && !rules.isNull() && !rules.isArray()) {
            throw new InvalidSbomException("SARIF driver rules must be a JSON array.");
        }
        if (!rules.isArray()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(rules.size());
        for (JsonNode rule : rules) {
            if (!rule.isObject()) {
                throw new InvalidSbomException("Each SARIF driver rule must be a JSON object.");
            }
            ids.add(rule.path("id").asText(""));
        }
        return List.copyOf(ids);
    }
}
