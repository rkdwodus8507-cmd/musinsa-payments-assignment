package com.musinsa.payments.point.api.dto;

import jakarta.validation.constraints.Min;
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
public class EarnRequest {

    @NotNull
    private Long userId;

    @Positive
    private long amount;

    @Min(1)
    private Integer expireDays;

    @Size(max = 255)
    private String memo;

    @Size(max = 64)
    private String requestKey;
}
