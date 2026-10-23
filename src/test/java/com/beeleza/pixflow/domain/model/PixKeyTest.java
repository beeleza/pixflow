package com.beeleza.pixflow.domain.model;

import com.beeleza.pixflow.domain.exception.InvalidPixKeyException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PixKeyTest {

    @Test
    void stripsValueBeforeValidating() {
        PixKey key = new PixKey("  fulano@email.com\n", PixKeyType.EMAIL);

        assertThat(key.value()).isEqualTo("fulano@email.com");
        assertThat(key).isEqualTo(new PixKey("fulano@email.com", PixKeyType.EMAIL));
    }

    @Test
    void rejectsNullType() {
        assertThatThrownBy(() -> new PixKey("fulano@email.com", null)).isInstanceOf(InvalidPixKeyException.class);
    }

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> new PixKey(null, PixKeyType.EMAIL)).isInstanceOf(InvalidPixKeyException.class);
    }

    @Test
    void rejectsValueThatDoesNotMatchType() {
        assertThatThrownBy(() -> new PixKey("fulano@email.com", PixKeyType.CPF))
                .isInstanceOf(InvalidPixKeyException.class)
                .hasMessageContaining("CPF");
    }

    @Test
    void sameValueWithDifferentTypeIsAnotherKey() {
        assertThat(new PixKey("12345678901", PixKeyType.CPF))
                .isNotEqualTo(new PixKey("123e4567-e89b-12d3-a456-426614174000", PixKeyType.RANDOM));
    }
}
