package com.naleul.naleul.global.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL) // null인 필드는 JSON에서 제외
public class ApiResponse<T> {

    private final boolean success;
    private final HttpStatus status;
    private final String message;
    private final T data;

    @Builder
    private ApiResponse(boolean success, HttpStatus status, String message, T data) {
        this.success = success;
        this.status = status;
        this.message = message;
        this.data = data;
    }

    // ✅ 성공 응답 (data 있음)
    public static <T> ApiResponse<T> success(SuccessCode successCode, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .status(successCode.getStatus())
                .message(successCode.getMessage())
                .data(data)
                .build();
    }

    // ✅ 성공 응답 (data 없음 - 삭제 같은 경우)
    public static <T> ApiResponse<T> success(SuccessCode successCode) {
        return ApiResponse.<T>builder()
                .success(true)
                .status(successCode.getStatus())
                .message(successCode.getMessage())
                .build();
    }

    // ❌ 실패 응답
    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .status(errorCode.getStatus())
                .message(errorCode.getMessage())
                .build();
    }

    // 메시지를 직접 덮어쓰는 버전
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .status(errorCode.getStatus())
                .message(message)  // ErrorCode 메시지 대신 직접 받은 메시지 사용
                .build();
    }

    /**
     * ✅ 신규: 실패 응답 + 상세 데이터.
     * 예) 목표 확정 시 검증 실패(422) → data에 { "violations": [...] }를 담아
     *     iOS가 어느 항목에 오류 표시를 할지 알 수 있게 한다.
     * (이름을 fail로 겹치지 않게 한 이유: fail(ErrorCode, String)과 헷갈리지 않도록)
     */
    public static <T> ApiResponse<T> failWithData(ErrorCode errorCode, T data) {
        return ApiResponse.<T>builder()
                .success(false)
                .status(errorCode.getStatus())
                .message(errorCode.getMessage())
                .data(data)
                .build();
    }
}
