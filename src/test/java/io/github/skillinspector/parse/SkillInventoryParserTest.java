package io.github.skillinspector.parse;

import io.github.skillinspector.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Assumptions;
import java.io.IOException;

class SkillInventoryParserTest {
    @TempDir Path temp;

    @Test void parsesInventoryAndDependencies() throws Exception {
        Path file = temp.resolve("skills.json");
        Files.writeString(file, """
                {"schemaVersion":"1.0","coverage":"COMPLETE","skills":[{
                  "identity":{"namespace":"acme","name":"parent"},"version":"1.2.0",
                  "availability":"AVAILABLE","source":"RUNTIME_INVENTORY","dependencyCoverage":"COMPLETE",
                  "dependencies":[{"identity":{"name":"child"},"version":">=1","necessity":"REQUIRED",
                    "source":"INFERRED","confidence":"HIGH","evidence":"parent/SKILL.md",
                    "matched":"API_TOKEN=example-sensitive-value use child"}]
                }]}
                """);
        SkillInventory inventory = new SkillInventoryParser().parse(file);
        assertThat(inventory.coverage()).isEqualTo(SkillInventoryCoverage.COMPLETE);
        assertThat(inventory.skills().getFirst().identity().canonicalId()).isEqualTo("acme/parent");
        assertThat(inventory.skills().getFirst().dependencies().getFirst().identity().canonicalId()).isEqualTo("child");
        assertThat(inventory.skills().getFirst().dependencies().getFirst().matched()).contains("<redacted>")
                .doesNotContain("example-sensitive-value");
    }

    @Test void rejectsDuplicateIdentityAndUntrustedLocalAvailability() throws Exception {
        Path duplicate = temp.resolve("duplicate.json");
        Files.writeString(duplicate, """
                {"schemaVersion":"1.0","coverage":"COMPLETE","skills":[
                 {"identity":{"name":"same"},"availability":"UNKNOWN","source":"USER_PROVIDED","dependencyCoverage":"COMPLETE"},
                 {"identity":{"name":"same"},"availability":"UNKNOWN","source":"USER_PROVIDED","dependencyCoverage":"COMPLETE"}]}
                """);
        assertThatThrownBy(() -> new SkillInventoryParser().parse(duplicate)).hasMessageContaining("Duplicate Skill identity");

        Path local = temp.resolve("local.json");
        Files.writeString(local, """
                {"schemaVersion":"1.0","coverage":"COMPLETE","skills":[
                 {"identity":{"name":"local"},"availability":"AVAILABLE","source":"LOCAL_SKILL_DIRECTORY","dependencyCoverage":"COMPLETE"}]}
                """);
        assertThatThrownBy(() -> new SkillInventoryParser().parse(local)).hasMessageContaining("cannot prove AVAILABLE");
    }

    @Test void rejectsExecutionConfigurationAndSymbolicLinkInputs() throws Exception {
        Path unsafe = temp.resolve("unsafe.json");
        Files.writeString(unsafe, """
                {"schemaVersion":"1.0","coverage":"COMPLETE","skills":[{
                  "identity":{"name":"child"},"availability":"CONFIGURED","source":"STATIC_CONFIGURATION",
                  "dependencyCoverage":"COMPLETE","endpoint":"https://example.test","dependencies":[]}]}
                """);
        assertThatThrownBy(() -> new SkillInventoryParser().parse(unsafe)).hasMessageContaining("Unrecognized field");

        Path link = temp.resolve("linked.json");
        try { Files.createSymbolicLink(link, unsafe); }
        catch (UnsupportedOperationException | IOException | SecurityException error) {
            Assumptions.abort("Symbolic links unavailable");
        }
        assertThatThrownBy(() -> new SkillInventoryParser().parse(link)).hasMessageContaining("non-symbolic-link");
    }
}
