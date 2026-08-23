package io.github.skillinspector.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RequirementType {
    RUNTIME("runtime"), COMMAND("command"), ENVIRONMENT_VARIABLE("environmentVariable"),
    FILE("file"), DIRECTORY("directory"), OPERATING_SYSTEM("operatingSystem"), PACKAGE("package"),
    CAPABILITY("capability");
    private final String jsonValue;
    RequirementType(String jsonValue) { this.jsonValue = jsonValue; }
    @JsonValue public String jsonValue() { return jsonValue; }
}
