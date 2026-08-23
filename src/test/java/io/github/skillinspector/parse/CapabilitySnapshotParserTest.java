package io.github.skillinspector.parse;

import io.github.skillinspector.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapabilitySnapshotParserTest {
    @TempDir Path temp;

    @Test void parsesAPlatformNeutralSnapshot() throws Exception {
        Path input = write("""
                {"schemaVersion":"1.0","runtime":{"name":"generic-agent-runtime","version":"1"},
                 "coverage":{"mcpServer":"COMPLETE","tool":"COMPLETE","capability":"PARTIAL"},
                 "capabilities":[{"capabilityKind":"tool","name":"mcp__docs__search",
                   "aliases":["search_docs"],"availability":"AVAILABLE","source":"RUNTIME_INVENTORY"}]}
                """);

        CapabilitySnapshot snapshot = new CapabilitySnapshotParser().parse(input);

        assertThat(snapshot.runtime().name()).isEqualTo("generic-agent-runtime");
        assertThat(snapshot.coverage().tool()).isEqualTo(CapabilityCoverage.COMPLETE);
        assertThat(snapshot.capabilities().getFirst().aliases()).containsExactly("search_docs");
    }

    @Test void rejectsConflictingNamesAndAliasesWithinOneKind() throws Exception {
        Path input = write("""
                {"schemaVersion":"1.0","runtime":{"name":"generic"},
                 "coverage":{"mcpServer":"COMPLETE","tool":"COMPLETE","capability":"PARTIAL"},
                 "capabilities":[
                   {"capabilityKind":"tool","name":"first","aliases":["shared"],"availability":"AVAILABLE","source":"RUNTIME_INVENTORY"},
                   {"capabilityKind":"tool","name":"shared","aliases":[],"availability":"AVAILABLE","source":"RUNTIME_INVENTORY"}]}
                """);

        assertThatThrownBy(() -> new CapabilitySnapshotParser().parse(input))
                .isInstanceOf(SkillParseException.class).hasMessageContaining("Ambiguous capability");
    }

    @Test void rejectsExecutionAndConnectionConfigurationFields() throws Exception {
        Path input = write("""
                {"schemaVersion":"1.0","runtime":{"name":"generic"},
                 "coverage":{"mcpServer":"COMPLETE","tool":"COMPLETE","capability":"PARTIAL"},
                 "capabilities":[],"command":"start-server"}
                """);

        assertThatThrownBy(() -> new CapabilitySnapshotParser().parse(input))
                .isInstanceOf(SkillParseException.class).hasMessageContaining("command");
    }

    @Test void rejectsMoreThanOneThousandEntries() throws Exception {
        StringBuilder entries = new StringBuilder();
        for (int index = 0; index < 1001; index++) {
            if (index > 0) entries.append(',');
            entries.append("{\"capabilityKind\":\"tool\",\"name\":\"tool")
                    .append(index).append("\",\"availability\":\"AVAILABLE\",\"source\":\"RUNTIME_INVENTORY\"}");
        }
        Path input = write("{\"schemaVersion\":\"1.0\",\"runtime\":{\"name\":\"generic\"},"
                + "\"coverage\":{\"mcpServer\":\"COMPLETE\",\"tool\":\"COMPLETE\",\"capability\":\"PARTIAL\"},"
                + "\"capabilities\":[" + entries + "]}");

        assertThatThrownBy(() -> new CapabilitySnapshotParser().parse(input))
                .isInstanceOf(SkillParseException.class).hasMessageContaining("exceeds 1000 entries");
    }

    private Path write(String value) throws Exception {
        Path input = temp.resolve("runtime-capabilities.json");
        Files.writeString(input, value);
        return input;
    }
}
