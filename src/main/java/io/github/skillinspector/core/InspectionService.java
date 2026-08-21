package io.github.skillinspector.core;

import io.github.skillinspector.check.*;
import io.github.skillinspector.model.*;
import io.github.skillinspector.parse.SkillParser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class InspectionService {
    private final SkillParser parser;
    private final EnvironmentProbe environment;
    private final List<RequirementChecker> checkers;

    public InspectionService() { this(new SkillParser(), new SystemEnvironmentProbe()); }
    public InspectionService(SkillParser parser, EnvironmentProbe environment) {
        this.parser = parser;
        this.environment = environment;
        this.checkers = List.of(new RuntimeChecker(), new CommandChecker(), new EnvironmentVariableChecker(), new FileChecker(), new OperatingSystemChecker());
    }

    public InspectionReport inspect(Path target) {
        SkillDefinition skill = parser.parse(target);
        List<CheckResult> results = new ArrayList<>();
        for (SkillRequirement requirement : skill.requirements()) {
            RequirementChecker checker = checkers.stream().filter(item -> item.supports(requirement.type())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No checker for " + requirement.type()));
            results.add(checker.check(requirement, skill.root(), environment));
        }
        OverallStatus overall = overall(results);
        List<String> issues = results.stream().filter(r -> r.status() != CheckStatus.PASS).map(CheckResult::message).toList();
        return new InspectionReport("1.0", skill.name(), skill.root().toString(), overall, score(results), readiness(overall), List.copyOf(results), issues);
    }

    private OverallStatus overall(List<CheckResult> results) {
        if (results.stream().anyMatch(r -> r.status() == CheckStatus.FAIL)) return OverallStatus.FAIL;
        if (results.isEmpty() || results.stream().anyMatch(r -> r.status() == CheckStatus.WARNING || r.status() == CheckStatus.UNKNOWN)) return OverallStatus.WARNING;
        return OverallStatus.READY;
    }

    private int score(List<CheckResult> results) {
        if (results.isEmpty()) return 0;
        int points = results.stream().mapToInt(result -> switch (result.status()) {
            case PASS -> 100; case WARNING -> 50; case FAIL, UNKNOWN -> 0;
        }).sum();
        return Math.round((float) points / results.size());
    }

    private String readiness(OverallStatus status) {
        return switch (status) { case READY -> "READY"; case WARNING -> "WARNING"; case FAIL -> "NOT READY"; };
    }
}
