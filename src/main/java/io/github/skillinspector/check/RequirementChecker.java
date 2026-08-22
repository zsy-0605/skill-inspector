package io.github.skillinspector.check;

import io.github.skillinspector.model.*;
import java.nio.file.Path;

public interface RequirementChecker {
    boolean supports(RequirementType type);
    CheckResult check(Requirement requirement, Path skillRoot, EnvironmentProbe environment);

    default CheckResult result(Requirement requirement, CheckStatus rawStatus, String actual, String message) {
        CheckStatus status = rawStatus;
        if (rawStatus == CheckStatus.FAIL && requirement.necessity() != RequirementNecessity.REQUIRED) status = CheckStatus.WARNING;
        PackageEcosystem ecosystem = requirement instanceof PackageRequirement packages ? packages.ecosystem() : null;
        String version = ecosystem == null ? null : requirement.required();
        return new CheckResult(requirement.type(), ecosystem, version, requirement.name(), requirement.required(), actual, status,
                requirement.source(), requirement.necessity(), requirement.confidence(), requirement.evidence(), requirement.matched(),
                requirement.inferenceRule(), message);
    }
}
