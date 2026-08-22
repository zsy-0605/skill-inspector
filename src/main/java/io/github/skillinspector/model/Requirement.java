package io.github.skillinspector.model;

public sealed interface Requirement permits SkillRequirement, PackageRequirement {
    RequirementType type();
    String name();
    String required();
    RequirementNecessity necessity();
    RequirementSource source();
    Confidence confidence();
    String evidence();
    String matched();
    String inferenceRule();
}
