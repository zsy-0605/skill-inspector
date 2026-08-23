package io.github.skillinspector.check;

import io.github.skillinspector.model.*;

import java.nio.file.Path;

public final class CapabilityChecker implements RequirementChecker {
    @Override public boolean supports(RequirementType type) { return type == RequirementType.CAPABILITY; }

    @Override public CheckResult check(Requirement requirement, Path skillRoot, EnvironmentProbe environment) {
        if (!(requirement instanceof CapabilityRequirement capability))
            throw new IllegalArgumentException("Capability checks require CapabilityRequirement");
        if (!"available".equals(capability.required()))
            return result(capability, CheckStatus.UNKNOWN, "UNKNOWN",
                    "Unsupported capability constraint: " + capability.required());
        CapabilityMatch match = environment.capability(capability);
        return switch (match.availability()) {
            case AVAILABLE -> result(capability, CheckStatus.PASS, match.actual(),
                    "Capability is advertised as available by the runtime snapshot.", match);
            case UNAVAILABLE -> result(capability, CheckStatus.FAIL, match.actual(),
                    match.resolvedCapability() == null
                            ? "Capability is not listed in the complete runtime inventory."
                            : "Capability is advertised as unavailable by the runtime snapshot.", match);
            case CONFIGURED -> result(capability, CheckStatus.UNKNOWN, match.actual(),
                    "Capability is configured but the snapshot does not establish runtime availability.", match);
            case UNKNOWN -> result(capability, CheckStatus.UNKNOWN, match.actual(),
                    "Capability availability cannot be determined from the runtime snapshot.", match);
        };
    }
}
