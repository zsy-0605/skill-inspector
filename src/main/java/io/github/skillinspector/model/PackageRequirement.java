package io.github.skillinspector.model;

public record PackageRequirement(
        PackageEcosystem ecosystem, String name, String required, RequirementNecessity necessity,
        RequirementSource source, Confidence confidence, String evidence,
        String matched, String inferenceRule) implements Requirement {

    @Override public RequirementType type() { return RequirementType.PACKAGE; }

    public static PackageRequirement declared(PackageEcosystem ecosystem, String name, String required,
                                              RequirementNecessity necessity, String evidence) {
        return new PackageRequirement(ecosystem, name, required, necessity, RequirementSource.DECLARED,
                null, evidence, null, null);
    }

    public static PackageRequirement inferred(PackageEcosystem ecosystem, String name, String required,
                                              RequirementNecessity necessity, Confidence confidence,
                                              String evidence, String matched, String inferenceRule) {
        return new PackageRequirement(ecosystem, name, required, necessity, RequirementSource.INFERRED,
                confidence, evidence, matched, inferenceRule);
    }
}
