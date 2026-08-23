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
    private final DependencyGraphResolver dependencyResolver;

    public InspectionService() { this(new SkillParser(), new SystemEnvironmentProbe(), new DependencyGraphResolver()); }
    public InspectionService(SkillParser parser, EnvironmentProbe environment) {
        this(parser, environment, new DependencyGraphResolver());
    }
    public InspectionService(SkillParser parser, EnvironmentProbe environment, DependencyGraphResolver dependencyResolver) {
        this.parser = parser;
        this.environment = environment;
        this.dependencyResolver = dependencyResolver;
        this.checkers = List.of(new RuntimeChecker(), new CommandChecker(), new EnvironmentVariableChecker(),
                new FileChecker(), new OperatingSystemChecker(), new PackageChecker(), new CapabilityChecker());
    }

    public InspectionReport inspect(Path target) {
        SkillDefinition skill = parser.parse(target);
        return inspect(skill, skill.requirements());
    }

    public InspectionReport verify(Path target, List<Requirement> semanticRequirements) {
        SkillDefinition skill = parser.parse(target);
        return inspect(skill, merge(skill.requirements(), semanticRequirements));
    }

    private InspectionReport inspect(SkillDefinition skill, List<Requirement> requirements) {
        List<CheckResult> results = new ArrayList<>();
        for (Requirement requirement : requirements) {
            if (requirement instanceof SkillDependencyRequirement) continue;
            RequirementChecker checker = checkers.stream().filter(item -> item.supports(requirement.type())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No checker for " + requirement.type()));
            results.add(checker.check(requirement, skill.root(), environment));
        }
        List<SkillDependencyRequirement> skillRequirements = requirements.stream()
                .filter(SkillDependencyRequirement.class::isInstance).map(SkillDependencyRequirement.class::cast).toList();
        results.addAll(dependencyResolver.resolve(skill.name(), skillRequirements));
        OverallStatus overall = overall(results);
        List<String> issues = results.stream().filter(r -> r.status() != CheckStatus.PASS).map(CheckResult::message).toList();
        return new InspectionReport("1.2", skill.name(), skill.root().toString(), overall, score(results), readiness(overall), List.copyOf(results), issues);
    }

    private List<Requirement> merge(List<Requirement> discovered, List<Requirement> semantic) {
        Map<String, Requirement> merged = new LinkedHashMap<>();
        for (Requirement item : discovered) merged.put(key(item), item);
        for (Requirement item : semantic) merged.merge(key(item), item, this::prefer);
        return List.copyOf(merged.values());
    }

    private Requirement prefer(Requirement left, Requirement right) {
        if (left.source() != right.source()) {
            Requirement declared = left.source() == RequirementSource.DECLARED ? left : right;
            RequirementNecessity strongest = rank(left.necessity()) >= rank(right.necessity())
                    ? left.necessity() : right.necessity();
            return withNecessity(declared, strongest);
        }
        int necessity = Integer.compare(rank(right.necessity()), rank(left.necessity()));
        if (necessity != 0) return necessity > 0 ? right : left;
        return confidence(right.confidence()) > confidence(left.confidence()) ? right : left;
    }

    private Requirement withNecessity(Requirement item, RequirementNecessity necessity) {
        if (item.necessity() == necessity) return item;
        if (item instanceof PackageRequirement value)
            return new PackageRequirement(value.ecosystem(), value.name(), value.required(), necessity, value.source(),
                    value.confidence(), value.evidence(), value.matched(), value.inferenceRule());
        if (item instanceof CapabilityRequirement value)
            return new CapabilityRequirement(value.capabilityKind(), value.name(), value.required(), necessity, value.source(),
                    value.confidence(), value.evidence(), value.matched(), value.inferenceRule());
        if (item instanceof SkillDependencyRequirement value)
            return new SkillDependencyRequirement(value.identity(), value.requiredVersion(), necessity, value.source(),
                    value.confidence(), value.evidence(), value.matched(), value.inferenceRule());
        SkillRequirement value = (SkillRequirement) item;
        return new SkillRequirement(value.type(), value.name(), value.required(), necessity, value.source(),
                value.confidence(), value.evidence(), value.matched(), value.inferenceRule());
    }

    private String key(Requirement item) {
        String qualifier = item instanceof PackageRequirement packages ? packages.ecosystem().jsonValue()
                : item instanceof CapabilityRequirement capability ? capability.capabilityKind().jsonValue() : "";
        String name = item instanceof CapabilityRequirement || item instanceof SkillDependencyRequirement
                ? item.name() : item.name().toLowerCase(Locale.ROOT);
        return item.type() + "\u0000" + qualifier + "\u0000" + name;
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
