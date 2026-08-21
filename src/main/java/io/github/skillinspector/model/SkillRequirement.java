package io.github.skillinspector.model;

public record SkillRequirement(
        RequirementType type, String name, String required, boolean optional,
        RequirementSource source, Confidence confidence, String evidence,
        String matched, String inferenceRule) {
    public static SkillRequirement declared(RequirementType type, String name, String required, boolean optional) {
        return new SkillRequirement(type, name, required, optional, RequirementSource.DECLARED, null,
                "SKILL.md frontmatter", null, null);
    }
    public static SkillRequirement inferred(RequirementType type, String name, String required, Confidence confidence,
                                            String evidence, String matched, String inferenceRule) {
        return new SkillRequirement(type, name, required, false, RequirementSource.INFERRED, confidence,
                evidence, matched, inferenceRule);
    }
}
