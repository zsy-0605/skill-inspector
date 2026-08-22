package io.github.skillinspector.model;

import java.nio.file.Path;
import java.util.List;

public record SkillDefinition(String name, Path root, List<Requirement> requirements) {}
