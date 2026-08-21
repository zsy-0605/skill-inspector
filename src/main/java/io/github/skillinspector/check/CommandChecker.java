package io.github.skillinspector.check;

import io.github.skillinspector.model.*;
import java.nio.file.Path;

public final class CommandChecker implements RequirementChecker {
    @Override public boolean supports(RequirementType type) { return type == RequirementType.COMMAND; }
    @Override public CheckResult check(SkillRequirement r, Path root, EnvironmentProbe env) {
        boolean found = env.commandExists(r.name());
        return result(r, found ? CheckStatus.PASS : CheckStatus.FAIL, found ? "FOUND" : "NOT FOUND",
                found ? "Command is available on PATH." : "Required command `" + r.name() + "` was not found on PATH.");
    }
}
