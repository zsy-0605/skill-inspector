package io.github.skillinspector.check;

import io.github.skillinspector.model.*;
import java.nio.file.Path;
import java.util.Optional;

public final class RuntimeChecker implements RequirementChecker {
    private final VersionMatcher versions = new VersionMatcher();
    @Override public boolean supports(RequirementType type) { return type == RequirementType.RUNTIME; }
    @Override public CheckResult check(SkillRequirement r, Path root, EnvironmentProbe env) {
        Optional<String> actual = env.runtimeVersion(r.name());
        if (actual.isEmpty()) return result(r, CheckStatus.FAIL, "NOT FOUND", "Runtime `" + r.name() + "` was not found.");
        try {
            boolean matches = versions.matches(r.required(), actual.get());
            return result(r, matches ? CheckStatus.PASS : CheckStatus.FAIL, actual.get(), matches ? "Runtime requirement is satisfied." : "Installed runtime does not satisfy " + r.required() + ".");
        } catch (IllegalArgumentException e) {
            return result(r, CheckStatus.UNKNOWN, actual.get(), e.getMessage());
        }
    }
}
