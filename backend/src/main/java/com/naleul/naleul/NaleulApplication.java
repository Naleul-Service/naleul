package com.naleul.naleul;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling  // ✅ 추가
public class NaleulApplication {
	public static void main(String[] args) {
		// ✅ 추가 — Spring Boot 실행형 jar는 BOOT-INF/lib 안에 jar가 또 들어있는
		//    nested jar 구조입니다. JDK는 기본적으로(useCaches=true) 한 번 연 jar 파일을
		//    캐싱해서 재사용하는데, 이 구조에서 캐시된 핸들이 다른 요청/스레드에 의해
		//    닫히거나 무효화된 상태로 재사용되면, JJWT처럼 ServiceLoader(SPI)로
		//    META-INF/services 설정 파일을 읽는 라이브러리가
		//    "ServiceConfigurationError: ... Error accessing configuration file"를 던지게 됩니다.
		//    (jjwt-jackson-0.12.6.jar 자체나 그 안의 설정 파일은 정상이었음 — 직접 unzip으로 확인함)
		//    이 한 줄로 JVM 전체의 jar 캐싱을 꺼서, 매번 새로 파일을 열게 만들어 이 문제를 원천 차단합니다.
		//    반드시 SpringApplication.run()보다 먼저, main()의 가장 첫 줄에서 실행돼야 합니다 —
		//    다른 어떤 jar라도 이미 열린 뒤에 설러정하면 그 전에 캐싱된 핸들에는 적용되지 않습니다.
		java.net.URLConnection.setDefaultUseCaches("jar", false);

		SpringApplication.run(NaleulApplication.class, args);
	}
}