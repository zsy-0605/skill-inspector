package io.github.skillinspector.model;

public record SkillRequirement(
        RequirementType type, String name, String required, RequirementNecessity necessity,
        RequirementSource source, Confidence confidence, String evidence,
        String matched, String inferenceRule) {
    public static SkillRequirement declared(RequirementType type, String name, String required, boolean optional) {
        return new SkillRequirement(type, name, required,
                optional ? RequirementNecessity.OPTIONAL : RequirementNecessity.REQUIRED, RequirementSource.DECLARED, null,
                "SKILL.md frontmatter", null, null);
    }
    public static SkillRequirement inferred(RequirementType type, String name, String required, Confidence confidence,
                                            String evidence, String matched, String inferenceRule) {
        return inferred(type, name, required, RequirementNecessity.CONDITIONAL, confidence, evidence, matched, inferenceRule);
    }
    public static SkillRequirement inferred(RequirementType type, String name, String required,
                                            RequirementNecessity necessity, Confidence confidence,
                                            String evidence, String matched, String inferenceRule) {
        return new SkillRequirement(type, name, required, necessity, RequirementSource.INFERRED, confidence,
                evidence, matched, inferenceRule);
    }
}
