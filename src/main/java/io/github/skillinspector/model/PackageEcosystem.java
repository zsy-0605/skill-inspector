package io.github.skillinspector.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum PackageEcosystem {
    PYTHON("python"), NPM("npm"), MAVEN("maven");

    private final String jsonValue;

    PackageEcosystem(String jsonValue) { this.jsonValue = jsonValue; }

    @JsonValue public String jsonValue() { return jsonValue; }

    @JsonCreator
    public static PackageEcosystem fromJson(String value) {
        if (value == null) return null;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "python", "pypi", "pip" -> PYTHON;
            case "npm", "node" -> NPM;
            case "maven", "mvn" -> MAVEN;
            default -> throw new IllegalArgumentException("Unsupported package ecosystem: " + value);
        };
    }
}
