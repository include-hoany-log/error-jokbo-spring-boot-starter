package io.github.includehoanylog.jokbo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "error-jokbo")
public class ErrorJokboProperties {
    private boolean enabled = true;
    private String sourcePath = "src/main/java";
    private String basePackage;
    private String enumClass;
    private String errorResponseClass; // 🌟 추가: 사용자의 커스텀 에러 응답 객체 패키지 경로 (e.g., "com.example.dto.GlobalErrorResponse")
    private List<String> exceptionClasses;
}