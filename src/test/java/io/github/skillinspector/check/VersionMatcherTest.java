package io.github.skillinspector.check;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class VersionMatcherTest {
    private final VersionMatcher matcher = new VersionMatcher();

    @ParameterizedTest
    @CsvSource({
            ">=21,21,true", ">=21,17,false", ">21,21,false", ">21,22,true",
            "<21,17.0.9,true", "<=21,20.9,true", "=21,21.0.0,true",
            "21.x,21.9.1,true", "21.x,22.0,false", ">=3.11,3.12.2,true"
    })
    void matchesConstraints(String required, String actual, boolean expected) {
        assertThat(matcher.matches(required, actual)).isEqualTo(expected);
    }
}
