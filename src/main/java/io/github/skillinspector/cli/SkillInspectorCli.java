package io.github.skillinspector.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.skillinspector.core.InspectionService;
import io.github.skillinspector.model.*;
import io.github.skillinspector.parse.SkillParseException;
import io.github.skillinspector.parse.SemanticRequirementsParser;
import io.github.skillinspector.parse.CapabilitySnapshotParser;
import io.github.skillinspector.check.*;
import io.github.skillinspector.report.*;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "skill-inspector", mixinStandardHelpOptions = true, version = "skill-inspector 0.3.0",
        description = "Preflight compatibility inspection for Agent Skills.",
        subcommands = {SkillInspectorCli.InspectCommand.class, SkillInspectorCli.VerifyCommand.class})
public final class SkillInspectorCli implements Runnable {
    @Override public void run() { CommandLine.usage(this, System.out); }
    public static void main(String[] args) { System.exit(new CommandLine(new SkillInspectorCli()).execute(args)); }

    @Command(name = "inspect", description = "Inspect a local Skill directory without executing its code.")
    static final class InspectCommand implements Callable<Integer> {
        @Parameters(index = "0", paramLabel = "SKILL_DIR", description = "Directory containing SKILL.md") Path target;
        @Option(names = "--capabilities", paramLabel = "FILE", description = "Runtime capability snapshot JSON") Path capabilities;
        @Option(names = "--json", description = "Emit stable machine-readable JSON") boolean json;

        @Override public Integer call() {
            try {
                InspectionReport report = service(capabilities).inspect(target);
                System.out.println(json ? new JsonReportRenderer().render(report) : new HumanReportRenderer().render(report));
                return report.status() == OverallStatus.FAIL ? 2 : 0;
            } catch (SkillParseException | IllegalArgumentException e) {
                if (json) {
                    try { System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("schemaVersion", "1.1", "status", "ERROR", "message", e.getMessage()))); }
                    catch (Exception ignored) { System.err.println(e.getMessage()); }
                } else System.err.println("Inspection error: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "verify", description = "Verify Agent-inferred requirements against the local environment.")
    static final class VerifyCommand implements Callable<Integer> {
        @Parameters(index = "0", paramLabel = "SKILL_DIR", description = "Directory containing SKILL.md") Path target;
        @Option(names = "--requirements", required = true, paramLabel = "FILE", description = "Semantic requirements JSON") Path requirements;
        @Option(names = "--capabilities", paramLabel = "FILE", description = "Runtime capability snapshot JSON") Path capabilities;
        @Option(names = "--json", description = "Emit stable machine-readable JSON") boolean json;

        @Override public Integer call() {
            try {
                InspectionReport report = service(capabilities).verify(target, new SemanticRequirementsParser().parse(requirements));
                System.out.println(json ? new JsonReportRenderer().render(report) : new HumanReportRenderer().render(report));
                return report.status() == OverallStatus.FAIL ? 2 : 0;
            } catch (SkillParseException | IllegalArgumentException e) {
                if (json) {
                    try { System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("schemaVersion", "1.1", "status", "ERROR", "message", e.getMessage()))); }
                    catch (Exception ignored) { System.err.println(e.getMessage()); }
                } else System.err.println("Verification error: " + e.getMessage());
                return 1;
            }
        }
    }

    private static InspectionService service(Path capabilities) {
        EnvironmentProbe environment = new SystemEnvironmentProbe();
        if (capabilities != null)
            environment = new SnapshotEnvironmentProbe(environment, new CapabilitySnapshotParser().parse(capabilities));
        return new InspectionService(new io.github.skillinspector.parse.SkillParser(), environment);
    }
}
