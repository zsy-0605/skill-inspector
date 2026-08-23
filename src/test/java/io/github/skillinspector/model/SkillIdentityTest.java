package io.github.skillinspector.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillIdentityTest {
    @Test void namespaceIsPartOfExactIdentity() {
        assertThat(new SkillIdentity("acme", "reader").canonicalId()).isEqualTo("acme/reader");
        assertThat(new SkillIdentity(null, "reader")).isNotEqualTo(new SkillIdentity("acme", "reader"));
    }

    @Test void rejectsNonCanonicalNames() {
        assertThatThrownBy(() -> new SkillIdentity(null, "Reader")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SkillIdentity("acme", "bad/name")).isInstanceOf(IllegalArgumentException.class);
    }
}
