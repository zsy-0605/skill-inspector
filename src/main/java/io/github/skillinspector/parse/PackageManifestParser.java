package io.github.skillinspector.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.skillinspector.model.*;
import org.w3c.dom.*;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PackageManifestParser {
    private static final long MAX_MANIFEST_BYTES = 1024 * 1024;
    private static final int MAX_REQUIREMENTS = 10_000;
    private static final Pattern PYTHON_SPEC = Pattern.compile("^([A-Za-z0-9][A-Za-z0-9._-]*)(?:\\[[^]]+])?\\s*(.*)$");
    private static final Pattern TOML_STRING = Pattern.compile("[\"']([^\"']+)[\"']");
    private final ObjectMapper json = new ObjectMapper();

    List<PackageRequirement> parse(Path root) throws IOException {
        List<PackageRequirement> requirements = new ArrayList<>();
        parseRequirements(root.resolve("requirements.txt"), requirements);
        parsePyproject(root.resolve("pyproject.toml"), requirements);
        parsePackageJson(root.resolve("package.json"), requirements);
        parsePom(root.resolve("pom.xml"), requirements);
        if (requirements.size() > MAX_REQUIREMENTS)
            throw new SkillParseException("Package manifests exceed " + MAX_REQUIREMENTS + " dependencies");
        return List.copyOf(requirements);
    }

    private void parseRequirements(Path path, List<PackageRequirement> out) throws IOException {
        if (!safeManifest(path)) return;
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i).strip();
            if (raw.isEmpty() || raw.startsWith("#") || raw.startsWith("-")) continue;
            int comment = raw.indexOf(" #");
            if (comment >= 0) raw = raw.substring(0, comment).strip();
            addPythonSpec(raw, RequirementNecessity.REQUIRED, "requirements.txt:" + (i + 1), out);
        }
    }

    private void parsePyproject(Path path, List<PackageRequirement> out) throws IOException {
        if (!safeManifest(path)) return;
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        String section = "";
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i).strip();
            if (raw.startsWith("[") && raw.endsWith("]")) {
                section = raw.substring(1, raw.length() - 1).strip();
                continue;
            }
            if (section.equals("project") && raw.matches("dependencies\\s*=.*")) {
                StringBuilder value = new StringBuilder(raw.substring(raw.indexOf('=') + 1));
                int start = i;
                while (value.indexOf("]") < 0 && ++i < lines.size()) value.append('\n').append(lines.get(i));
                if (value.indexOf("]") < 0) throw new SkillParseException("Unclosed project.dependencies array in pyproject.toml");
                for (String spec : tomlStrings(value.toString()))
                    addPythonSpec(spec, RequirementNecessity.REQUIRED, "pyproject.toml:" + (start + 1), out);
            } else if (section.equals("project.optional-dependencies") && raw.contains("=")) {
                StringBuilder value = new StringBuilder(raw.substring(raw.indexOf('=') + 1));
                int start = i;
                while (value.indexOf("]") < 0 && ++i < lines.size()) value.append('\n').append(lines.get(i));
                if (value.indexOf("]") < 0) throw new SkillParseException("Unclosed project.optional-dependencies array in pyproject.toml");
                for (String spec : tomlStrings(value.toString()))
                    addPythonSpec(spec, RequirementNecessity.OPTIONAL, "pyproject.toml:" + (start + 1), out);
            } else if (section.equals("tool.poetry.dependencies") || section.matches("tool\\.poetry\\.group\\..+\\.dependencies")) {
                parsePoetryLine(raw, section.equals("tool.poetry.dependencies") ? RequirementNecessity.REQUIRED
                        : RequirementNecessity.CONDITIONAL, "pyproject.toml:" + (i + 1), out);
            }
        }
    }

    private void parsePoetryLine(String raw, RequirementNecessity necessity, String evidence,
                                 List<PackageRequirement> out) {
        if (raw.isEmpty() || raw.startsWith("#") || !raw.contains("=")) return;
        String name = unquote(raw.substring(0, raw.indexOf('=')).strip());
        if (name.equalsIgnoreCase("python") || name.isBlank()) return;
        String value = raw.substring(raw.indexOf('=') + 1).strip();
        String required = "*";
        Matcher string = TOML_STRING.matcher(value);
        if (value.startsWith("{") && string.find()) required = string.group(1);
        else if (string.matches()) required = string.group(1);
        RequirementNecessity effective = value.matches(".*\\boptional\\s*=\\s*true\\b.*")
                ? RequirementNecessity.OPTIONAL : necessity;
        out.add(PackageRequirement.declared(PackageEcosystem.PYTHON, name, poetryConstraint(required), effective, evidence));
    }

    private void parsePackageJson(Path path, List<PackageRequirement> out) throws IOException {
        if (!safeManifest(path)) return;
        JsonNode document = json.readTree(path.toFile());
        addNpmMap(document.get("dependencies"), RequirementNecessity.REQUIRED, "package.json#/dependencies", out);
        addNpmMap(document.get("optionalDependencies"), RequirementNecessity.OPTIONAL, "package.json#/optionalDependencies", out);
        addNpmMap(document.get("peerDependencies"), RequirementNecessity.CONDITIONAL, "package.json#/peerDependencies", out);
        addNpmMap(document.get("devDependencies"), RequirementNecessity.CONDITIONAL, "package.json#/devDependencies", out);
    }

    private void addNpmMap(JsonNode node, RequirementNecessity necessity, String evidence,
                           List<PackageRequirement> out) {
        if (node == null || !node.isObject()) return;
        node.properties().forEach(entry -> out.add(PackageRequirement.declared(PackageEcosystem.NPM,
                entry.getKey(), entry.getValue().isTextual() ? entry.getValue().asText() : "*", necessity,
                evidence + "/" + entry.getKey().replace("~", "~0").replace("/", "~1"))));
    }

    private void parsePom(Path path, List<PackageRequirement> out) throws IOException {
        if (!safeManifest(path)) return;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            Element project = builder.parse(path.toFile()).getDocumentElement();
            Map<String, String> properties = pomProperties(project);
            for (Element dependencies : directChildren(project, "dependencies"))
                addMavenDependencies(dependencies, RequirementNecessity.REQUIRED, properties, out);
            for (Element profiles : directChildren(project, "profiles"))
                for (Element profile : directChildren(profiles, "profile"))
                    for (Element dependencies : directChildren(profile, "dependencies"))
                        addMavenDependencies(dependencies, RequirementNecessity.CONDITIONAL, properties, out);
        } catch (ParserConfigurationException | SAXException error) {
            throw new SkillParseException("Cannot safely parse pom.xml: " + error.getMessage(), error);
        }
    }

    private void addMavenDependencies(Element dependencies, RequirementNecessity defaultNecessity,
                                      Map<String, String> properties, List<PackageRequirement> out) {
        for (Element dependency : directChildren(dependencies, "dependency")) {
            String group = childText(dependency, "groupId");
            String artifact = childText(dependency, "artifactId");
            if (group.isBlank() || artifact.isBlank()) continue;
            String version = resolveProperty(childText(dependency, "version"), properties);
            String scope = childText(dependency, "scope");
            boolean optional = Boolean.parseBoolean(childText(dependency, "optional"));
            RequirementNecessity necessity = optional ? RequirementNecessity.OPTIONAL
                    : Set.of("test", "provided", "system").contains(scope) ? RequirementNecessity.CONDITIONAL : defaultNecessity;
            out.add(PackageRequirement.declared(PackageEcosystem.MAVEN, group + ":" + artifact,
                    version.isBlank() ? "*" : version, necessity, "pom.xml#/project/dependencies"));
        }
    }

    private Map<String, String> pomProperties(Element project) {
        Map<String, String> properties = new HashMap<>();
        for (Element container : directChildren(project, "properties")) {
            NodeList children = container.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof Element element) properties.put(element.getTagName(), element.getTextContent().strip());
            }
        }
        return properties;
    }

    private List<Element> directChildren(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && (element.getTagName().equals(localName)
                    || element.getLocalName() != null && element.getLocalName().equals(localName))) result.add(element);
        }
        return result;
    }

    private String childText(Element parent, String name) {
        List<Element> children = directChildren(parent, name);
        return children.isEmpty() ? "" : children.getFirst().getTextContent().strip();
    }

    private String resolveProperty(String value, Map<String, String> properties) {
        Matcher matcher = Pattern.compile("^\\$\\{([^}]+)}$").matcher(value);
        return matcher.matches() ? properties.getOrDefault(matcher.group(1), value) : value;
    }

    private void addPythonSpec(String raw, RequirementNecessity necessity, String evidence,
                               List<PackageRequirement> out) {
        String spec = raw.strip();
        int marker = spec.indexOf(';');
        if (marker >= 0) {
            spec = spec.substring(0, marker).strip();
            necessity = RequirementNecessity.CONDITIONAL;
        }
        Matcher matcher = PYTHON_SPEC.matcher(spec);
        if (!matcher.matches()) return;
        String constraint = matcher.group(2).strip();
        if (constraint.startsWith("@")) constraint = "*";
        out.add(PackageRequirement.declared(PackageEcosystem.PYTHON, matcher.group(1),
                cleanPythonConstraint(constraint), necessity, evidence));
    }

    private String cleanPythonConstraint(String value) {
        String constraint = value == null ? "" : value.strip();
        return constraint.isEmpty() ? "*" : constraint.replaceAll("\\s+", "");
    }

    private String poetryConstraint(String value) {
        String constraint = cleanPythonConstraint(value);
        if (!(constraint.startsWith("^") || constraint.startsWith("~"))) return constraint;
        String version = constraint.substring(1);
        String[] parts = version.split("\\.");
        if (!Arrays.stream(parts).allMatch(part -> part.matches("\\d+"))) return constraint;
        int index;
        if (constraint.startsWith("~")) index = parts.length > 1 ? 1 : 0;
        else {
            index = 0;
            while (index < parts.length - 1 && Integer.parseInt(parts[index]) == 0) index++;
        }
        int[] upper = Arrays.stream(parts).mapToInt(Integer::parseInt).toArray();
        upper[index]++;
        for (int i = index + 1; i < upper.length; i++) upper[i] = 0;
        String upperText = String.join(".", Arrays.stream(upper).mapToObj(String::valueOf).toList());
        return ">=" + version + ",<" + upperText;
    }

    private List<String> tomlStrings(String value) {
        List<String> result = new ArrayList<>();
        Matcher matcher = TOML_STRING.matcher(value);
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }

    private String unquote(String value) {
        return value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'")) ? value.substring(1, value.length() - 1) : value;
    }

    private boolean safeManifest(Path path) throws IOException {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)
                && Files.size(path) <= MAX_MANIFEST_BYTES;
    }
}
