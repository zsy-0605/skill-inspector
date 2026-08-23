package io.github.skillinspector.check;

import java.nio.file.Path;
import java.util.Optional;
import io.github.skillinspector.model.PackageInstallation;
import io.github.skillinspector.model.PackageRequirement;
import io.github.skillinspector.model.CapabilityMatch;
import io.github.skillinspector.model.CapabilityRequirement;

public interface EnvironmentProbe {
    String operatingSystem();
    boolean commandExists(String command);
    boolean environmentVariablePresent(String name);
    boolean fileExists(Path path);
    boolean directoryExists(Path path);
    Optional<String> runtimeVersion(String runtime);
    default PackageInstallation packageInstallation(PackageRequirement requirement, Path skillRoot) {
        return PackageInstallation.unknown();
    }
    default CapabilityMatch capability(CapabilityRequirement requirement) {
        return CapabilityMatch.noSnapshot();
    }
}
