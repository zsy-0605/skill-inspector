package io.github.skillinspector.core;

import io.github.skillinspector.check.EnvironmentProbe;
import io.github.skillinspector.model.*;
import io.github.skillinspector.parse.SkillParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Optional;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class InspectionServiceTest {
    @TempDir Path temp;

    @Test void declaredFailureForcesNotReadyRegardlessOfScore() throws Exception {
        Files.writeString(temp.resolve("SKILL.md"), "---\nname: broken\ncompatibility:\n  commands: [git, missing]\n---\n");
        EnvironmentProbe probe = new EnvironmentProbe() {
            public String operatingSystem() { return "linux"; }
            public boolean commandExists(String value) { return value.equals("git"); }
            public boolean environmentVariablePresent(String value) { return false; }
            public boolean fileExists(Path value) { return false; }
            public boolean directoryExists(Path value) { return false; }
            public Optional<String> runtimeVersion(String value) { return Optional.empty(); }
        };
        InspectionReport report = new InspectionService(new SkillParser(), probe).inspect(temp);
        assertThat(report.status()).isEqualTo(OverallStatus.FAIL);
        assertThat(report.readiness()).isEqualTo("NOT READY");
        assertThat(report.score()).isEqualTo(50);
    }

    @Test void requiredSemanticInferenceCanBlockWhileConditionalInferenceOnlyWarns() throws Exception {
        Files.writeString(temp.resolve("SKILL.md"), "---\nname: semantic\n---\n");
        EnvironmentProbe probe = new EnvironmentProbe() {
            public String operatingSystem() { return "linux"; }
            public boolean commandExists(String value) { return false; }
            public boolean environmentVariablePresent(String value) { return false; }
            public boolean fileExists(Path value) { return false; }
            public boolean directoryExists(Path value) { return false; }
            public Optional<String> runtimeVersion(String value) { return Optional.empty(); }
        };
        SkillRequirement required = SkillRequirement.inferred(RequirementType.COMMAND, "vercel", "present",
                RequirementNecessity.REQUIRED, Confidence.HIGH, "SKILL.md:12", "Vercel CLI is required", "agent-semantic-extraction");
        SkillRequirement conditional = SkillRequirement.inferred(RequirementType.COMMAND, "docker", "present",
                Confidence.HIGH, "scripts/build.sh:2", "docker build .", "shell-command-position");

        InspectionReport report = new InspectionService(new SkillParser(), probe).verify(temp, java.util.List.of(required, conditional));

        assertThat(report.readiness()).isEqualTo("NOT READY");
        assertThat(report.checks()).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("vercel");
            assertThat(check.status()).isEqualTo(CheckStatus.FAIL);
            assertThat(check.necessity()).isEqualTo(RequirementNecessity.REQUIRED);
        }).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("docker");
            assertThat(check.status()).isEqualTo(CheckStatus.WARNING);
        });
    }

    @Test void incompleteSkillGraphCannotProduceReady() throws Exception {
        Files.writeString(temp.resolve("SKILL.md"), """
                ---
                name: root
                compatibility:
                  skills:
                    - name: child
                ---
                """);
        SkillInventory inventory = new SkillInventory("1.0", SkillInventoryCoverage.COMPLETE, List.of(
                new SkillInventoryEntry(new SkillIdentity(null, "child"), "1.0", SkillAvailability.AVAILABLE,
                        SkillInventorySource.RUNTIME_INVENTORY, SkillInventoryCoverage.PARTIAL, List.of())));
        InspectionReport report = new InspectionService(new SkillParser(), new io.github.skillinspector.check.SystemEnvironmentProbe(),
                new DependencyGraphResolver(inventory)).inspect(temp);
        assertThat(report.schemaVersion()).isEqualTo("1.2");
        assertThat(report.status()).isEqualTo(OverallStatus.WARNING);
        assertThat(report.checks()).anyMatch(item -> item.resolutionKind() == SkillResolutionKind.GRAPH_COVERAGE);
    }

    @Test void declaredProvenanceAndStrongestNecessityRemainIndependentForSkillMerge() throws Exception {
        Files.writeString(temp.resolve("SKILL.md"), """
                ---
                name: root
                compatibility:
                  skills:
                    - name: child
                      necessity: optional
                ---
                """);
        SkillDependencyRequirement inferred = SkillDependencyRequirement.inferred(new SkillIdentity(null, "child"), "*",
                RequirementNecessity.REQUIRED, Confidence.HIGH, "SKILL.md:8", "always use child", "SEMANTIC_SKILL_REFERENCE");
        SkillInventory inventory = new SkillInventory("1.0", SkillInventoryCoverage.COMPLETE, List.of());
        InspectionReport report = new InspectionService(new SkillParser(), new io.github.skillinspector.check.SystemEnvironmentProbe(),
                new DependencyGraphResolver(inventory)).verify(temp, List.of(inferred));
        assertThat(report.checks().getFirst().source()).isEqualTo(RequirementSource.DECLARED);
        assertThat(report.checks().getFirst().necessity()).isEqualTo(RequirementNecessity.REQUIRED);
        assertThat(report.checks().getFirst().status()).isEqualTo(CheckStatus.FAIL);
    }
}
