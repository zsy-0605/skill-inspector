package io.github.skillinspector.check;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class SystemEnvironmentProbe implements EnvironmentProbe {
    private static final Map<String, List<List<String>>> RUNTIME_COMMANDS = Map.of(
            "java", List.of(List.of("java", "--version")),
            "python", List.of(List.of("python3", "--version"), List.of("python", "--version")),
            "node", List.of(List.of("node", "--version")));
    private final Map<String, String> environment;
    private final String osName;

    public SystemEnvironmentProbe() { this(System.getenv(), System.getProperty("os.name", "unknown")); }
    SystemEnvironmentProbe(Map<String, String> environment, String osName) { this.environment = environment; this.osName = osName; }

    @Override public String operatingSystem() {
        String value = osName.toLowerCase(Locale.ROOT);
        if (value.contains("win")) return "windows";
        if (value.contains("mac") || value.contains("darwin")) return "macos";
        if (value.contains("nux") || value.contains("linux")) return "linux";
        return "unknown";
    }
    @Override public boolean commandExists(String command) {
        if (!command.matches("[A-Za-z0-9._+-]+")) return false;
        String path = environment.getOrDefault("PATH", "");
        List<String> extensions = operatingSystem().equals("windows")
                ? new java.util.ArrayList<>(Arrays.stream(environment.getOrDefault("PATHEXT", ".COM;.EXE;.BAT;.CMD").split(";")).map(String::toLowerCase).toList()) : new java.util.ArrayList<>(List.of(""));
        if (!extensions.contains("")) extensions.addFirst("");
        for (String directory : path.split(java.util.regex.Pattern.quote(System.getProperty("path.separator")))) {
            if (directory.isBlank()) continue;
            for (String extension : extensions) {
                Path candidate = Path.of(directory).resolve(command + extension);
                if (Files.isRegularFile(candidate) && (operatingSystem().equals("windows") || Files.isExecutable(candidate))) return true;
            }
        }
        return false;
    }
    @Override public boolean environmentVariablePresent(String name) { String value = environment.get(name); return value != null && !value.isBlank(); }
    @Override public boolean fileExists(Path path) { return Files.isRegularFile(path); }
    @Override public boolean directoryExists(Path path) { return Files.isDirectory(path); }
    @Override public Optional<String> runtimeVersion(String runtime) {
        List<List<String>> candidates = RUNTIME_COMMANDS.get(runtime.toLowerCase(Locale.ROOT));
        if (candidates == null) return Optional.empty();
        List<String> command = candidates.stream().filter(candidate -> commandExists(candidate.getFirst())).findFirst().orElse(null);
        if (command == null) return Optional.empty();
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(Duration.ofSeconds(3).toMillis(), TimeUnit.MILLISECONDS)) { process.destroyForcibly(); return Optional.empty(); }
            return Optional.of(new VersionMatcher().normalize(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return Optional.empty();
        } finally { if (process != null) process.destroy(); }
    }
    @Override public io.github.skillinspector.model.PackageInstallation packageInstallation(
            io.github.skillinspector.model.PackageRequirement requirement, Path skillRoot) {
        return new LocalPackageMetadataProbe(environment).find(requirement, skillRoot);
    }
}
