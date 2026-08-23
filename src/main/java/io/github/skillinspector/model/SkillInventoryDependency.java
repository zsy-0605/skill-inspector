package io.github.skillinspector.model;

public record SkillInventoryDependency(
        SkillIdentity identity, String version, RequirementNecessity necessity,
        RequirementSource source, Confidence confidence, String evidence,
        String matched, String inferenceRule) {
    public SkillInventoryDependency {
        if (identity == null) throw new IllegalArgumentException("Inventory dependency identity is required");
        if (version == null || version.isBlank()) version = "*";
        if (necessity == null) necessity = RequirementNecessity.REQUIRED;
        if (source == null) source = RequirementSource.DECLARED;
    }
}
