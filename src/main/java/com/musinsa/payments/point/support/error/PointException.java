package com.musinsa.payments.point.support.error;

import lombok.Getter;

@Getter
public class PointException extends RuntimeException {

    private final ErrorCode errorCode;

    public PointException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public PointException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + " " + detail);
        this.errorCode = errorCode;
    }

    public static PointException of(ErrorCode errorCode) {
        return new PointException(errorCode);
    }

    public static PointException of(ErrorCode errorCode, String detail) {
        return new PointException(errorCode, detail);
    }
}
