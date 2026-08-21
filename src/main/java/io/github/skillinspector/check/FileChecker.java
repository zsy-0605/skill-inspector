package io.github.skillinspector.check;

import io.github.skillinspector.model.*;
import java.nio.file.Path;

public final class FileChecker implements RequirementChecker {
    @Override public boolean supports(RequirementType type) { return type == RequirementType.FILE || type == RequirementType.DIRECTORY; }
    @Override public CheckResult check(SkillRequirement r, Path root, EnvironmentProbe env) {
        Path declared = Path.of(r.name());
        Path resolved = declared.isAbsolute() ? declared.normalize() : root.resolve(declared).normalize();
        boolean exists = r.type() == RequirementType.FILE ? env.fileExists(resolved) : env.directoryExists(resolved);
        String kind = r.type() == RequirementType.FILE ? "file" : "directory";
        return result(r, exists ? CheckStatus.PASS : CheckStatus.FAIL, exists ? "EXISTS" : "MISSING",
                exists ? "Required " + kind + " exists." : "Required " + kind + " `" + r.name() + "` is missing.");
    }
}
