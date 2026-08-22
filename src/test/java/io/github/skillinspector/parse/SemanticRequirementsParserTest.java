package io.github.skillinspector.parse;

import io.github.skillinspector.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticRequirementsParserTest {
    @TempDir Path temp;

    @Test void parsesNecessityAndRedactsMatchedSensitiveValues() throws Exception {
        Path input = temp.resolve("requirements.json");
        Files.writeString(input, """
                {"schemaVersion":"1.0","requirements":[{
                  "type":"command","name":"vercel","necessity":"REQUIRED","source":"INFERRED",
                  "confidence":"HIGH","evidence":"SKILL.md:12","matched":"API_TOKEN=example-sensitive-value vercel deploy"
                }]}
                """);

        Requirement requirement = new SemanticRequirementsParser().parse(input).getFirst();

        assertThat(requirement.type()).isEqualTo(RequirementType.COMMAND);
        assertThat(requirement.necessity()).isEqualTo(RequirementNecessity.REQUIRED);
        assertThat(requirement.matched()).contains("<redacted>").doesNotContain("example-sensitive-value");
        assertThat(requirement.inferenceRule()).isEqualTo("agent-semantic-extraction");
    }

    @Test void parsesPackageEcosystemAsPackageRequirement() throws Exception {
        Path input = temp.resolve("packages.json");
        Files.writeString(input, """
                {"schemaVersion":"1.0","requirements":[{
                  "type":"package","ecosystem":"python","name":"pypdf","version":">=4",
                  "necessity":"REQUIRED","source":"INFERRED","confidence":"HIGH",
                  "evidence":{"file":"SKILL.md:20","matched":"API_TOKEN=example-sensitive-value ghp_1234567890abcdef Use pypdf for extraction",
                              "inferenceRule":"SEMANTIC_PACKAGE_REFERENCE"}
                }]}
                """);

        Requirement requirement = new SemanticRequirementsParser().parse(input).getFirst();

        assertThat(requirement).isInstanceOf(PackageRequirement.class);
        assertThat(((PackageRequirement) requirement).ecosystem()).isEqualTo(PackageEcosystem.PYTHON);
        assertThat(requirement.required()).isEqualTo(">=4");
        assertThat(requirement.matched()).contains("<redacted>").doesNotContain("example-sensitive-value");
        assertThat(requirement.matched()).doesNotContain("ghp_1234567890abcdef");
        assertThat(requirement.inferenceRule()).isEqualTo("SEMANTIC_PACKAGE_REFERENCE");
    }

    @Test void rejectsPackageWithoutEcosystem() throws Exception {
        Path input = temp.resolve("invalid-package.json");
        Files.writeString(input, """
                {"schemaVersion":"1.0","requirements":[{
                  "type":"package","name":"pypdf","necessity":"REQUIRED","source":"INFERRED",
                  "confidence":"HIGH","evidence":"SKILL.md:20"
                }]}
                """);

        assertThatThrownBy(() -> new SemanticRequirementsParser().parse(input))
                .isInstanceOf(SkillParseException.class).hasMessageContaining("need ecosystem");
    }

    @Test void rejectsRequirementsPresentedAsDeclaredFacts() throws Exception {
        Path input = temp.resolve("requirements.json");
        Files.writeString(input, """
                {"schemaVersion":"1.0","requirements":[{
                  "type":"command","name":"vercel","necessity":"REQUIRED","source":"DECLARED",
                  "confidence":"HIGH","evidence":"SKILL.md:12"
                }]}
                """);

        assertThatThrownBy(() -> new SemanticRequirementsParser().parse(input))
                .isInstanceOf(SkillParseException.class).hasMessageContaining("source INFERRED");
    }

    @Test void rejectsNullDocumentInsteadOfLeakingAnInternalError() throws Exception {
        Path input = temp.resolve("requirements.json");
        Files.writeString(input, "null\n");

        assertThatThrownBy(() -> new SemanticRequirementsParser().parse(input))
                .isInstanceOf(SkillParseException.class).hasMessageContaining("JSON object");
    }
}
