package io.github.skillinspector.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CapabilityKind {
    MCP_SERVER("mcpServer"), TOOL("tool"), CAPABILITY("capability");

    private final String jsonValue;

    CapabilityKind(String jsonValue) { this.jsonValue = jsonValue; }

    @JsonValue public String jsonValue() { return jsonValue; }

    @JsonCreator
    public static CapabilityKind fromJson(String value) {
        if (value == null) return null;
        return switch (value) {
            case "mcpServer" -> MCP_SERVER;
            case "tool" -> TOOL;
            case "capability" -> CAPABILITY;
            default -> throw new IllegalArgumentException("Unsupported capability kind: " + value);
        };
    }
}
