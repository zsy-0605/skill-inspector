package io.github.skillinspector.model;

public record CapabilityCoverageMap(
        CapabilityCoverage mcpServer, CapabilityCoverage tool, CapabilityCoverage capability) {
    public CapabilityCoverage forKind(CapabilityKind kind) {
        return switch (kind) {
            case MCP_SERVER -> mcpServer;
            case TOOL -> tool;
            case CAPABILITY -> capability;
        };
    }
}
