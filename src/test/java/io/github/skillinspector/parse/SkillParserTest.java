package io.github.skillinspector.parse;

import io.github.skillinspector.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class SkillParserTest {
    @TempDir Path temp;

    @Test void parsesDeclaredAndInferredRequirementsWithoutExecutingScript() throws Exception {
        Files.writeString(temp.resolve("SKILL.md"), """
                ---
                name: sample
                compatibility:
                  runtimes:
                    java: ">=21"
                  env: [API_TOKEN]
                  files: [config.json]
                  directories: [data]
                  os: [linux, macos]
                ---
                # Sample
                """);
        Path scripts = Files.createDirectory(temp.resolve("scripts"));
        Files.writeString(scripts.resolve("convert.sh"), "#!/bin/sh\ntouch SHOULD_NOT_EXIST\npython analyze.py\npdftotext input.pdf output.txt\n");

        SkillDefinition parsed = new SkillParser().parse(temp);

        assertThat(parsed.requirements()).anySatisfy(r -> {
            assertThat(r.name()).isEqualTo("java"); assertThat(r.source()).isEqualTo(RequirementSource.DECLARED);
        }).anySatisfy(r -> {
            assertThat(r.name()).isEqualTo("pdftotext");
            assertThat(r.source()).isEqualTo(RequirementSource.INFERRED);
        }).anySatisfy(r -> {
            assertThat(r.name()).isEqualTo("python"); assertThat(r.type()).isEqualTo(RequirementType.RUNTIME);
        });
        assertThat(temp.resolve("SHOULD_NOT_EXIST")).doesNotExist();
    }
}
