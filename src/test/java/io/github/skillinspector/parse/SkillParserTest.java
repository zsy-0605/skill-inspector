package io.github.skillinspector.parse;

import io.github.skillinspector.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class SkillParserTest {
    @TempDir Path temp;

    @Test void parsesDeclaredAndInferredRequirementsWithoutExecutingScript() throws Exception {
        Files.writeString(temp.resolve("SKILL.md"), """
                ---
                name: sample
                compatibility:
                  runtimes:
                    java: ">=21"
                  env: [API_TOKEN]
                  files: [config.json]
                  directories: [data]
                  os: [linux, macos]
                ---
                # Sample
                """);
        Path scripts = Files.createDirectory(temp.resolve("scripts"));
        Files.writeString(scripts.resolve("convert.sh"), "#!/bin/sh\ntouch SHOULD_NOT_EXIST\npython analyze.py\npdftotext input.pdf output.txt\ncurl https://example.test?API_KEY=example-sensitive-value\n");

        SkillDefinition parsed = new SkillParser().parse(temp);

        assertThat(parsed.requirements()).anySatisfy(r -> {
            assertThat(r.name()).isEqualTo("java"); assertThat(r.source()).isEqualTo(RequirementSource.DECLARED);
        }).anySatisfy(r -> {
            assertThat(r.name()).isEqualTo("pdftotext");
            assertThat(r.source()).isEqualTo(RequirementSource.INFERRED);
            assertThat(r.evidence()).isEqualTo("scripts/convert.sh:4");
            assertThat(r.matched()).isEqualTo("pdftotext input.pdf output.txt");
            assertThat(r.inferenceRule()).isEqualTo("shell-command-position");
        }).anySatisfy(r -> {
            assertThat(r.name()).isEqualTo("python"); assertThat(r.type()).isEqualTo(RequirementType.RUNTIME);
        }).anySatisfy(r -> {
            assertThat(r.name()).isEqualTo("curl");
            assertThat(r.matched()).contains("<redacted>").doesNotContain("example-sensitive-value");
        });
        assertThat(temp.resolve("SHOULD_NOT_EXIST")).doesNotExist();
    }

    @Test void skipsSymbolicLinkScripts() throws Exception {
        Files.writeString(temp.resolve("SKILL.md"), "---\nname: symlink-skill\n---\n");
        Path scripts = Files.createDirectory(temp.resolve("scripts"));
        Path outside = temp.resolve("outside.sh");
        Files.writeString(outside, "pdftotext input.pdf output.txt\n");
        try { Files.createSymbolicLink(scripts.resolve("linked.sh"), outside); }
        catch (UnsupportedOperationException | IOException | SecurityException e) {
            Assumptions.assumeTrue(false, "Symbolic links unavailable");
            return;
        }

        SkillDefinition parsed = new SkillParser().parse(temp);

        assertThat(parsed.requirements()).isEmpty();
    }

    @Test void parsesDeclaredCapabilitiesFromFrontmatter() throws Exception {
        Files.writeString(temp.resolve("SKILL.md"), """
                ---
                name: capability-skill
                compatibility:
                  capabilities:
                    - capabilityKind: mcpServer
                      name: openaiDeveloperDocs
                      necessity: required
                    - capabilityKind: tool
                      name: hf_jobs
                      necessity: conditional
                ---
                # Capability skill
                """);

        var capabilities = new SkillParser().parse(temp).requirements().stream()
                .filter(CapabilityRequirement.class::isInstance)
                .map(CapabilityRequirement.class::cast).toList();

        assertThat(capabilities).extracting(CapabilityRequirement::name)
                .containsExactly("openaiDeveloperDocs", "hf_jobs");
        assertThat(capabilities.getFirst().necessity()).isEqualTo(RequirementNecessity.REQUIRED);
        assertThat(capabilities.get(1).necessity()).isEqualTo(RequirementNecessity.CONDITIONAL);
    }
}
