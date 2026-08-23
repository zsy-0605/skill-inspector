package io.github.skillinspector.parse;

import io.github.skillinspector.model.*;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class SkillParser {
    private static final long MAX_FILE_BYTES = 1024 * 1024;
    private static final Set<String> SCRIPT_EXTENSIONS = Set.of(".sh", ".bash", ".py", ".js", ".mjs", ".cjs");
    private static final Set<String> SHELL_BUILTINS = Set.of("cd", "echo", "printf", "test", "export", "set", "if", "then", "fi", "for", "do", "done", "case", "esac", "function", "source", ".", "touch", "mkdir", "rm", "mv", "cp");
    private static final Set<String> INFERABLE_COMMANDS = Set.of(
            "git", "curl", "wget", "ffmpeg", "ffprobe", "pdftotext", "docker", "mvn", "npm", "npx",
            "python", "python3", "java", "node", "bash", "sh", "convert", "magick");
    private static final Pattern SIMPLE_COMMAND = Pattern.compile("^\\s*(?:sudo\\s+)?([A-Za-z][A-Za-z0-9._+-]*)\\s+(?:[^|;&]|$)");
    private static final Pattern SENSITIVE_LITERAL = Pattern.compile(
            "(?i)([A-Z0-9_]*(?:TOKEN|SECRET|PASSWORD|PASSWD|API_KEY)[A-Z0-9_]*\\s*=\\s*)(\\\"[^\\\"]*\\\"|'[^']*'|[^\\s]+)");
    private static final Pattern SENSITIVE_TOKEN = Pattern.compile(
            "(?i)\\b(?:bearer\\s+)?(?:sk-|gh[opusr]_|xox[baprs]-)[A-Za-z0-9._-]{8,}");
    private static final int MAX_MATCHED_LENGTH = 240;

    public SkillDefinition parse(Path target) {
        Path root = target.toAbsolutePath().normalize();
        Path skillFile = root.resolve("SKILL.md");
        if (!Files.isDirectory(root)) throw new SkillParseException("Target is not a directory: " + root);
        if (!Files.isRegularFile(skillFile)) throw new SkillParseException("Target has no SKILL.md: " + root);
        try {
            String content = Files.readString(skillFile, StandardCharsets.UTF_8);
            Map<String, Object> frontmatter = parseFrontmatter(content);
            String name = stringValue(frontmatter.getOrDefault("name", root.getFileName().toString()));
            List<Requirement> requirements = new ArrayList<>();
            parseCompatibility(frontmatter.get("compatibility"), requirements);
            inferFromScripts(root, requirements);
            requirements.addAll(new PackageManifestParser().parse(root));
            return new SkillDefinition(name, root, deduplicate(requirements));
        } catch (IOException | ClassCastException e) {
            throw new SkillParseException("Cannot parse " + skillFile + ": " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFrontmatter(String content) {
        if (!content.startsWith("---")) return Map.of();
        int firstEnd = content.indexOf('\n');
        int closing = content.indexOf("\n---", firstEnd);
        if (closing < 0) throw new SkillParseException("SKILL.md has unclosed YAML frontmatter");
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setCodePointLimit(2_000_000);
        Object loaded = new Yaml(new SafeConstructor(options)).load(content.substring(firstEnd + 1, closing));
        return loaded instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private void parseCompatibility(Object raw, List<Requirement> out) {
        if (!(raw instanceof Map<?, ?> compatibility)) return;
        parseRuntimes(compatibility.get("runtimes"), out);
        parseList(compatibility.get("commands"), RequirementType.COMMAND, out);
        parseList(compatibility.get("env"), RequirementType.ENVIRONMENT_VARIABLE, out);
        parseList(compatibility.get("files"), RequirementType.FILE, out);
        parseList(compatibility.get("directories"), RequirementType.DIRECTORY, out);
        parsePackages(compatibility.get("packages"), out);
        parseCapabilities(compatibility.get("capabilities"), out);
        parseSkills(compatibility.get("skills"), out);
        Object os = compatibility.get("os");
        if (os == null) os = compatibility.get("supportedOs");
        if (os instanceof Collection<?> values) {
            String joined = values.stream().map(this::stringValue).map(String::toLowerCase).reduce((a, b) -> a + "," + b).orElse("");
            out.add(SkillRequirement.declared(RequirementType.OPERATING_SYSTEM, "operating-system", joined, false));
        }
    }

    private void parseSkills(Object raw, List<Requirement> out) {
        if (!(raw instanceof Collection<?> values)) return;
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> map) || map.get("name") == null)
                throw new SkillParseException("skill entries require name");
            RequirementNecessity necessity;
            if (map.containsKey("necessity")) {
                try { necessity = RequirementNecessity.valueOf(stringValue(map.get("necessity")).toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException error) { throw new SkillParseException("Invalid skill necessity: " + map.get("necessity")); }
            } else necessity = Boolean.parseBoolean(stringValue(map.containsKey("optional") ? map.get("optional") : false))
                    ? RequirementNecessity.OPTIONAL : RequirementNecessity.REQUIRED;
            String version = stringValue(map.containsKey("version") ? map.get("version") : "*").strip();
            String namespace = map.containsKey("namespace") ? stringValue(map.get("namespace")).strip() : null;
            try {
                out.add(SkillDependencyRequirement.declared(new SkillIdentity(namespace, stringValue(map.get("name")).strip()),
                        version.isEmpty() ? "*" : version, necessity, "SKILL.md frontmatter"));
            } catch (IllegalArgumentException error) { throw new SkillParseException(error.getMessage()); }
        }
    }

    private void parseCapabilities(Object raw, List<Requirement> out) {
        if (!(raw instanceof Collection<?> values)) return;
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> map) || map.get("capabilityKind") == null || map.get("name") == null)
                throw new SkillParseException("capability entries require capabilityKind and name");
            RequirementNecessity necessity;
            if (map.containsKey("necessity")) {
                try { necessity = RequirementNecessity.valueOf(stringValue(map.get("necessity")).toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException error) { throw new SkillParseException("Invalid capability necessity: " + map.get("necessity")); }
            } else necessity = Boolean.parseBoolean(stringValue(map.containsKey("optional") ? map.get("optional") : false))
                    ? RequirementNecessity.OPTIONAL : RequirementNecessity.REQUIRED;
            String name = stringValue(map.get("name"));
            if (name.isBlank()) throw new SkillParseException("capability name must not be blank");
            CapabilityKind kind;
            try { kind = CapabilityKind.fromJson(stringValue(map.get("capabilityKind"))); }
            catch (IllegalArgumentException error) {
                throw new SkillParseException("Invalid capabilityKind: " + map.get("capabilityKind"));
            }
            out.add(CapabilityRequirement.declared(kind, name, necessity, "SKILL.md frontmatter"));
        }
    }

    private void parsePackages(Object raw, List<Requirement> out) {
        if (!(raw instanceof Collection<?> values)) return;
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> map))
                throw new SkillParseException("package entries require ecosystem and name");
            Object ecosystem = map.get("ecosystem");
            Object name = map.get("name");
            if (ecosystem == null || name == null)
                throw new SkillParseException("package entries require ecosystem and name");
            String version = stringValue(map.containsKey("version") ? map.get("version")
                    : map.containsKey("required") ? map.get("required") : "*").strip();
            RequirementNecessity necessity;
            if (map.containsKey("necessity")) {
                try { necessity = RequirementNecessity.valueOf(stringValue(map.get("necessity")).toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException error) { throw new SkillParseException("Invalid package necessity: " + map.get("necessity")); }
            } else necessity = Boolean.parseBoolean(stringValue(map.containsKey("optional") ? map.get("optional") : false))
                    ? RequirementNecessity.OPTIONAL : RequirementNecessity.REQUIRED;
            out.add(PackageRequirement.declared(PackageEcosystem.fromJson(stringValue(ecosystem)), stringValue(name),
                    version.isEmpty() ? "*" : version, necessity, "SKILL.md frontmatter"));
        }
    }

    private void parseRuntimes(Object raw, List<Requirement> out) {
        if (!(raw instanceof Map<?, ?> map)) return;
        map.forEach((key, value) -> {
            if (value instanceof Map<?, ?> details) {
                String version = stringValue(details.containsKey("version") ? details.get("version") : "*").trim();
                boolean optional = Boolean.parseBoolean(stringValue(details.containsKey("optional") ? details.get("optional") : false));
                out.add(SkillRequirement.declared(RequirementType.RUNTIME, stringValue(key).toLowerCase(), version, optional));
            } else out.add(SkillRequirement.declared(RequirementType.RUNTIME, stringValue(key).toLowerCase(), stringValue(value), false));
        });
    }

    private void parseList(Object raw, RequirementType type, List<Requirement> out) {
        if (!(raw instanceof Collection<?> values)) return;
        for (Object value : values) {
            if (value instanceof Map<?, ?> map) {
                Object identity = type == RequirementType.FILE || type == RequirementType.DIRECTORY ? map.get("path") : map.get("name");
                if (identity == null) throw new SkillParseException(type + " object requires " + (type == RequirementType.FILE || type == RequirementType.DIRECTORY ? "path" : "name"));
                boolean optional = Boolean.parseBoolean(stringValue(map.containsKey("optional") ? map.get("optional") : false));
                out.add(SkillRequirement.declared(type, stringValue(identity), "present", optional));
            } else out.add(SkillRequirement.declared(type, stringValue(value), "present", false));
        }
    }

    private void inferFromScripts(Path root, List<Requirement> out) throws IOException {
        Path scripts = root.resolve("scripts");
        if (!Files.isDirectory(scripts)) return;
        try (Stream<Path> paths = Files.walk(scripts, 5)) {
            for (Path path : paths.filter(candidate -> Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
                    .filter(candidate -> !Files.isSymbolicLink(candidate)).sorted().toList()) {
                if (Files.size(path) > MAX_FILE_BYTES || !isScript(path)) continue;
                String relative = root.relativize(path).toString().replace('\\', '/');
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                inferRuntime(lines, relative, out);
                inferCommands(lines, relative, out);
            }
        }
    }

    private boolean isScript(Path path) {
        String lower = path.getFileName().toString().toLowerCase();
        return SCRIPT_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private void inferRuntime(List<String> lines, String evidence, List<Requirement> out) {
        String name = evidence.toLowerCase();
        if (name.endsWith(".py"))
            out.add(SkillRequirement.inferred(RequirementType.RUNTIME, "python", "*", Confidence.HIGH,
                    evidence, evidence, "script-extension"));
        else if (!lines.isEmpty() && lines.getFirst().matches("^#!.*\\bpython[0-9.]*\\b.*"))
            out.add(SkillRequirement.inferred(RequirementType.RUNTIME, "python", "*", Confidence.HIGH,
                    evidence + ":1", sanitizeMatched(lines.getFirst()), "shebang"));
        if (name.matches(".*\\.(js|mjs|cjs)$"))
            out.add(SkillRequirement.inferred(RequirementType.RUNTIME, "node", "*", Confidence.HIGH,
                    evidence, evidence, "script-extension"));
        else if (!lines.isEmpty() && lines.getFirst().matches("^#!.*\\bnode\\b.*"))
            out.add(SkillRequirement.inferred(RequirementType.RUNTIME, "node", "*", Confidence.HIGH,
                    evidence + ":1", sanitizeMatched(lines.getFirst()), "shebang"));
    }

    private void inferCommands(List<String> lines, String evidence, List<Requirement> out) {
        if (!evidence.endsWith(".sh") && !evidence.endsWith(".bash")) return;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#") || line.contains("$(") || line.contains("`")) continue;
            Matcher matcher = SIMPLE_COMMAND.matcher(line);
            if (matcher.find()) {
                String command = matcher.group(1);
                if (!SHELL_BUILTINS.contains(command) && INFERABLE_COMMANDS.contains(command.toLowerCase())) {
                    String location = evidence + ":" + (i + 1);
                    String matched = sanitizeMatched(lines.get(i));
                    out.add(SkillRequirement.inferred(RequirementType.COMMAND, command, "present", Confidence.HIGH,
                            location, matched, "shell-command-position"));
                    String runtime = switch (command.toLowerCase()) {
                        case "python", "python3" -> "python";
                        case "node", "java" -> command.toLowerCase();
                        default -> null;
                    };
                    if (runtime != null) out.add(SkillRequirement.inferred(RequirementType.RUNTIME, runtime, "*", Confidence.HIGH,
                            location, matched, "shell-runtime-command"));
                }
            }
        }
    }

    private List<Requirement> deduplicate(List<Requirement> input) {
        Map<String, Requirement> unique = new LinkedHashMap<>();
        for (Requirement item : input) {
            String qualifier = item instanceof PackageRequirement packages ? packages.ecosystem().jsonValue()
                    : item instanceof CapabilityRequirement capability ? capability.capabilityKind().jsonValue() : "";
            String name = item instanceof CapabilityRequirement ? item.name() : item.name().toLowerCase();
            String key = item.type() + "\u0000" + qualifier + "\u0000" + name;
            Requirement old = unique.get(key);
            if (old == null || old.source() == RequirementSource.INFERRED && item.source() == RequirementSource.DECLARED) unique.put(key, item);
        }
        return List.copyOf(unique.values());
    }

    private String sanitizeMatched(String raw) {
        String assignments = SENSITIVE_LITERAL.matcher(raw.strip()).replaceAll("$1<redacted>");
        String redacted = SENSITIVE_TOKEN.matcher(assignments).replaceAll("<redacted>");
        return redacted.length() <= MAX_MATCHED_LENGTH ? redacted : redacted.substring(0, MAX_MATCHED_LENGTH - 1) + "…";
    }

    private String stringValue(Object value) { return value == null ? "" : String.valueOf(value); }
}
