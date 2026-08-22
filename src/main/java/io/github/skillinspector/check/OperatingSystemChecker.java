package io.github.skillinspector.check;

import io.github.skillinspector.model.*;
import java.nio.file.Path;
import java.util.Arrays;

public final class OperatingSystemChecker implements RequirementChecker {
    @Override public boolean supports(RequirementType type) { return type == RequirementType.OPERATING_SYSTEM; }
    @Override public CheckResult check(Requirement r, Path root, EnvironmentProbe env) {
        String current = env.operatingSystem();
        boolean supported = Arrays.stream(r.required().split(",")).map(String::strip).anyMatch(current::equalsIgnoreCase);
        return result(r, supported ? CheckStatus.PASS : CheckStatus.FAIL, current,
                supported ? "Current operating system is supported." : "Unsupported operating system; supported: " + r.required() + ".");
    }
}
