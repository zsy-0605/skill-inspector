package io.github.skillinspector.model;

public record CapabilityRequirement(
        CapabilityKind capabilityKind, String name, String required, RequirementNecessity necessity,
        RequirementSource source, Confidence confidence, String evidence,
        String matched, String inferenceRule) implements Requirement {

    @Override public RequirementType type() { return RequirementType.CAPABILITY; }

    public static CapabilityRequirement declared(CapabilityKind kind, String name,
                                                 RequirementNecessity necessity, String evidence) {
        return new CapabilityRequirement(kind, name, "available", necessity, RequirementSource.DECLARED,
                null, evidence, null, null);
    }

    public static CapabilityRequirement inferred(CapabilityKind kind, String name,
                                                 RequirementNecessity necessity, Confidence confidence,
                                                 String evidence, String matched, String inferenceRule) {
        return new CapabilityRequirement(kind, name, "available", necessity, RequirementSource.INFERRED,
                confidence, evidence, matched, inferenceRule);
    }
}
