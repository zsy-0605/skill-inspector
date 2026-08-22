package io.github.skillinspector.check;

import io.github.skillinspector.model.*;
import java.nio.file.Path;

public final class EnvironmentVariableChecker implements RequirementChecker {
    @Override public boolean supports(RequirementType type) { return type == RequirementType.ENVIRONMENT_VARIABLE; }
    @Override public CheckResult check(Requirement r, Path root, EnvironmentProbe env) {
        boolean present = env.environmentVariablePresent(r.name());
        return result(r, present ? CheckStatus.PASS : CheckStatus.FAIL, present ? "PRESENT" : "MISSING",
                present ? "Environment variable is present; its value was not read or displayed." : "Required environment variable `" + r.name() + "` is missing.");
    }
}
