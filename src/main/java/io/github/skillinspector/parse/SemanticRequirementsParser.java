package io.github.skillinspector.parse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.skillinspector.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class SemanticRequirementsParser {
    private static final long MAX_FILE_BYTES = 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 500;
    private static final int MAX_MATCHED_LENGTH = 240;
    private static final int MAX_REQUIREMENTS = 1000;
    private static final Pattern SENSITIVE_LITERAL = Pattern.compile(
            "(?i)([A-Z0-9_]*(?:TOKEN|SECRET|PASSWORD|PASSWD|API_KEY)[A-Z0-9_]*\\s*=\\s*)(\\\"[^\\\"]*\\\"|'[^']*'|[^\\s]+)");
    private static final Pattern SENSITIVE_TOKEN = Pattern.compile(
            "(?i)\\b(?:bearer\\s+)?(?:sk-|gh[opusr]_|xox[baprs]-)[A-Za-z0-9._-]{8,}");
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public List<Requirement> parse(Path input) {
        Path normalized = input.toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized))
                throw new SkillParseException("Requirements input must be a regular non-symbolic-link file: " + normalized);
            if (Files.size(normalized) > MAX_FILE_BYTES)
                throw new SkillParseException("Requirements input exceeds 1 MiB: " + normalized);
            Handoff handoff = mapper.readValue(normalized.toFile(), Handoff.class);
            if (handoff == null)
                throw new SkillParseException("Requirements input must be a JSON object");
            if (!"1.0".equals(handoff.schemaVersion()))
                throw new SkillParseException("Unsupported requirements schemaVersion: " + handoff.schemaVersion());
            if (handoff.requirements() == null)
                throw new SkillParseException("Requirements input must contain `requirements`");
            if (handoff.requirements().size() > MAX_REQUIREMENTS)
                throw new SkillParseException("Requirements input exceeds " + MAX_REQUIREMENTS + " entries");
            return handoff.requirements().stream().map(this::toRequirement).toList();
        } catch (SkillParseException e) {
            throw e;
        } catch (IOException | IllegalArgumentException e) {
            throw new SkillParseException("Cannot parse requirements input " + normalized + ": " + e.getMessage(), e);
        }
    }

    private Requirement toRequirement(Entry entry) {
        if (entry == null)
            throw new SkillParseException("Requirements entries must be JSON objects");
        if (entry.type() == null || entry.name() == null || entry.name().isBlank())
            throw new SkillParseException("Each semantic requirement needs a type and non-blank name");
        if (entry.name().length() > MAX_TEXT_LENGTH)
            throw new SkillParseException("Semantic requirement name exceeds " + MAX_TEXT_LENGTH + " characters");
        if (entry.source() != RequirementSource.INFERRED)
            throw new SkillParseException("Semantic requirements must use source INFERRED");
        if (entry.type() == RequirementType.PACKAGE && entry.ecosystem() == null)
            throw new SkillParseException("Package requirements need ecosystem python, npm, or maven");
        if (entry.type() != RequirementType.PACKAGE && entry.ecosystem() != null)
            throw new SkillParseException("ecosystem is only valid for package requirements");
        Evidence evidenceInput = evidence(entry);
        if (entry.necessity() == null || entry.confidence() == null || evidenceInput.file() == null || evidenceInput.file().isBlank())
            throw new SkillParseException("Each semantic requirement needs necessity, confidence, and evidence");
        if (entry.type() == RequirementType.PACKAGE && entry.version() != null && entry.required() != null
                && !entry.version().equals(entry.required()))
            throw new SkillParseException("Package version and required constraints must match when both are present");
        String required = entry.type() == RequirementType.PACKAGE && entry.version() != null ? entry.version() : entry.required();
        if (required == null || required.isBlank())
            required = entry.type() == RequirementType.RUNTIME || entry.type() == RequirementType.PACKAGE ? "*" : "present";
        if (required.length() > MAX_TEXT_LENGTH)
            throw new SkillParseException("Semantic requirement constraint exceeds " + MAX_TEXT_LENGTH + " characters");
        String evidence = bounded(evidenceInput.file().strip(), MAX_TEXT_LENGTH);
        String rawMatched = entry.matched() != null ? entry.matched() : evidenceInput.matched();
        String matched = rawMatched == null ? null : sanitizeMatched(rawMatched);
        String rawRule = entry.inferenceRule() != null ? entry.inferenceRule() : evidenceInput.inferenceRule();
        String rule = rawRule == null || rawRule.isBlank()
                ? "agent-semantic-extraction" : bounded(rawRule.strip(), MAX_TEXT_LENGTH);
        if (entry.type() == RequirementType.PACKAGE)
            return PackageRequirement.inferred(entry.ecosystem(), entry.name().strip(), required.strip(), entry.necessity(),
                    entry.confidence(), evidence, matched, rule);
        return SkillRequirement.inferred(entry.type(), entry.name().strip(), required.strip(), entry.necessity(),
                    entry.confidence(), evidence, matched, rule);
    }

    private Evidence evidence(Entry entry) {
        JsonNode value = entry.evidence();
        if (value == null || value.isNull()) return new Evidence(null, null, null);
        if (value.isTextual()) return new Evidence(value.asText(), null, null);
        if (!value.isObject()) throw new SkillParseException("evidence must be a string or object");
        ensureEvidenceFields(value);
        return new Evidence(text(value, "file"), text(value, "matched"), text(value, "inferenceRule"));
    }

    private void ensureEvidenceFields(JsonNode value) {
        value.fieldNames().forEachRemaining(name -> {
            if (!Set.of("file", "matched", "inferenceRule").contains(name))
                throw new SkillParseException("Unknown evidence field: " + name);
        });
    }

    private String text(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw new SkillParseException("evidence." + field + " must be a string");
        return value.asText();
    }

    private String sanitizeMatched(String raw) {
        String assignments = SENSITIVE_LITERAL.matcher(raw.strip()).replaceAll("$1<redacted>");
        return bounded(SENSITIVE_TOKEN.matcher(assignments).replaceAll("<redacted>"), MAX_MATCHED_LENGTH);
    }

    private String bounded(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit - 1) + "…";
    }

    private record Handoff(String schemaVersion, List<Entry> requirements) {}
    private record Entry(RequirementType type, PackageEcosystem ecosystem, String name, String required, String version,
                         RequirementNecessity necessity, RequirementSource source, Confidence confidence, JsonNode evidence,
                         String matched, String inferenceRule) {}
    private record Evidence(String file, String matched, String inferenceRule) {}
}
