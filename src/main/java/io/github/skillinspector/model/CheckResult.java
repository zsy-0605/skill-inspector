package io.github.skillinspector.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckResult(
        RequirementType type, String name, String required, String actual,
        CheckStatus status, RequirementSource source, Confidence confidence,
        String evidence, String message) {}
