package com.musinsa.payments.point.support;

import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class PointAssertions {

    public static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private PointAssertions() {
    }
}
