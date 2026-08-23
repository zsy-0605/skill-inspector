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

    @Test void parsesCapabilityFromHandoff11WithExplainableEvidence() throws Exception {
        Path input = temp.resolve("capabilities.json");
        Files.writeString(input, """
                {"schemaVersion":"1.1","requirements":[{
                  "type":"capability","capabilityKind":"tool","name":"hf_jobs","required":"available",
                  "necessity":"REQUIRED","source":"INFERRED","confidence":"HIGH",
                  "evidence":{"file":"SKILL.md:38","matched":"ALWAYS use hf_jobs() MCP tool",
                              "inferenceRule":"SEMANTIC_TOOL_REFERENCE"}
                }]}
                """);

        CapabilityRequirement requirement = (CapabilityRequirement) new SemanticRequirementsParser().parse(input).getFirst();

        assertThat(requirement.capabilityKind()).isEqualTo(CapabilityKind.TOOL);
        assertThat(requirement.required()).isEqualTo("available");
        assertThat(requirement.inferenceRule()).isEqualTo("SEMANTIC_TOOL_REFERENCE");
    }

    @Test void rejectsCapabilitiesInHandoff10() throws Exception {
        Path input = temp.resolve("old-capabilities.json");
        Files.writeString(input, """
                {"schemaVersion":"1.0","requirements":[{
                  "type":"capability","capabilityKind":"tool","name":"hf_jobs",
                  "necessity":"REQUIRED","source":"INFERRED","confidence":"HIGH","evidence":"SKILL.md:38"
                }]}
                """);

        assertThatThrownBy(() -> new SemanticRequirementsParser().parse(input))
                .isInstanceOf(SkillParseException.class).hasMessageContaining("schemaVersion 1.1");
    }

    @Test void parsesSkillDependencyOnlyFromHandoff12() throws Exception {
        Path input = temp.resolve("skills.json");
        Files.writeString(input, """
                {"schemaVersion":"1.2","requirements":[{
                  "type":"skill","namespace":"acme","name":"pdf-analysis","version":">=1.2",
                  "necessity":"REQUIRED","source":"INFERRED","confidence":"HIGH",
                  "evidence":{"file":"SKILL.md:20","matched":"Use acme/pdf-analysis",
                    "inferenceRule":"SEMANTIC_SKILL_REFERENCE"}
                }]}
                """);
        SkillDependencyRequirement requirement = (SkillDependencyRequirement) new SemanticRequirementsParser().parse(input).getFirst();
        assertThat(requirement.identity().canonicalId()).isEqualTo("acme/pdf-analysis");
        assertThat(requirement.requiredVersion()).isEqualTo(">=1.2");

        Files.writeString(input, Files.readString(input).replace("\"1.2\"", "\"1.1\""));
        assertThatThrownBy(() -> new SemanticRequirementsParser().parse(input)).hasMessageContaining("schemaVersion 1.2");
    }
}
