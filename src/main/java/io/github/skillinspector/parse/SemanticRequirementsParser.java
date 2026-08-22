package io.github.skillinspector.parse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.skillinspector.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public final class SemanticRequirementsParser {
    private static final long MAX_FILE_BYTES = 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 500;
    private static final int MAX_MATCHED_LENGTH = 240;
    private static final int MAX_REQUIREMENTS = 1000;
    private static final Pattern SENSITIVE_LITERAL = Pattern.compile(
            "(?i)([A-Z0-9_]*(?:TOKEN|SECRET|PASSWORD|PASSWD|API_KEY)[A-Z0-9_]*\\s*=\\s*)(\\\"[^\\\"]*\\\"|'[^']*'|[^\\s]+)");
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public List<SkillRequirement> parse(Path input) {
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

    private SkillRequirement toRequirement(Entry entry) {
        if (entry == null)
            throw new SkillParseException("Requirements entries must be JSON objects");
        if (entry.type() == null || entry.name() == null || entry.name().isBlank())
            throw new SkillParseException("Each semantic requirement needs a type and non-blank name");
        if (entry.name().length() > MAX_TEXT_LENGTH)
            throw new SkillParseException("Semantic requirement name exceeds " + MAX_TEXT_LENGTH + " characters");
        if (entry.source() != RequirementSource.INFERRED)
            throw new SkillParseException("Semantic requirements must use source INFERRED");
        if (entry.necessity() == null || entry.confidence() == null || entry.evidence() == null || entry.evidence().isBlank())
            throw new SkillParseException("Each semantic requirement needs necessity, confidence, and evidence");
        String required = entry.required();
        if (required == null || required.isBlank())
            required = entry.type() == RequirementType.RUNTIME ? "*" : "present";
        if (required.length() > MAX_TEXT_LENGTH)
            throw new SkillParseException("Semantic requirement constraint exceeds " + MAX_TEXT_LENGTH + " characters");
        String evidence = bounded(entry.evidence().strip(), MAX_TEXT_LENGTH);
        String matched = entry.matched() == null ? null : sanitizeMatched(entry.matched());
        String rule = entry.inferenceRule() == null || entry.inferenceRule().isBlank()
                ? "agent-semantic-extraction" : bounded(entry.inferenceRule().strip(), MAX_TEXT_LENGTH);
        return SkillRequirement.inferred(entry.type(), entry.name().strip(), required.strip(), entry.necessity(),
                entry.confidence(), evidence, matched, rule);
    }

    private String sanitizeMatched(String raw) {
        return bounded(SENSITIVE_LITERAL.matcher(raw.strip()).replaceAll("$1<redacted>"), MAX_MATCHED_LENGTH);
    }

    private String bounded(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit - 1) + "…";
    }

    private record Handoff(String schemaVersion, List<Entry> requirements) {}
    private record Entry(RequirementType type, String name, String required, RequirementNecessity necessity,
                         RequirementSource source, Confidence confidence, String evidence,
                         String matched, String inferenceRule) {}
}
