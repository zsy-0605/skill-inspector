package io.github.skillinspector.check;

import java.nio.file.Path;
import java.util.Optional;

public interface EnvironmentProbe {
    String operatingSystem();
    boolean commandExists(String command);
    boolean environmentVariablePresent(String name);
    boolean fileExists(Path path);
    boolean directoryExists(Path path);
    Optional<String> runtimeVersion(String runtime);
}
