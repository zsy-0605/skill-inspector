package io.github.skillinspector.model;

public record PackageInstallation(State state, String version) {
    public enum State { FOUND, NOT_FOUND, UNKNOWN }

    public static PackageInstallation found(String version) { return new PackageInstallation(State.FOUND, version); }
    public static PackageInstallation notFound() { return new PackageInstallation(State.NOT_FOUND, null); }
    public static PackageInstallation unknown() { return new PackageInstallation(State.UNKNOWN, null); }
}
