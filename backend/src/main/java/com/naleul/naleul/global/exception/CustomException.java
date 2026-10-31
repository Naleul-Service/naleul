package com.naleul.naleul.global.exception;

import com.naleul.naleul.global.common.response.ErrorCode;
import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {
    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    // GlobalExceptionHandler에서 .getStatus() 쓰려면 이게 있어야 함
    public org.springframework.http.HttpStatus getStatus() {
        return errorCode.getStatus();
    }
}