package io.github.skillinspector.parse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.skillinspector.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class SkillInventoryParser {
    public static final int MAX_SKILLS = 1_000;
    public static final int MAX_EDGES = 5_000;
    private static final long MAX_FILE_BYTES = 1024 * 1024;
    private static final int MAX_TEXT = 500;
    private static final int MAX_MATCHED = 240;
    private static final Pattern SENSITIVE_LITERAL = Pattern.compile(
            "(?i)([A-Z0-9_]*(?:TOKEN|SECRET|PASSWORD|PASSWD|API_KEY)[A-Z0-9_]*\\s*=\\s*)(\\\"[^\\\"]*\\\"|'[^']*'|[^\\s]+)");
    private static final Pattern SENSITIVE_TOKEN = Pattern.compile(
            "(?i)\\b(?:bearer\\s+)?(?:sk-|gh[opusr]_|xox[baprs]-)[A-Za-z0-9._-]{8,}");
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public SkillInventory parse(Path input) {
        Path normalized = input.toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized))
                throw new SkillParseException("Skill Inventory must be a regular non-symbolic-link file: " + normalized);
            if (Files.size(normalized) > MAX_FILE_BYTES)
                throw new SkillParseException("Skill Inventory exceeds 1 MiB: " + normalized);
            RawInventory raw = mapper.readValue(normalized.toFile(), RawInventory.class);
            return validate(raw);
        } catch (SkillParseException error) {
            throw error;
        } catch (IOException | IllegalArgumentException error) {
            throw new SkillParseException("Cannot parse Skill Inventory " + normalized + ": " + error.getMessage(), error);
        }
    }

    private SkillInventory validate(RawInventory raw) {
        if (raw == null || !"1.0".equals(raw.schemaVersion()))
            throw new SkillParseException("Skill Inventory schemaVersion must be 1.0");
        if (raw.coverage() == null || raw.skills() == null)
            throw new SkillParseException("Skill Inventory requires coverage and skills");
        if (raw.skills().size() > MAX_SKILLS)
            throw new SkillParseException("Skill Inventory exceeds " + MAX_SKILLS + " skills");
        Set<String> identities = new HashSet<>();
        int edges = 0;
        List<SkillInventoryEntry> entries = raw.skills().stream().map(item -> {
            require(item != null && item.identity() != null, "Each inventory entry requires identity");
            SkillIdentity identity = identity(item.identity());
            require(identities.add(identity.canonicalId()), "Duplicate Skill identity: " + identity.canonicalId());
            require(item.availability() != null && item.source() != null && item.dependencyCoverage() != null,
                    "Skill " + identity.canonicalId() + " requires availability, source, and dependencyCoverage");
            if (item.source() == SkillInventorySource.LOCAL_SKILL_DIRECTORY
                    && item.availability() == SkillAvailability.AVAILABLE)
                throw new SkillParseException("LOCAL_SKILL_DIRECTORY cannot prove AVAILABLE: " + identity.canonicalId());
            bounded(item.version(), "Skill version");
            List<RawDependency> dependencies = item.dependencies() == null ? List.of() : item.dependencies();
            return new SkillInventoryEntry(identity, item.version(), item.availability(), item.source(),
                    item.dependencyCoverage(), dependencies.stream().map(this::dependency).toList());
        }).toList();
        for (SkillInventoryEntry entry : entries) edges += entry.dependencies().size();
        if (edges > MAX_EDGES) throw new SkillParseException("Skill Inventory exceeds " + MAX_EDGES + " dependency edges");
        return new SkillInventory(raw.schemaVersion(), raw.coverage(), entries);
    }

    private SkillInventoryDependency dependency(RawDependency item) {
        require(item != null && item.identity() != null, "Each inventory dependency requires identity");
        require(item.necessity() != null && item.source() != null,
                "Each inventory dependency requires necessity and source");
        require(item.evidence() != null && !item.evidence().isBlank(), "Each inventory dependency requires evidence");
        if (item.source() == RequirementSource.INFERRED && item.confidence() == null)
            throw new SkillParseException("Inferred inventory dependencies require confidence");
        if (item.source() == RequirementSource.DECLARED && item.confidence() != null)
            throw new SkillParseException("Declared inventory dependencies must not set confidence");
        bounded(item.version(), "Dependency version");
        bounded(item.evidence(), "Dependency evidence");
        if (item.matched() != null && item.matched().length() > MAX_MATCHED)
            throw new SkillParseException("Dependency matched exceeds " + MAX_MATCHED + " characters");
        bounded(item.inferenceRule(), "Dependency inferenceRule");
        return new SkillInventoryDependency(identity(item.identity()), item.version(), item.necessity(), item.source(),
                item.confidence(), item.evidence(), sanitizeMatched(item.matched()), item.inferenceRule());
    }

    private SkillIdentity identity(RawIdentity value) { return new SkillIdentity(value.namespace(), value.name()); }
    private void bounded(String value, String label) {
        if (value != null && value.length() > MAX_TEXT) throw new SkillParseException(label + " exceeds " + MAX_TEXT + " characters");
    }
    private void require(boolean condition, String message) { if (!condition) throw new SkillParseException(message); }
    private String sanitizeMatched(String value) {
        if (value == null) return null;
        String assignments = SENSITIVE_LITERAL.matcher(value.strip()).replaceAll("$1<redacted>");
        return SENSITIVE_TOKEN.matcher(assignments).replaceAll("<redacted>");
    }

    private record RawInventory(String schemaVersion, SkillInventoryCoverage coverage, List<RawEntry> skills) {}
    private record RawEntry(RawIdentity identity, String version, SkillAvailability availability,
                            SkillInventorySource source, SkillInventoryCoverage dependencyCoverage,
                            List<RawDependency> dependencies) {}
    private record RawIdentity(String namespace, String name) {}
    private record RawDependency(RawIdentity identity, String version, RequirementNecessity necessity,
                                 RequirementSource source, Confidence confidence, String evidence,
                                 String matched, String inferenceRule) {}
}
