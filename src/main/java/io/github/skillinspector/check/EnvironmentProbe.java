package io.github.skillinspector.check;

import java.nio.file.Path;
import java.util.Optional;
import io.github.skillinspector.model.PackageInstallation;
import io.github.skillinspector.model.PackageRequirement;

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
}
