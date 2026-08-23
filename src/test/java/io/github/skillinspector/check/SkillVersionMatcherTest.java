package io.github.skillinspector.check;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SkillVersionMatcherTest {
    private final SkillVersionMatcher matcher = new SkillVersionMatcher();

    @Test void supportsTheMinimalV04ConstraintSet() {
        assertThat(matcher.matches(null, "*")).isEqualTo(SkillVersionMatcher.Result.MATCH);
        assertThat(matcher.matches("1.2.0", "1.2.0")).isEqualTo(SkillVersionMatcher.Result.MATCH);
        assertThat(matcher.matches("1.2", "=1.2.0")).isEqualTo(SkillVersionMatcher.Result.MATCH);
        assertThat(matcher.matches("1.3.0", ">1.2")).isEqualTo(SkillVersionMatcher.Result.MATCH);
        assertThat(matcher.matches("1.2.0", ">=1.2")).isEqualTo(SkillVersionMatcher.Result.MATCH);
        assertThat(matcher.matches("1.9", "<2")).isEqualTo(SkillVersionMatcher.Result.MATCH);
        assertThat(matcher.matches("2", "<=2")).isEqualTo(SkillVersionMatcher.Result.MATCH);
        assertThat(matcher.matches("1.8.4", "1.x")).isEqualTo(SkillVersionMatcher.Result.MATCH);
        assertThat(matcher.matches("1.8.4", "1.*")).isEqualTo(SkillVersionMatcher.Result.MATCH);
    }

    @Test void unsupportedOrUnknownVersionsRemainUnknown() {
        assertThat(matcher.matches("1.2.0", "^1.0")).isEqualTo(SkillVersionMatcher.Result.UNKNOWN);
        assertThat(matcher.matches(null, ">=1")).isEqualTo(SkillVersionMatcher.Result.UNKNOWN);
        assertThat(matcher.matches("1.2.0-beta", ">=1")).isEqualTo(SkillVersionMatcher.Result.UNKNOWN);
    }
}
