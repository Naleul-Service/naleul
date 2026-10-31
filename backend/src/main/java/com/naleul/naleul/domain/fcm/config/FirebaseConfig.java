package com.naleul.naleul.domain.fcm.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.credentials.path:}")
    private String externalCredentialsPath;

    private static final List<String> FIREBASE_SCOPES = List.of(
            "https://www.googleapis.com/auth/firebase.messaging",
            "https://www.googleapis.com/auth/cloud-platform"
    );

    private GoogleCredentials firebaseCredentials;
    private String projectId;

    @Bean
    public GoogleCredentials firebaseCredentials() {
        return firebaseCredentials;
    }

    @Bean
    public String firebaseProjectId() {
        return projectId;
    }

    @PostConstruct
    public void initFirebase() throws IOException {
        try (InputStream inputStream = resolveCredentialsStream()) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(inputStream)
                    .createScoped(FIREBASE_SCOPES);

            this.firebaseCredentials = credentials;

            if (credentials instanceof ServiceAccountCredentials) {
                this.projectId = ((ServiceAccountCredentials) credentials).getProjectId();
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(projectId)
                    .setHttpTransport(GoogleNetHttpTransport.newTrustedTransport())
                    .build();

            for (FirebaseApp app : FirebaseApp.getApps()) {
                app.delete();
            }
            FirebaseApp.initializeApp(options);

            log.info("Firebase 초기화 완료 - projectId: {}", projectId);
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("HttpTransport 생성 실패", e);
        }
    }

    private InputStream resolveCredentialsStream() throws IOException {
        if (externalCredentialsPath != null && !externalCredentialsPath.isBlank()) {
            Path path = Path.of(externalCredentialsPath);
            if (Files.exists(path)) {
                return Files.newInputStream(path);
            }
        }
        Resource resource = new ClassPathResource("firebase-service-account.json");
        return resource.getInputStream();
    }
}