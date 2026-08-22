package io.github.skillinspector.report;

import io.github.skillinspector.model.*;
import java.util.*;

public final class HumanReportRenderer {
    public String render(InspectionReport report) {
        StringBuilder out = new StringBuilder();
        out.append("Skill Inspector\n\nTarget: ").append(report.skill()).append("\nPath: ").append(report.target())
                .append("\n\nCompatibility: ").append(report.status()).append("\nReadiness: ").append(report.readiness())
                .append("\nCompatibility Score: ").append(report.score()).append(" / 100\n");
        Map<RequirementType, List<CheckResult>> grouped = new LinkedHashMap<>();
        for (CheckResult check : report.checks()) grouped.computeIfAbsent(check.type(), ignored -> new ArrayList<>()).add(check);
        grouped.forEach((type, checks) -> {
            out.append("\n").append(title(type)).append("\n--------------------------------\n");
            checks.forEach(check -> {
                out.append(symbol(check.status())).append(" ").append(check.name());
                if (check.ecosystem() != null) out.append(" [").append(check.ecosystem().jsonValue()).append("]");
                if (!check.required().equals("present")) out.append(" ").append(check.required());
                out.append("\n  Actual: ").append(check.actual()).append("\n  Status: ").append(check.status())
                        .append(" | Source: ").append(check.source()).append(" | Necessity: ").append(check.necessity());
                if (check.confidence() != null) out.append(" | Confidence: ").append(check.confidence());
                if (check.evidence() != null) out.append("\n  Evidence: ").append(check.evidence());
                if (check.matched() != null) out.append("\n  Matched: ").append(check.matched());
                if (check.inferenceRule() != null) out.append("\n  Rule: ").append(check.inferenceRule());
                out.append("\n");
            });
        });
        out.append("\nSummary\n--------------------------------\n");
        if (report.issues().isEmpty()) out.append("No compatibility issues detected.\n");
        else for (int i = 0; i < report.issues().size(); i++) out.append(i + 1).append(". ").append(report.issues().get(i)).append("\n");
        out.append("\nRecommendation: ").append(switch (report.status()) {
            case READY -> "The declared and inferred checks passed; this Skill is ready for activation.";
            case WARNING -> "Confirm unresolved or inferred requirements before activating this Skill.";
            case FAIL -> "Resolve blocking dependencies before activating this Skill.";
        }).append("\n");
        return out.toString();
    }

    private String title(RequirementType type) { return switch (type) {
        case RUNTIME -> "Runtimes"; case COMMAND -> "Commands"; case ENVIRONMENT_VARIABLE -> "Environment";
        case FILE -> "Files"; case DIRECTORY -> "Directories"; case OPERATING_SYSTEM -> "Operating System";
        case PACKAGE -> "Packages";
    }; }
    private String symbol(CheckStatus status) { return switch (status) { case PASS -> "✓"; case FAIL -> "✗"; case WARNING -> "!"; case UNKNOWN -> "?"; }; }
}
