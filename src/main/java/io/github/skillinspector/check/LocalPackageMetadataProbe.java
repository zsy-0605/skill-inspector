package io.github.skillinspector.check;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.skillinspector.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class LocalPackageMetadataProbe {
    private static final long MAX_METADATA_BYTES = 1024 * 1024;
    private static final int MAX_DIRECTORY_ENTRIES = 20_000;
    private static final Pattern PYTHON_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final Pattern NPM_NAME = Pattern.compile("(?:@[a-z0-9_.-]+/)?[a-z0-9_.-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAVEN_COORDINATE = Pattern.compile("[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+");

    private final Map<String, String> environment;
    private final ObjectMapper json = new ObjectMapper();

    LocalPackageMetadataProbe(Map<String, String> environment) { this.environment = environment; }

    private boolean isolated() {
        return "isolated".equalsIgnoreCase(environment.get("SKILL_INSPECTOR_PACKAGE_METADATA_MODE"));
    }

    PackageInstallation find(PackageRequirement requirement, Path skillRoot) {
        try {
            return switch (requirement.ecosystem()) {
                case PYTHON -> findPython(requirement.name(), skillRoot);
                case NPM -> findNpm(requirement.name(), skillRoot);
                case MAVEN -> findMaven(requirement, skillRoot);
            };
        } catch (IOException | SecurityException error) {
            return PackageInstallation.unknown();
        }
    }

    private PackageInstallation findPython(String packageName, Path skillRoot) throws IOException {
        if (!PYTHON_NAME.matcher(packageName).matches()) return PackageInstallation.unknown();
        String canonical = canonicalPython(packageName);
        for (Path root : pythonMetadataRoots(skillRoot)) {
            if (!Files.isDirectory(root)) continue;
            int visited = 0;
            try (Stream<Path> entries = Files.list(root)) {
                for (Path entry : entries.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList()) {
                    if (++visited > MAX_DIRECTORY_ENTRIES) return PackageInstallation.unknown();
                    String filename = entry.getFileName().toString();
                    Path metadata = filename.endsWith(".dist-info") ? entry.resolve("METADATA")
                            : filename.endsWith(".egg-info") && Files.isDirectory(entry) ? entry.resolve("PKG-INFO") : null;
                    if (metadata == null || !safeMetadataFile(metadata)) continue;
                    Map<String, String> headers = metadataHeaders(metadata);
                    if (canonical.equals(canonicalPython(headers.getOrDefault("Name", "")))) {
                        String version = headers.get("Version");
                        return version == null || version.isBlank() ? PackageInstallation.unknown() : PackageInstallation.found(version.strip());
                    }
                }
            }
        }
        return PackageInstallation.notFound();
    }

    private List<Path> pythonMetadataRoots(Path skillRoot) throws IOException {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        addPythonPrefix(roots, skillRoot.resolve(".venv"));
        addPythonPrefix(roots, skillRoot.resolve("venv"));
        addPythonPrefix(roots, environmentPath("VIRTUAL_ENV"));
        addPythonPrefix(roots, environmentPath("CONDA_PREFIX"));
        addPathList(roots, environment.get("PYTHONPATH"));
        if (!isolated()) {
            Path userHome = Path.of(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
            addPythonLibChildren(roots, userHome.resolve(".local/lib"));
            addPythonLibChildren(roots, Path.of("/usr/local/lib"));
            addPythonLibChildren(roots, Path.of("/usr/lib"));
        }
        return roots.stream().map(path -> path.toAbsolutePath().normalize()).toList();
    }

    private void addPythonPrefix(Set<Path> roots, Path prefix) throws IOException {
        if (prefix == null || !Files.isDirectory(prefix)) return;
        Path windows = prefix.resolve("Lib/site-packages");
        if (Files.isDirectory(windows)) roots.add(windows);
        addPythonLibChildren(roots, prefix.resolve("lib"));
    }

    private void addPythonLibChildren(Set<Path> roots, Path lib) throws IOException {
        if (!Files.isDirectory(lib)) return;
        int visited = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(lib, "python*")) {
            for (Path python : entries) {
                if (++visited > 100) return;
                Path site = python.resolve("site-packages");
                Path dist = python.resolve("dist-packages");
                if (Files.isDirectory(site)) roots.add(site);
                if (Files.isDirectory(dist)) roots.add(dist);
            }
        }
    }

    private void addPathList(Set<Path> roots, String value) {
        if (value == null || value.isBlank()) return;
        for (String item : value.split(Pattern.quote(System.getProperty("path.separator"))))
            if (!item.isBlank()) roots.add(Path.of(item));
    }

    private PackageInstallation findNpm(String packageName, Path skillRoot) throws IOException {
        if (!NPM_NAME.matcher(packageName).matches() || packageName.contains("..")) return PackageInstallation.unknown();
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        Path current = skillRoot.toAbsolutePath().normalize();
        while (current != null) {
            roots.add(current.resolve("node_modules"));
            if (isolated()) break;
            current = current.getParent();
        }
        addPathList(roots, environment.get("NODE_PATH"));
        for (Path root : roots) {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path manifest = normalizedRoot.resolve(packageName).resolve("package.json").normalize();
            if (!manifest.startsWith(normalizedRoot) || !safeMetadataFile(manifest)) continue;
            JsonNode document = json.readTree(manifest.toFile());
            String actualName = document.path("name").asText("");
            String version = document.path("version").asText("");
            if (actualName.equalsIgnoreCase(packageName) && !version.isBlank()) return PackageInstallation.found(version);
        }
        return PackageInstallation.notFound();
    }

    private PackageInstallation findMaven(PackageRequirement requirement, Path skillRoot) throws IOException {
        if (!MAVEN_COORDINATE.matcher(requirement.name()).matches()) return PackageInstallation.unknown();
        String[] coordinate = requirement.name().split(":", 2);
        LinkedHashSet<Path> repositories = new LinkedHashSet<>();
        Path configured = environmentPath("MAVEN_REPO_LOCAL");
        if (configured != null) repositories.add(configured);
        repositories.add(skillRoot.resolve(".m2/repository"));
        if (!isolated()) repositories.add(Path.of(System.getProperty("user.home", "."), ".m2", "repository"));
        PackageVersionMatcher matcher = new PackageVersionMatcher();
        String fallback = null;
        for (Path repository : repositories) {
            Path normalizedRepository = repository.toAbsolutePath().normalize();
            Path artifact = normalizedRepository.resolve(coordinate[0].replace('.', '/')).resolve(coordinate[1]).normalize();
            if (!artifact.startsWith(normalizedRepository) || !Files.isDirectory(artifact)) continue;
            int visited = 0;
            try (Stream<Path> versions = Files.list(artifact)) {
                for (Path versionDirectory : versions.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList()) {
                    if (++visited > MAX_DIRECTORY_ENTRIES) return PackageInstallation.unknown();
                    if (!Files.isDirectory(versionDirectory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(versionDirectory)) continue;
                    String version = versionDirectory.getFileName().toString();
                    if (!hasMavenArtifact(versionDirectory, coordinate[1], version)) continue;
                    fallback = version;
                    try {
                        if (matcher.matches(PackageEcosystem.MAVEN, requirement.required(), version))
                            return PackageInstallation.found(version);
                    } catch (IllegalArgumentException ignored) {
                        return PackageInstallation.found(version);
                    }
                }
            }
        }
        return fallback == null ? PackageInstallation.notFound() : PackageInstallation.found(fallback);
    }

    private boolean hasMavenArtifact(Path directory, String artifact, String version) {
        return safeMetadataFile(directory.resolve(artifact + "-" + version + ".pom"))
                || Files.isRegularFile(directory.resolve(artifact + "-" + version + ".jar"), LinkOption.NOFOLLOW_LINKS);
    }

    private Path environmentPath(String name) {
        String value = environment.get(name);
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private boolean safeMetadataFile(Path path) {
        try {
            return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)
                    && Files.size(path) <= MAX_METADATA_BYTES;
        } catch (IOException | SecurityException error) {
            return false;
        }
    }

    private Map<String, String> metadataHeaders(Path path) throws IOException {
        Map<String, String> headers = new HashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) break;
            int colon = line.indexOf(':');
            if (colon > 0) headers.putIfAbsent(line.substring(0, colon), line.substring(colon + 1).strip());
        }
        return headers;
    }

    private String canonicalPython(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[-_.]+", "-");
    }
}
