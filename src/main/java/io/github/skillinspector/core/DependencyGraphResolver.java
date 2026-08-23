package io.github.skillinspector.core;

import io.github.skillinspector.check.SkillVersionMatcher;
import io.github.skillinspector.model.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves only the caller-provided inventory. It never scans, installs, activates, or executes a Skill. */
public final class DependencyGraphResolver {
    public static final int MAX_DEPTH = 64;
    private static final int MAX_RESOLVED_EDGES = 5_000;
    private final SkillInventory inventory;
    private final SkillVersionMatcher versions = new SkillVersionMatcher();
    private final Map<String, SkillInventoryEntry> entries;

    public DependencyGraphResolver() { this(null); }
    public DependencyGraphResolver(SkillInventory inventory) {
        this.inventory = inventory;
        Map<String, SkillInventoryEntry> index = new LinkedHashMap<>();
        if (inventory != null) for (SkillInventoryEntry entry : inventory.skills())
            index.put(entry.identity().canonicalId(), entry);
        this.entries = Map.copyOf(index);
    }

    public List<CheckResult> resolve(String rootSkill, List<SkillDependencyRequirement> roots) {
        List<CheckResult> results = new ArrayList<>();
        State state = new State();
        for (SkillDependencyRequirement requirement : roots) {
            if (state.limited) break;
            List<String> path = new ArrayList<>();
            path.add(rootSkill);
            visit(requirement, requirement.necessity(), path, 1, results, state);
        }
        return List.copyOf(results);
    }

    private void visit(SkillDependencyRequirement requirement, RequirementNecessity effective,
                       List<String> parentPath, int depth, List<CheckResult> out, State state) {
        if (++state.edges > MAX_RESOLVED_EDGES) {
            if (!state.limited) out.add(limit(requirement, parentPath, "Dependency graph exceeds 5000 resolved edges"));
            state.limited = true;
            return;
        }
        if (depth > MAX_DEPTH) {
            out.add(limit(requirement, parentPath, "Dependency graph exceeds maximum depth 64"));
            return;
        }
        String id = requirement.identity().canonicalId();
        List<String> path = new ArrayList<>(parentPath);
        path.add(id);
        if (parentPath.contains(id)) {
            CheckStatus status = effective == RequirementNecessity.REQUIRED ? CheckStatus.FAIL : CheckStatus.WARNING;
            out.add(result(requirement, effective, status, "cycle", null, depth, path,
                    SkillResolutionKind.GRAPH_CYCLE, "Dependency cycle detected: " + String.join(" -> ", path)));
            return;
        }

        SkillInventoryEntry entry = entries.get(id);
        CheckStatus status;
        String actual;
        String message;
        if (inventory == null) {
            status = CheckStatus.UNKNOWN;
            actual = "No Skill Inventory provided";
            message = "Cannot determine Skill " + id + " without a Skill Inventory";
        } else if (entry == null) {
            if (inventory.coverage() == SkillInventoryCoverage.COMPLETE) {
                status = effective == RequirementNecessity.REQUIRED ? CheckStatus.FAIL : CheckStatus.WARNING;
                actual = "Missing from COMPLETE inventory";
                message = "Skill " + id + " is absent from the COMPLETE inventory";
            } else {
                status = CheckStatus.UNKNOWN;
                actual = "Not listed in PARTIAL inventory";
                message = "Skill " + id + " is not known because the inventory is PARTIAL";
            }
        } else if (entry.availability() == SkillAvailability.CONFIGURED || entry.availability() == SkillAvailability.UNKNOWN) {
            status = CheckStatus.UNKNOWN;
            actual = entry.availability().name();
            message = "Skill " + id + " is " + entry.availability() + ", not confirmed AVAILABLE";
        } else if (entry.availability() == SkillAvailability.UNAVAILABLE) {
            status = effective == RequirementNecessity.REQUIRED ? CheckStatus.FAIL : CheckStatus.WARNING;
            actual = "UNAVAILABLE";
            message = "Skill " + id + " is explicitly UNAVAILABLE";
        } else {
            SkillVersionMatcher.Result match = versions.matches(entry.version(), requirement.requiredVersion());
            status = switch (match) {
                case MATCH -> CheckStatus.PASS;
                case MISMATCH -> effective == RequirementNecessity.REQUIRED ? CheckStatus.FAIL : CheckStatus.WARNING;
                case UNKNOWN -> CheckStatus.UNKNOWN;
            };
            actual = entry.version() == null ? "AVAILABLE; version unknown" : "AVAILABLE " + entry.version();
            message = switch (match) {
                case MATCH -> "Skill " + id + " is available and satisfies " + requirement.requiredVersion();
                case MISMATCH -> "Skill " + id + " version " + entry.version() + " does not satisfy " + requirement.requiredVersion();
                case UNKNOWN -> "Skill " + id + " version cannot be evaluated against " + requirement.requiredVersion();
            };
        }
        out.add(result(requirement, effective, status, actual, entry, depth, path,
                SkillResolutionKind.DEPENDENCY, message));

        if (entry == null || entry.availability() != SkillAvailability.AVAILABLE || status != CheckStatus.PASS) return;
        if (entry.dependencyCoverage() == SkillInventoryCoverage.PARTIAL) {
            out.add(result(requirement, effective, CheckStatus.UNKNOWN, "PARTIAL dependency inventory", entry,
                    depth, path, SkillResolutionKind.GRAPH_COVERAGE,
                    "Transitive dependencies of " + id + " are only partially known"));
        }
        for (SkillInventoryDependency child : entry.dependencies()) {
            if (state.limited) break;
            RequirementNecessity childNecessity = effective(effective, child.necessity());
            SkillDependencyRequirement next = new SkillDependencyRequirement(child.identity(), child.version(), childNecessity,
                    child.source(), child.confidence(), child.evidence(), child.matched(), child.inferenceRule());
            visit(next, childNecessity, path, depth + 1, out, state);
        }
    }

    private RequirementNecessity effective(RequirementNecessity parent, RequirementNecessity child) {
        if (parent == RequirementNecessity.REQUIRED) return child;
        if (parent == RequirementNecessity.CONDITIONAL || child == RequirementNecessity.CONDITIONAL)
            return RequirementNecessity.CONDITIONAL;
        return RequirementNecessity.OPTIONAL;
    }

    private CheckResult limit(SkillDependencyRequirement requirement, List<String> path, String message) {
        return result(requirement, requirement.necessity(), CheckStatus.UNKNOWN, "Graph limit reached", null,
                path.size(), path, SkillResolutionKind.GRAPH_LIMIT, message);
    }

    private CheckResult result(SkillDependencyRequirement requirement, RequirementNecessity necessity,
                               CheckStatus status, String actual, SkillInventoryEntry entry, int depth,
                               List<String> path, SkillResolutionKind kind, String message) {
        return new CheckResult(RequirementType.SKILL, null, null, null, null,
                requirement.identity().namespace(), entry == null ? null : entry.identity().canonicalId(),
                entry == null ? null : entry.version(), entry == null ? null : entry.source(),
                depth, String.join(" -> ", path), kind,
                requirement.requiredVersion(), requirement.name(), requirement.requiredVersion(), actual, status,
                requirement.source(), necessity, requirement.confidence(), requirement.evidence(), requirement.matched(),
                requirement.inferenceRule(), message);
    }

    private static final class State { int edges; boolean limited; }
}
