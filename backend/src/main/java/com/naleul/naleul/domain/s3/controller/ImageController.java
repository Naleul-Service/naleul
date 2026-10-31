package com.naleul.naleul.domain.s3.controller;

import com.naleul.naleul.domain.s3.dto.response.ImageUploadResponse;
import com.naleul.naleul.domain.s3.service.S3Service;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final S3Service s3Service;

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<ImageUploadResponse>> uploadImage(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file) {

        String imageUrl = s3Service.uploadImage(file);
        ImageUploadResponse response = new ImageUploadResponse(imageUrl);

        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.IMAGE_UPLOADED, response));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @AuthenticationPrincipal Long userId,
            @RequestParam("url") String imageUrl) {

        s3Service.deleteImage(imageUrl);

        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.IMAGE_DELETED, null));
    }
}