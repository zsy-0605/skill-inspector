package io.github.skillinspector.model;

import java.util.List;

public record SkillInventoryEntry(
        SkillIdentity identity, String version, SkillAvailability availability,
        SkillInventorySource source, SkillInventoryCoverage dependencyCoverage,
        List<SkillInventoryDependency> dependencies) {
    public SkillInventoryEntry {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
