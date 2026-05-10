package com.murdermystery.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureRandomInviteCodeGeneratorTest {

    private final SecureRandomInviteCodeGenerator generator = new SecureRandomInviteCodeGenerator();

    @Test
    void next_returnsExactlySixDigits() {
        String code = generator.next();
        assertThat(code).hasSize(6).matches("\\d{6}");
    }

    @Test
    void next_padsLeadingZeros_acrossManyIterations() {
        // %06d contract: numbers below 100000 must be left-padded with zeros so DB CHECK
        // (^[0-9]{6}$) accepts every code. 5000 iterations exercises the small-number branch
        // with > 99.99% probability (P(no n<100000) ≈ 0.9^5000 ≈ 0).
        for (int i = 0; i < 5000; i++) {
            String code = generator.next();
            assertThat(code)
                .as("iteration %d produced non-conforming code: %s", i, code)
                .hasSize(6)
                .matches("\\d{6}");
        }
    }
}
