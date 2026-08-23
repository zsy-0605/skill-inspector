package io.github.skillinspector.model;

public record CapabilityMatch(
        CapabilityAvailability availability, String actual, String resolvedCapability,
        CapabilitySource source) {
    public static CapabilityMatch noSnapshot() {
        return new CapabilityMatch(CapabilityAvailability.UNKNOWN, "NO SNAPSHOT", null, null);
    }
    public static CapabilityMatch partial() {
        return new CapabilityMatch(CapabilityAvailability.UNKNOWN, "NOT LISTED (PARTIAL)", null, null);
    }
    public static CapabilityMatch absent() {
        return new CapabilityMatch(CapabilityAvailability.UNAVAILABLE, "NOT LISTED (COMPLETE)", null, null);
    }
    public static CapabilityMatch entry(CapabilityEntry entry) {
        return new CapabilityMatch(entry.availability(), entry.availability().name(), entry.name(), entry.source());
    }
}
