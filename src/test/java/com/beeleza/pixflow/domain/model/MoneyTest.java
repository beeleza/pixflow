package com.beeleza.pixflow.domain.model;

import com.beeleza.pixflow.domain.exception.InvalidMoneyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new Money(null)).isInstanceOf(InvalidMoneyException.class);
        assertThatThrownBy(() -> Money.of((String) null)).isInstanceOf(InvalidMoneyException.class);
    }

    @Test
    void rejectsNegative() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-0.01"))).isInstanceOf(InvalidMoneyException.class);
    }

    @Test
    void acceptsZero() {
        assertThat(Money.of(0).isPositive()).isFalse();
    }

    @Test
    void normalizesToTwoDecimalPlaces() {
        Money money = new Money(new BigDecimal("150.5"));

        assertThat(money.amount()).isEqualTo(new BigDecimal("150.50"));
        assertThat(money).isEqualTo(new Money(new BigDecimal("150.50")));
        assertThat(Money.of(10).amount()).isEqualTo(new BigDecimal("10.00"));
    }

    // Documents the current rule: extra decimals are rounded instead of rejected.
    @ParameterizedTest
    @CsvSource({
            "10.004, 10.00",
            "10.005, 10.01",
            "10.009, 10.01"
    })
    void roundsExtraDecimalsHalfUp(String raw, String expected) {
        assertThat(Money.of(raw).amount()).isEqualTo(new BigDecimal(expected));
    }

    @Test
    void rejectsTextThatIsNotANumber() {
        assertThatThrownBy(() -> Money.of("abc"))
                .isInstanceOf(InvalidMoneyException.class)
                .hasCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    void addsAndSubtracts() {
        assertThat(Money.of("10.50").add(Money.of("0.25"))).isEqualTo(Money.of("10.75"));
        assertThat(Money.of("10.50").subtract(Money.of("0.50"))).isEqualTo(Money.of(10));
    }

    @Test
    void refusesSubtractionBelowZero() {
        assertThatThrownBy(() -> Money.of(1).subtract(Money.of(2))).isInstanceOf(InvalidMoneyException.class);
    }

    @Test
    void compares() {
        assertThat(Money.of("0.01").isPositive()).isTrue();
        assertThat(Money.of(2).isGreaterThan(Money.of(1))).isTrue();
        assertThat(Money.of(1).isGreaterThan(Money.of(1))).isFalse();
    }
}
