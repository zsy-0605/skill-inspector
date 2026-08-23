package io.github.skillinspector.model;

import java.util.regex.Pattern;

public record SkillIdentity(String namespace, String name) {
    private static final Pattern PART = Pattern.compile("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?");
    private static final int MAX_PART_LENGTH = 500;

    public SkillIdentity {
        if (namespace != null && namespace.isBlank())
            throw new IllegalArgumentException("Skill namespace must not be blank");
        if (name == null || name.length() > MAX_PART_LENGTH || !PART.matcher(name).matches())
            throw new IllegalArgumentException("Invalid Skill name: " + name);
        if (namespace != null && (namespace.length() > MAX_PART_LENGTH || !PART.matcher(namespace).matches()))
            throw new IllegalArgumentException("Invalid Skill namespace: " + namespace);
    }

    public String canonicalId() { return namespace == null ? name : namespace + "/" + name; }
}
