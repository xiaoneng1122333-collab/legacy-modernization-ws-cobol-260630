package com.practicebank.common.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** JPY 金額を表す値オブジェクト。COBOL の PIC 9(15) に対応。 */
public record Money(BigDecimal amount) {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.scale() > 0) {
            throw new IllegalArgumentException("JPY must have zero decimal places: " + amount);
        }
    }

    public static Money of(long amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(this.amount.subtract(other.amount));
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public long toLong() {
        return amount.longValueExact();
    }
}
