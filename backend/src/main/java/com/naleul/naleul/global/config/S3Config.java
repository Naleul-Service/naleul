package com.naleul.naleul.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3 클라이언트.
 * 2026-10: 접근 키를 yml 에 두지 않고 EC2 IAM 역할로 접속한다 (키 노출 → 키 없이 운영).
 *  - spring.cloud.aws.credentials.access-key / secret-key 가 있으면 그 키로 (로컬 개발 등)
 *  - 없으면 기본 자격 증명 체인 → EC2 에서는 인스턴스에 붙인 IAM 역할 (naleul-ec2-role)
 */
@Configuration
public class S3Config {

    @Value("${spring.cloud.aws.credentials.access-key:}")
    private String accessKey;

    @Value("${spring.cloud.aws.credentials.secret-key:}")
    private String secretKey;

    @Value("${spring.cloud.aws.region.static}")
    private String region;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials())
                .build();
    }

    private AwsCredentialsProvider credentials() {
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        return DefaultCredentialsProvider.create();
    }
}
