package com.naleul.naleul.global.config;

import com.apple.itunes.storekit.client.AppStoreServerAPIClient;
import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

@Configuration
public class AppStoreConfig {

    @Value("${apple.iap.key-id}")
    private String keyId;

    @Value("${apple.iap.issuer-id}")
    private String issuerId;

    @Value("${apple.iap.bundle-id}")
    private String bundleId;

    @Value("${apple.iap.private-key-path}")
    private String privateKeyPath;

    @Value("${apple.iap.app-apple-id}")
    private Long appAppleId;

    // ── API Client: Production ──
    @Bean
    public AppStoreServerAPIClient appStoreServerAPIClientProduction() throws IOException {
        String encodedKey = Files.readString(Path.of(privateKeyPath));
        return new AppStoreServerAPIClient(encodedKey, keyId, issuerId, bundleId, Environment.PRODUCTION);
    }

    // ── API Client: Sandbox ──
    @Bean
    public AppStoreServerAPIClient appStoreServerAPIClientSandbox() throws IOException {
        String encodedKey = Files.readString(Path.of(privateKeyPath));
        return new AppStoreServerAPIClient(encodedKey, keyId, issuerId, bundleId, Environment.SANDBOX);
    }

    // ── Verifier: Production (appAppleId 필수) ──
    @Bean
    public SignedDataVerifier signedDataVerifierProduction() throws IOException {
        return new SignedDataVerifier(loadRootCAs(), bundleId, appAppleId, Environment.PRODUCTION, true);
    }

    // ── Verifier: Sandbox (appAppleId는 null이어도 됨) ──
    @Bean
    public SignedDataVerifier signedDataVerifierSandbox() throws IOException {
        return new SignedDataVerifier(loadRootCAs(), bundleId, null, Environment.SANDBOX, true);
    }

    private Set<InputStream> loadRootCAs() throws IOException {
        return Set.of(
                new ClassPathResource("certs/AppleRootCA-G3.cer").getInputStream(),
                new ClassPathResource("certs/AppleIncRootCertificate.cer").getInputStream()
        );
    }
}