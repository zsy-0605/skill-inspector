package io.github.skillinspector.model;

import java.util.List;

public record InspectionReport(
        String schemaVersion, String skill, String target, OverallStatus status,
        int score, String readiness, List<CheckResult> checks, List<String> issues) {}
