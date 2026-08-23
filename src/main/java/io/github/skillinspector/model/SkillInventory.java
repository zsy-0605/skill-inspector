package io.github.skillinspector.model;

import java.util.List;

public record SkillInventory(String schemaVersion, SkillInventoryCoverage coverage,
                             List<SkillInventoryEntry> skills) {
    public SkillInventory { skills = skills == null ? List.of() : List.copyOf(skills); }
}
