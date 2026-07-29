package com.musinsa.payments.point.support.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_EARN_AMOUNT(HttpStatus.BAD_REQUEST, "1회 적립 가능 금액 범위를 벗어났습니다."),
    INVALID_EXPIRE_DAYS(HttpStatus.BAD_REQUEST, "허용된 만료일 범위를 벗어났습니다."),
    MAX_BALANCE_EXCEEDED(HttpStatus.BAD_REQUEST, "개인별 최대 보유 포인트를 초과했습니다."),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "사용 가능한 포인트가 부족합니다."),

    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "포인트 거래를 찾을 수 없습니다."),
    NOT_EARN_TRANSACTION(HttpStatus.BAD_REQUEST, "적립 거래가 아닙니다."),
    NOT_USE_TRANSACTION(HttpStatus.BAD_REQUEST, "사용 거래가 아닙니다."),

    EARN_ALREADY_CANCELED(HttpStatus.CONFLICT, "이미 취소된 적립입니다."),
    EARN_ALREADY_EXPIRED(HttpStatus.CONFLICT, "이미 만료된 적립은 취소할 수 없습니다."),
    EARN_PARTIALLY_USED(HttpStatus.CONFLICT, "일부가 사용된 적립은 취소할 수 없습니다."),
    USE_CANCEL_AMOUNT_EXCEEDED(HttpStatus.CONFLICT, "취소 가능한 사용 금액을 초과했습니다."),
    REQUEST_KEY_CONFLICT(HttpStatus.CONFLICT, "같은 requestKey 가 다른 종류의 요청에 이미 사용되었습니다."),

    POLICY_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "포인트 정책이 초기화되지 않았습니다."),
    INVALID_POLICY(HttpStatus.BAD_REQUEST, "포인트 정책 값이 올바르지 않습니다."),

    EARNED_POINT_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "적립분을 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "처리 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
