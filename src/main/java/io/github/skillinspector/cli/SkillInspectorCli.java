package io.github.skillinspector.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.skillinspector.core.InspectionService;
import io.github.skillinspector.model.*;
import io.github.skillinspector.parse.SkillParseException;
import io.github.skillinspector.report.*;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "skill-inspector", mixinStandardHelpOptions = true, version = "skill-inspector 0.1.1-SNAPSHOT",
        description = "Preflight compatibility inspection for Agent Skills.",
        subcommands = SkillInspectorCli.InspectCommand.class)
public final class SkillInspectorCli implements Runnable {
    @Override public void run() { CommandLine.usage(this, System.out); }
    public static void main(String[] args) { System.exit(new CommandLine(new SkillInspectorCli()).execute(args)); }

    @Command(name = "inspect", description = "Inspect a local Skill directory without executing its code.")
    static final class InspectCommand implements Callable<Integer> {
        @Parameters(index = "0", paramLabel = "SKILL_DIR", description = "Directory containing SKILL.md") Path target;
        @Option(names = "--json", description = "Emit stable machine-readable JSON") boolean json;

        @Override public Integer call() {
            try {
                InspectionReport report = new InspectionService().inspect(target);
                System.out.println(json ? new JsonReportRenderer().render(report) : new HumanReportRenderer().render(report));
                return report.status() == OverallStatus.FAIL ? 2 : 0;
            } catch (SkillParseException | IllegalArgumentException e) {
                if (json) {
                    try { System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("schemaVersion", "1.0", "status", "ERROR", "message", e.getMessage()))); }
                    catch (Exception ignored) { System.err.println(e.getMessage()); }
                } else System.err.println("Inspection error: " + e.getMessage());
                return 1;
            }
        }
    }
}
