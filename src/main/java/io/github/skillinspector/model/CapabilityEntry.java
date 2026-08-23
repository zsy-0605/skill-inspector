package io.github.skillinspector.model;

import java.util.List;

public record CapabilityEntry(
        CapabilityKind capabilityKind, String name, List<String> aliases,
        CapabilityAvailability availability, CapabilitySource source) {}
