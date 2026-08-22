package io.github.skillinspector.check;

import io.github.skillinspector.model.*;

import java.nio.file.Path;

public final class PackageChecker implements RequirementChecker {
    private final PackageVersionMatcher versions = new PackageVersionMatcher();

    @Override public boolean supports(RequirementType type) { return type == RequirementType.PACKAGE; }

    @Override public CheckResult check(Requirement requirement, Path root, EnvironmentProbe environment) {
        if (!(requirement instanceof PackageRequirement packages))
            throw new IllegalArgumentException("Package checks require PackageRequirement");
        PackageInstallation installation = environment.packageInstallation(packages, root);
        if (installation.state() == PackageInstallation.State.UNKNOWN)
            return result(packages, CheckStatus.UNKNOWN, "UNKNOWN",
                    "Package availability could not be determined without executing package code or a package manager.");
        if (installation.state() == PackageInstallation.State.NOT_FOUND)
            return result(packages, CheckStatus.FAIL, "NOT FOUND",
                    "Required " + packages.ecosystem().jsonValue() + " package `" + packages.name() + "` was not found in the inspected local package metadata.");
        try {
            boolean matches = versions.matches(packages.ecosystem(), packages.required(), installation.version());
            return result(packages, matches ? CheckStatus.PASS : CheckStatus.FAIL, installation.version(),
                    matches ? "Package requirement is satisfied."
                            : "Installed package version does not satisfy " + packages.required() + ".");
        } catch (IllegalArgumentException error) {
            return result(packages, CheckStatus.UNKNOWN, installation.version(), error.getMessage());
        }
    }
}
