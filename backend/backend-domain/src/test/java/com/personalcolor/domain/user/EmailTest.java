package com.personalcolor.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Email 정규화")
class EmailTest {

    @Test
    @DisplayName("대소문자와 공백을 정규화한다")
    void normalizesCaseAndWhitespace() {
        // 정규화가 없으면 Foo@Example.com과 foo@example.com이 별개 계정이 된다.
        assertThat(Email.normalize("  Foo@Example.COM  ")).isEqualTo("foo@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a@b.co", "user.name+tag@sub.example.com", "x@y.zz"})
    @DisplayName("정상 형식을 받아들인다")
    void acceptsValidShapes(String email) {
        assertThat(Email.normalize(email)).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-at-sign", "@example.com", "user@", "user@nodot", "a b@c.com"})
    @DisplayName("명백한 오타를 거부한다")
    void rejectsMalformed(String email) {
        assertThatThrownBy(() -> Email.normalize(email))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("빈 값을 거부한다")
    void rejectsBlank() {
        assertThatThrownBy(() -> Email.normalize("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비어");
    }

    @Test
    @DisplayName("과도하게 긴 주소를 거부한다")
    void rejectsTooLong() {
        String long_ = "a".repeat(250) + "@example.com";

        assertThatThrownBy(() -> Email.normalize(long_))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("깁니다");
    }
}
