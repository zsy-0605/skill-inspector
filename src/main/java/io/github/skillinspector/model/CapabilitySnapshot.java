package io.github.skillinspector.model;

import java.util.List;

public record CapabilitySnapshot(
        String schemaVersion, RuntimeDescriptor runtime, CapabilityCoverageMap coverage,
        List<CapabilityEntry> capabilities) {}
