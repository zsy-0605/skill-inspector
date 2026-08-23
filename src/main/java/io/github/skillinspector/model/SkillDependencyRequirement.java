package io.github.skillinspector.model;

public record SkillDependencyRequirement(
        SkillIdentity identity, String requiredVersion, RequirementNecessity necessity,
        RequirementSource source, Confidence confidence, String evidence,
        String matched, String inferenceRule) implements Requirement {

    public SkillDependencyRequirement {
        if (identity == null) throw new IllegalArgumentException("Skill dependency identity is required");
        if (requiredVersion == null || requiredVersion.isBlank()) requiredVersion = "*";
    }

    @Override public RequirementType type() { return RequirementType.SKILL; }
    @Override public String name() { return identity.canonicalId(); }
    @Override public String required() { return requiredVersion; }

    public static SkillDependencyRequirement declared(SkillIdentity identity, String version,
                                                       RequirementNecessity necessity, String evidence) {
        return new SkillDependencyRequirement(identity, version, necessity, RequirementSource.DECLARED,
                null, evidence, null, null);
    }

    public static SkillDependencyRequirement inferred(SkillIdentity identity, String version,
                                                       RequirementNecessity necessity, Confidence confidence,
                                                       String evidence, String matched, String inferenceRule) {
        return new SkillDependencyRequirement(identity, version, necessity, RequirementSource.INFERRED,
                confidence, evidence, matched, inferenceRule);
    }
}
