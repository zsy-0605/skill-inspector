package io.github.skillinspector.core;

import io.github.skillinspector.check.*;
import io.github.skillinspector.model.*;
import io.github.skillinspector.parse.SkillParser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        return inspect(skill, skill.requirements());
    }

    public InspectionReport verify(Path target, List<SkillRequirement> semanticRequirements) {
        SkillDefinition skill = parser.parse(target);
        return inspect(skill, merge(skill.requirements(), semanticRequirements));
    }

    private InspectionReport inspect(SkillDefinition skill, List<SkillRequirement> requirements) {
        List<CheckResult> results = new ArrayList<>();
        for (SkillRequirement requirement : requirements) {
            RequirementChecker checker = checkers.stream().filter(item -> item.supports(requirement.type())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No checker for " + requirement.type()));
            results.add(checker.check(requirement, skill.root(), environment));
        }
        OverallStatus overall = overall(results);
        List<String> issues = results.stream().filter(r -> r.status() != CheckStatus.PASS).map(CheckResult::message).toList();
        return new InspectionReport("1.0", skill.name(), skill.root().toString(), overall, score(results), readiness(overall), List.copyOf(results), issues);
    }

    private List<SkillRequirement> merge(List<SkillRequirement> discovered, List<SkillRequirement> semantic) {
        Map<String, SkillRequirement> merged = new LinkedHashMap<>();
        for (SkillRequirement item : discovered) merged.put(key(item), item);
        for (SkillRequirement item : semantic) merged.merge(key(item), item, this::prefer);
        return List.copyOf(merged.values());
    }

    private SkillRequirement prefer(SkillRequirement left, SkillRequirement right) {
        if (left.source() != right.source()) return left.source() == RequirementSource.DECLARED ? left : right;
        int necessity = Integer.compare(rank(right.necessity()), rank(left.necessity()));
        if (necessity != 0) return necessity > 0 ? right : left;
        return confidence(right.confidence()) > confidence(left.confidence()) ? right : left;
    }

    private String key(SkillRequirement item) {
        return item.type() + "\u0000" + item.name().toLowerCase(Locale.ROOT);
    }

    private int rank(RequirementNecessity necessity) {
        return switch (necessity) { case REQUIRED -> 3; case CONDITIONAL -> 2; case OPTIONAL -> 1; };
    }

    private int confidence(Confidence confidence) {
        if (confidence == null) return 4;
        return switch (confidence) { case HIGH -> 3; case MEDIUM -> 2; case LOW -> 1; };
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
