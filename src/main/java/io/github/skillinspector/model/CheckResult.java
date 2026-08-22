package io.github.skillinspector.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckResult(
        RequirementType type, PackageEcosystem ecosystem, String version, String name, String required, String actual,
        CheckStatus status, RequirementSource source, RequirementNecessity necessity, Confidence confidence,
        String evidence, String matched, String inferenceRule, String message) {}
