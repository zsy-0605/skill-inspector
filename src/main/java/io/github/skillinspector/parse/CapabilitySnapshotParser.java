package io.github.skillinspector.parse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.skillinspector.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;

public final class CapabilitySnapshotParser {
    private static final long MAX_FILE_BYTES = 1024 * 1024;
    private static final int MAX_CAPABILITIES = 1000;
    private static final int MAX_ALIASES = 100;
    private static final int MAX_TEXT_LENGTH = 500;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public CapabilitySnapshot parse(Path input) {
        Path normalized = input.toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized))
                throw new SkillParseException("Capability snapshot must be a regular non-symbolic-link file: " + normalized);
            if (Files.size(normalized) > MAX_FILE_BYTES)
                throw new SkillParseException("Capability snapshot exceeds 1 MiB: " + normalized);
            CapabilitySnapshot snapshot = mapper.readValue(normalized.toFile(), CapabilitySnapshot.class);
            validate(snapshot);
            return snapshot;
        } catch (SkillParseException error) {
            throw error;
        } catch (IOException | IllegalArgumentException error) {
            throw new SkillParseException("Cannot parse capability snapshot " + normalized + ": " + error.getMessage(), error);
        }
    }

    private void validate(CapabilitySnapshot snapshot) {
        if (snapshot == null) throw new SkillParseException("Capability snapshot must be a JSON object");
        if (!"1.0".equals(snapshot.schemaVersion()))
            throw new SkillParseException("Unsupported capability snapshot schemaVersion: " + snapshot.schemaVersion());
        if (snapshot.runtime() == null || blank(snapshot.runtime().name()))
            throw new SkillParseException("Capability snapshot runtime needs a non-blank name");
        bounded(snapshot.runtime().name(), "runtime name");
        if (snapshot.runtime().version() != null) bounded(snapshot.runtime().version(), "runtime version");
        if (snapshot.coverage() == null || snapshot.coverage().mcpServer() == null
                || snapshot.coverage().tool() == null || snapshot.coverage().capability() == null)
            throw new SkillParseException("Capability snapshot coverage must define mcpServer, tool, and capability");
        if (snapshot.capabilities() == null)
            throw new SkillParseException("Capability snapshot must contain capabilities");
        if (snapshot.capabilities().size() > MAX_CAPABILITIES)
            throw new SkillParseException("Capability snapshot exceeds " + MAX_CAPABILITIES + " entries");
        Map<CapabilityKind, Map<String, String>> identities = new EnumMap<>(CapabilityKind.class);
        for (CapabilityEntry entry : snapshot.capabilities()) validateEntry(entry, identities);
    }

    private void validateEntry(CapabilityEntry entry, Map<CapabilityKind, Map<String, String>> identities) {
        if (entry == null || entry.capabilityKind() == null || blank(entry.name())
                || entry.availability() == null || entry.source() == null)
            throw new SkillParseException("Each capability entry needs capabilityKind, name, availability, and source");
        bounded(entry.name(), "capability name");
        List<String> aliases = entry.aliases() == null ? List.of() : entry.aliases();
        if (aliases.size() > MAX_ALIASES)
            throw new SkillParseException("Capability entry exceeds " + MAX_ALIASES + " aliases: " + entry.name());
        Map<String, String> names = identities.computeIfAbsent(entry.capabilityKind(), ignored -> new HashMap<>());
        register(names, entry.name(), entry.name());
        for (String alias : aliases) {
            if (blank(alias)) throw new SkillParseException("Capability aliases must not be blank: " + entry.name());
            bounded(alias, "capability alias");
            register(names, alias, entry.name());
        }
    }

    private void register(Map<String, String> names, String identity, String owner) {
        String previous = names.putIfAbsent(identity, owner);
        if (previous != null)
            throw new SkillParseException("Ambiguous capability name or alias `" + identity + "` for `"
                    + previous + "` and `" + owner + "`");
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private void bounded(String value, String field) {
        if (value.length() > MAX_TEXT_LENGTH)
            throw new SkillParseException("Capability snapshot " + field + " exceeds " + MAX_TEXT_LENGTH + " characters");
    }
}
