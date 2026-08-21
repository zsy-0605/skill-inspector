package io.github.skillinspector.model;

public record SkillRequirement(
        RequirementType type, String name, String required, boolean optional,
        RequirementSource source, Confidence confidence, String evidence) {
    public static SkillRequirement declared(RequirementType type, String name, String required, boolean optional) {
        return new SkillRequirement(type, name, required, optional, RequirementSource.DECLARED, null, "SKILL.md frontmatter");
    }
    public static SkillRequirement inferred(RequirementType type, String name, String required, Confidence confidence, String evidence) {
        return new SkillRequirement(type, name, required, false, RequirementSource.INFERRED, confidence, evidence);
    }
}
