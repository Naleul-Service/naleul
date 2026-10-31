package com.naleul.naleul.domain.purchase.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VerifyPurchaseRequest {

    @NotBlank(message = "signedTransaction은 필수입니다.")
    private String signedTransaction;
}