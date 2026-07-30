package com.musinsa.payments.point.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UseRequest {

    @NotNull
    private Long userId;

    @NotBlank @Size(max = 64)
    private String orderId;

    @Positive
    private long amount;

    @Size(max = 64)
    private String requestKey;
}
