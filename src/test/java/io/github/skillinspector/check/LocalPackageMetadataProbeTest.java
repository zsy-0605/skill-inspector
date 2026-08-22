package io.github.skillinspector.check;

import io.github.skillinspector.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalPackageMetadataProbeTest {
    @TempDir Path temp;

    @Test void readsPythonDistributionMetadataWithoutImportingPackage() throws Exception {
        Path site = Files.createDirectories(temp.resolve(".venv/lib/python3.12/site-packages/demo_pkg-2.4.1.dist-info"));
        Files.writeString(site.resolve("METADATA"), "Metadata-Version: 2.1\nName: demo-pkg\nVersion: 2.4.1\n\n");
        Files.writeString(temp.resolve("SHOULD_NOT_EXECUTE.py"), "raise RuntimeError('must not run')\n");
        SystemEnvironmentProbe probe = new SystemEnvironmentProbe(Map.of("PATH", "/usr/bin:/bin"), "Linux");
        PackageRequirement requirement = PackageRequirement.declared(PackageEcosystem.PYTHON, "demo_pkg", ">=2.0",
                RequirementNecessity.REQUIRED, "requirements.txt:1");

        PackageInstallation installation = probe.packageInstallation(requirement, temp);

        assertThat(installation.state()).isEqualTo(PackageInstallation.State.FOUND);
        assertThat(installation.version()).isEqualTo("2.4.1");
    }

    @Test void readsLocalNpmAndMavenMetadata() throws Exception {
        Path npm = Files.createDirectories(temp.resolve("node_modules/@scope/demo"));
        Files.writeString(npm.resolve("package.json"), "{\"name\":\"@scope/demo\",\"version\":\"3.2.1\",\"scripts\":{\"postinstall\":\"touch SHOULD_NOT_EXIST\"}}");
        Path repository = temp.resolve("m2");
        Path maven = Files.createDirectories(repository.resolve("org/example/demo/1.5.0"));
        Files.writeString(maven.resolve("demo-1.5.0.pom"), "<project/>");
        SystemEnvironmentProbe probe = new SystemEnvironmentProbe(Map.of("PATH", "/usr/bin:/bin", "MAVEN_REPO_LOCAL", repository.toString()), "Linux");

        PackageInstallation npmResult = probe.packageInstallation(PackageRequirement.declared(PackageEcosystem.NPM,
                "@scope/demo", "^3.0.0", RequirementNecessity.REQUIRED, "package.json"), temp);
        PackageInstallation mavenResult = probe.packageInstallation(PackageRequirement.declared(PackageEcosystem.MAVEN,
                "org.example:demo", "[1.0,2.0)", RequirementNecessity.REQUIRED, "pom.xml"), temp);

        assertThat(npmResult.version()).isEqualTo("3.2.1");
        assertThat(mavenResult.version()).isEqualTo("1.5.0");
        assertThat(temp.resolve("SHOULD_NOT_EXIST")).doesNotExist();
    }
}
