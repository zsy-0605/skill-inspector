package io.github.skillinspector.check;

import io.github.skillinspector.model.PackageEcosystem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PackageVersionMatcherTest {
    private final PackageVersionMatcher matcher = new PackageVersionMatcher();

    @Test void supportsCommonPythonConstraints() {
        assertThat(matcher.matches(PackageEcosystem.PYTHON, ">=2.0,<3", "2.7.1")).isTrue();
        assertThat(matcher.matches(PackageEcosystem.PYTHON, "~=1.4.5", "1.4.9")).isTrue();
        assertThat(matcher.matches(PackageEcosystem.PYTHON, "~=1.4.5", "1.5.0")).isFalse();
        assertThat(matcher.matches(PackageEcosystem.PYTHON, "!=2.1", "2.1.0")).isFalse();
    }

    @Test void supportsCommonNpmRanges() {
        assertThat(matcher.matches(PackageEcosystem.NPM, "^2.3.0", "2.9.4")).isTrue();
        assertThat(matcher.matches(PackageEcosystem.NPM, "^2.3.0", "3.0.0")).isFalse();
        assertThat(matcher.matches(PackageEcosystem.NPM, ">=18 <20", "19.2.0")).isTrue();
        assertThat(matcher.matches(PackageEcosystem.NPM, "1.2.x || 2.x", "2.4.1")).isTrue();
    }

    @Test void supportsMavenIntervalsAndRejectsUnknownSyntax() {
        assertThat(matcher.matches(PackageEcosystem.MAVEN, "[2.0,3.0)", "2.21.5")).isTrue();
        assertThat(matcher.matches(PackageEcosystem.MAVEN, "[2.0,3.0)", "3.0.0")).isFalse();
        assertThatThrownBy(() -> matcher.matches(PackageEcosystem.NPM, "workspace:*", "1.0.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> matcher.matches(PackageEcosystem.NPM, "^1.0.0", "1.1.0-beta.1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
