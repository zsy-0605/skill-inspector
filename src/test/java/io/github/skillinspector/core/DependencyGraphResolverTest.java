package io.github.skillinspector.core;

import io.github.skillinspector.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyGraphResolverTest {
    @Test void resolvesTransitiveDependencyAndExactPath() {
        SkillInventory inventory = inventory(SkillInventoryCoverage.COMPLETE,
                entry("a", "1.0", SkillInventoryCoverage.COMPLETE, dep("b", ">=2", RequirementNecessity.REQUIRED)),
                entry("b", "2.1", SkillInventoryCoverage.COMPLETE));
        var results = new DependencyGraphResolver(inventory).resolve("root", List.of(root("a", "*", RequirementNecessity.REQUIRED)));
        assertThat(results).hasSize(2).allMatch(item -> item.status() == CheckStatus.PASS);
        assertThat(results.get(1).dependencyPath()).isEqualTo("root -> a -> b");
        assertThat(results.get(1).dependencyDepth()).isEqualTo(2);
    }

    @Test void completeAndPartialMissingHaveDifferentMeaning() {
        var required = List.of(root("missing", "*", RequirementNecessity.REQUIRED));
        assertThat(new DependencyGraphResolver(inventory(SkillInventoryCoverage.COMPLETE)).resolve("root", required).getFirst().status())
                .isEqualTo(CheckStatus.FAIL);
        assertThat(new DependencyGraphResolver(inventory(SkillInventoryCoverage.PARTIAL)).resolve("root", required).getFirst().status())
                .isEqualTo(CheckStatus.UNKNOWN);
        assertThat(new DependencyGraphResolver().resolve("root", required).getFirst().status()).isEqualTo(CheckStatus.UNKNOWN);
    }

    @Test void propagatesNecessityAndClassifiesCycles() {
        SkillInventory inventory = inventory(SkillInventoryCoverage.COMPLETE,
                entry("a", "1", SkillInventoryCoverage.COMPLETE, dep("b", "*", RequirementNecessity.REQUIRED)),
                entry("b", "1", SkillInventoryCoverage.COMPLETE, dep("a", "*", RequirementNecessity.REQUIRED)));
        var required = new DependencyGraphResolver(inventory).resolve("root", List.of(root("a", "*", RequirementNecessity.REQUIRED)));
        assertThat(required).anySatisfy(item -> {
            assertThat(item.resolutionKind()).isEqualTo(SkillResolutionKind.GRAPH_CYCLE);
            assertThat(item.status()).isEqualTo(CheckStatus.FAIL);
            assertThat(item.dependencyPath()).isEqualTo("root -> a -> b -> a");
        });
        var optional = new DependencyGraphResolver(inventory).resolve("root", List.of(root("a", "*", RequirementNecessity.OPTIONAL)));
        assertThat(optional).anySatisfy(item -> {
            assertThat(item.resolutionKind()).isEqualTo(SkillResolutionKind.GRAPH_CYCLE);
            assertThat(item.status()).isEqualTo(CheckStatus.WARNING);
        });
    }

    @Test void partialChildGraphPreventsReadyAndVersionMismatchBlocks() {
        SkillInventory inventory = inventory(SkillInventoryCoverage.COMPLETE,
                entry("a", "1.0", SkillInventoryCoverage.PARTIAL));
        var coverage = new DependencyGraphResolver(inventory).resolve("root", List.of(root("a", "*", RequirementNecessity.REQUIRED)));
        assertThat(coverage).anyMatch(item -> item.resolutionKind() == SkillResolutionKind.GRAPH_COVERAGE
                && item.status() == CheckStatus.UNKNOWN);
        var mismatch = new DependencyGraphResolver(inventory).resolve("root", List.of(root("a", ">=2", RequirementNecessity.REQUIRED)));
        assertThat(mismatch.getFirst().status()).isEqualTo(CheckStatus.FAIL);
    }

    @Test void configuredUnknownAndUnsupportedConstraintsStayUnknown() {
        SkillInventory configured = new SkillInventory("1.0", SkillInventoryCoverage.COMPLETE, List.of(
                new SkillInventoryEntry(new SkillIdentity(null, "child"), "1.0", SkillAvailability.CONFIGURED,
                        SkillInventorySource.STATIC_CONFIGURATION, SkillInventoryCoverage.COMPLETE, List.of())));
        assertThat(new DependencyGraphResolver(configured).resolve("root", List.of(root("child", "*", RequirementNecessity.REQUIRED))).getFirst().status())
                .isEqualTo(CheckStatus.UNKNOWN);
        SkillInventory available = inventory(SkillInventoryCoverage.COMPLETE, entry("child", "1.0", SkillInventoryCoverage.COMPLETE));
        assertThat(new DependencyGraphResolver(available).resolve("root", List.of(root("child", "^1", RequirementNecessity.REQUIRED))).getFirst().status())
                .isEqualTo(CheckStatus.UNKNOWN);
    }

    @Test void conditionalAncestorDowngradesTransitiveFailure() {
        SkillInventory inventory = inventory(SkillInventoryCoverage.COMPLETE,
                entry("parent", "1", SkillInventoryCoverage.COMPLETE,
                        dep("absent", "*", RequirementNecessity.REQUIRED)));
        var results = new DependencyGraphResolver(inventory).resolve("root",
                List.of(root("parent", "*", RequirementNecessity.CONDITIONAL)));
        assertThat(results.get(1).status()).isEqualTo(CheckStatus.WARNING);
        assertThat(results.get(1).necessity()).isEqualTo(RequirementNecessity.CONDITIONAL);
    }

    @Test void depthLimitIsUnknownInsteadOfReady() {
        List<SkillInventoryEntry> entries = new ArrayList<>();
        for (int i = 1; i <= 65; i++) {
            SkillInventoryDependency[] dependencies = i == 65 ? new SkillInventoryDependency[0]
                    : new SkillInventoryDependency[]{dep("node-" + (i + 1), "*", RequirementNecessity.REQUIRED)};
            entries.add(entry("node-" + i, "1", SkillInventoryCoverage.COMPLETE, dependencies));
        }
        var results = new DependencyGraphResolver(new SkillInventory("1.0", SkillInventoryCoverage.COMPLETE, entries))
                .resolve("root", List.of(root("node-1", "*", RequirementNecessity.REQUIRED)));
        assertThat(results).anyMatch(item -> item.resolutionKind() == SkillResolutionKind.GRAPH_LIMIT
                && item.status() == CheckStatus.UNKNOWN);
    }

    private SkillInventory inventory(SkillInventoryCoverage coverage, SkillInventoryEntry... entries) {
        return new SkillInventory("1.0", coverage, List.of(entries));
    }
    private SkillInventoryEntry entry(String name, String version, SkillInventoryCoverage coverage, SkillInventoryDependency... deps) {
        return new SkillInventoryEntry(new SkillIdentity(null, name), version, SkillAvailability.AVAILABLE,
                SkillInventorySource.RUNTIME_INVENTORY, coverage, List.of(deps));
    }
    private SkillInventoryDependency dep(String name, String version, RequirementNecessity necessity) {
        return new SkillInventoryDependency(new SkillIdentity(null, name), version, necessity,
                RequirementSource.DECLARED, null, "inventory", null, null);
    }
    private SkillDependencyRequirement root(String name, String version, RequirementNecessity necessity) {
        return SkillDependencyRequirement.declared(new SkillIdentity(null, name), version, necessity, "frontmatter");
    }
}
