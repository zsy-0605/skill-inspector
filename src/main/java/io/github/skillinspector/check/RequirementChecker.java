package io.github.skillinspector.check;

import io.github.skillinspector.model.*;
import java.nio.file.Path;

public interface RequirementChecker {
    boolean supports(RequirementType type);
    CheckResult check(SkillRequirement requirement, Path skillRoot, EnvironmentProbe environment);

    default CheckResult result(SkillRequirement requirement, CheckStatus rawStatus, String actual, String message) {
        CheckStatus status = rawStatus;
        if (rawStatus == CheckStatus.FAIL && requirement.necessity() != RequirementNecessity.REQUIRED) status = CheckStatus.WARNING;
        return new CheckResult(requirement.type(), requirement.name(), requirement.required(), actual, status,
                requirement.source(), requirement.necessity(), requirement.confidence(), requirement.evidence(), requirement.matched(),
                requirement.inferenceRule(), message);
    }
}
