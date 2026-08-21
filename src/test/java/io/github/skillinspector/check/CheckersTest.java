package io.github.skillinspector.check;

import io.github.skillinspector.model.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class CheckersTest {
    private final Path root = Path.of("/tmp/example");

    @Test void commandCheckerFindsAndMissesCommands() {
        FakeProbe probe = new FakeProbe(); probe.command = true;
        SkillRequirement requirement = SkillRequirement.declared(RequirementType.COMMAND, "git", "present", false);
        assertThat(new CommandChecker().check(requirement, root, probe).status()).isEqualTo(CheckStatus.PASS);
        probe.command = false;
        assertThat(new CommandChecker().check(requirement, root, probe).status()).isEqualTo(CheckStatus.FAIL);
    }

    @Test void inferredMissingCommandIsWarning() {
        SkillRequirement requirement = SkillRequirement.inferred(RequirementType.COMMAND, "pdftotext", "present", Confidence.HIGH,
                "scripts/a.sh:1", "pdftotext in.pdf out.txt", "shell-command-position");
        assertThat(new CommandChecker().check(requirement, root, new FakeProbe()).status()).isEqualTo(CheckStatus.WARNING);
    }

    @Test void environmentVariableNeverReturnsItsValue() {
        FakeProbe probe = new FakeProbe(); probe.env = true;
        CheckResult result = new EnvironmentVariableChecker().check(SkillRequirement.declared(RequirementType.ENVIRONMENT_VARIABLE, "SECRET", "present", false), root, probe);
        assertThat(result.actual()).isEqualTo("PRESENT");
        assertThat(result.message()).doesNotContain("secret-value");
    }

    @Test void fileDirectoryAndOperatingSystemAreDeterministic() {
        FakeProbe probe = new FakeProbe(); probe.file = true; probe.directory = false; probe.os = "linux";
        assertThat(new FileChecker().check(SkillRequirement.declared(RequirementType.FILE, "config.json", "present", false), root, probe).status()).isEqualTo(CheckStatus.PASS);
        assertThat(new FileChecker().check(SkillRequirement.declared(RequirementType.DIRECTORY, "data", "present", false), root, probe).status()).isEqualTo(CheckStatus.FAIL);
        assertThat(new OperatingSystemChecker().check(SkillRequirement.declared(RequirementType.OPERATING_SYSTEM, "operating-system", "linux,macos", false), root, probe).status()).isEqualTo(CheckStatus.PASS);
    }

    private static final class FakeProbe implements EnvironmentProbe {
        boolean command, env, file, directory; String os = "linux"; Optional<String> runtime = Optional.of("21.0.4");
        public String operatingSystem() { return os; }
        public boolean commandExists(String name) { return command; }
        public boolean environmentVariablePresent(String name) { return env; }
        public boolean fileExists(Path path) { return file; }
        public boolean directoryExists(Path path) { return directory; }
        public Optional<String> runtimeVersion(String name) { return runtime; }
    }
}
