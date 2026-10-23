package com.beeleza.pixflow.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class PixKeyTypeTest {

    @ParameterizedTest
    @CsvSource({
            "CPF,    12345678901",
            "CNPJ,   12345678000190",
            "EMAIL,  fulano@email.com",
            "EMAIL,  fulano.silva+pix@email.com.br",
            "PHONE,  +5511987654321",
            "PHONE,  +551133334444",
            "RANDOM, 123e4567-e89b-12d3-a456-426614174000",
            "RANDOM, 123E4567-E89B-12D3-A456-426614174000"
    })
    void acceptsWellFormedValues(PixKeyType type, String value) {
        assertThat(type.matches(value)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "CPF,    1234567890",
            "CPF,    123456789012",
            "CPF,    123.456.789-01",
            "CNPJ,   1234567800019",
            "CNPJ,   12.345.678/0001-90",
            "EMAIL,  fulano.email.com",
            "EMAIL,  fulano@email",
            "EMAIL,  a@b.c",
            "EMAIL,  ful ano@email.com",
            "PHONE,  11987654321",
            "PHONE,  +55119876543210",
            "PHONE,  +1415555012",
            "RANDOM, 123e4567e89b12d3a456426614174000",
            "RANDOM, 123e4567-e89b-12d3-a456-42661417400g"
    })
    void rejectsMalformedValues(PixKeyType type, String value) {
        assertThat(type.matches(value)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(PixKeyType.class)
    void rejectsNull(PixKeyType type) {
        assertThat(type.matches(null)).isFalse();
    }

    @Test
    void doesNotMatchAnotherTypesFormat() {
        assertThat(PixKeyType.CPF.matches("12345678000190")).isFalse();
        assertThat(PixKeyType.CNPJ.matches("12345678901")).isFalse();
    }
}
