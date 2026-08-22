package io.github.skillinspector.parse;

import io.github.skillinspector.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PackageManifestParserTest {
    @TempDir Path temp;

    @Test void parsesPythonNpmAndMavenManifestsWithoutExecutingAnything() throws Exception {
        Files.writeString(temp.resolve("SKILL.md"), "---\nname: packages\n---\n");
        Files.writeString(temp.resolve("requirements.txt"), "requests>=2.31\npywin32>=306; sys_platform == 'win32'\n");
        Files.writeString(temp.resolve("pyproject.toml"), """
                [project]
                dependencies = ["pydantic>=2", "httpx~=0.27"]
                [project.optional-dependencies]
                pdf = ["pypdf>=4"]
                [tool.poetry.dependencies]
                python = "^3.11"
                rich = "^13.0"
                """
        );
        Files.writeString(temp.resolve("package.json"), """
                {"dependencies":{"commander":"^12.0.0"},"optionalDependencies":{"sharp":"~0.33.0"},
                 "devDependencies":{"vitest":"^2.0.0"}}
                """);
        Files.writeString(temp.resolve("pom.xml"), """
                <project><modelVersion>4.0.0</modelVersion><properties><jackson.version>2.21.5</jackson.version></properties>
                  <dependencies>
                    <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId><version>${jackson.version}</version></dependency>
                    <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><version>6.1.3</version><scope>test</scope></dependency>
                  </dependencies>
                </project>
                """);

        SkillDefinition skill = new SkillParser().parse(temp);

        assertThat(skill.requirements()).filteredOn(PackageRequirement.class::isInstance).hasSize(11);
        assertThat(skill.requirements()).anySatisfy(item -> {
            PackageRequirement requirement = (PackageRequirement) item;
            assertThat(requirement.ecosystem()).isEqualTo(PackageEcosystem.PYTHON);
            assertThat(requirement.name()).isEqualTo("pywin32");
            assertThat(requirement.necessity()).isEqualTo(RequirementNecessity.CONDITIONAL);
        }).anySatisfy(item -> {
            PackageRequirement requirement = (PackageRequirement) item;
            assertThat(requirement.ecosystem()).isEqualTo(PackageEcosystem.NPM);
            assertThat(requirement.name()).isEqualTo("sharp");
            assertThat(requirement.necessity()).isEqualTo(RequirementNecessity.OPTIONAL);
        }).anySatisfy(item -> {
            PackageRequirement requirement = (PackageRequirement) item;
            assertThat(requirement.ecosystem()).isEqualTo(PackageEcosystem.MAVEN);
            assertThat(requirement.name()).isEqualTo("com.fasterxml.jackson.core:jackson-databind");
            assertThat(requirement.required()).isEqualTo("2.21.5");
        });
        assertThat(skill.requirements()).filteredOn(PackageRequirement.class::isInstance)
                .map(PackageRequirement.class::cast).anySatisfy(requirement -> {
                    assertThat(requirement.name()).isEqualTo("rich");
                    assertThat(requirement.required()).isEqualTo(">=13.0,<14.0");
                });
    }

    @Test void rejectsPomDoctypeInsteadOfResolvingExternalEntities() throws Exception {
        Files.writeString(temp.resolve("SKILL.md"), "---\nname: packages\n---\n");
        Files.writeString(temp.resolve("pom.xml"), "<!DOCTYPE project SYSTEM \"file:///etc/passwd\"><project/>");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new SkillParser().parse(temp))
                .isInstanceOf(SkillParseException.class).hasMessageContaining("safely parse pom.xml");
    }

    @Test void parsesFrontmatterPackageNecessityAndMissingVersion() throws Exception {
        Files.writeString(temp.resolve("SKILL.md"), """
                ---
                name: declared-packages
                compatibility:
                  packages:
                    - ecosystem: python
                      name: pypdf
                      version: ">=5"
                      necessity: required
                    - ecosystem: npm
                      name: sharp
                      optional: true
                    - ecosystem: maven
                      name: org.example:demo
                      necessity: conditional
                ---
                """);

        var packages = new SkillParser().parse(temp).requirements().stream()
                .filter(PackageRequirement.class::isInstance).map(PackageRequirement.class::cast).toList();

        assertThat(packages).extracting(PackageRequirement::required).contains(">=5", "*");
        assertThat(packages).extracting(PackageRequirement::necessity)
                .contains(RequirementNecessity.REQUIRED, RequirementNecessity.OPTIONAL, RequirementNecessity.CONDITIONAL);
    }

    @Test void malformedPackageJsonReturnsClearParseError() throws Exception {
        Files.writeString(temp.resolve("SKILL.md"), "---\nname: packages\n---\n");
        Files.writeString(temp.resolve("package.json"), "{not-json");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new SkillParser().parse(temp))
                .isInstanceOf(SkillParseException.class).hasMessageContaining("Cannot parse");
    }

    @Test void malformedPyprojectDependencyArrayReturnsClearError() throws Exception {
        Files.writeString(temp.resolve("SKILL.md"), "---\nname: packages\n---\n");
        Files.writeString(temp.resolve("pyproject.toml"), "[project]\ndependencies = [\"pypdf>=5\"\n");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new SkillParser().parse(temp))
                .isInstanceOf(SkillParseException.class).hasMessageContaining("Unclosed project.dependencies");
    }
}
