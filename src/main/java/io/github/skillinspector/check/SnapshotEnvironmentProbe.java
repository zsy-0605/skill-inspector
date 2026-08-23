package io.github.skillinspector.check;

import io.github.skillinspector.model.*;

import java.nio.file.Path;
import java.util.*;

public final class SnapshotEnvironmentProbe implements EnvironmentProbe {
    private final EnvironmentProbe delegate;
    private final CapabilitySnapshot snapshot;
    private final Map<CapabilityKind, Map<String, CapabilityEntry>> index;

    public SnapshotEnvironmentProbe(EnvironmentProbe delegate, CapabilitySnapshot snapshot) {
        this.delegate = Objects.requireNonNull(delegate);
        this.snapshot = Objects.requireNonNull(snapshot);
        this.index = index(snapshot);
    }

    private Map<CapabilityKind, Map<String, CapabilityEntry>> index(CapabilitySnapshot value) {
        Map<CapabilityKind, Map<String, CapabilityEntry>> result = new EnumMap<>(CapabilityKind.class);
        for (CapabilityEntry entry : value.capabilities()) {
            Map<String, CapabilityEntry> entries = result.computeIfAbsent(entry.capabilityKind(), ignored -> new HashMap<>());
            entries.put(entry.name(), entry);
            if (entry.aliases() != null) for (String alias : entry.aliases()) entries.put(alias, entry);
        }
        return result;
    }

    @Override public String operatingSystem() { return delegate.operatingSystem(); }
    @Override public boolean commandExists(String command) { return delegate.commandExists(command); }
    @Override public boolean environmentVariablePresent(String name) { return delegate.environmentVariablePresent(name); }
    @Override public boolean fileExists(Path path) { return delegate.fileExists(path); }
    @Override public boolean directoryExists(Path path) { return delegate.directoryExists(path); }
    @Override public Optional<String> runtimeVersion(String runtime) { return delegate.runtimeVersion(runtime); }
    @Override public PackageInstallation packageInstallation(PackageRequirement requirement, Path skillRoot) {
        return delegate.packageInstallation(requirement, skillRoot);
    }

    @Override public CapabilityMatch capability(CapabilityRequirement requirement) {
        CapabilityEntry entry = index.getOrDefault(requirement.capabilityKind(), Map.of()).get(requirement.name());
        if (entry != null) return CapabilityMatch.entry(entry);
        return snapshot.coverage().forKind(requirement.capabilityKind()) == CapabilityCoverage.COMPLETE
                ? CapabilityMatch.absent() : CapabilityMatch.partial();
    }
}
