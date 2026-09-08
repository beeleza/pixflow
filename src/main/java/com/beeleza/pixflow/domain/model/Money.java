package com.beeleza.pixflow.domain.model;

import com.beeleza.pixflow.domain.exception.InvalidMoneyException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Monetary amount in BRL, normalized to two decimal places and never negative.
 */
public record Money(BigDecimal amount) {

    public Money {
        if (amount == null) {
            throw new InvalidMoneyException("amount must not be null");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() < 0) {
            throw new InvalidMoneyException("amount must not be negative: " + amount);
        }
    }

    public static Money of(String amount) {
        try {
            return new Money(new BigDecimal(amount));
        } catch (NumberFormatException e) {
            throw new InvalidMoneyException("amount is not a valid number: " + amount, e);
        }
    }

    public static Money of(long amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public Money add(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isGreaterThan(Money other) {
        return amount.compareTo(other.amount) > 0;
    }
}
