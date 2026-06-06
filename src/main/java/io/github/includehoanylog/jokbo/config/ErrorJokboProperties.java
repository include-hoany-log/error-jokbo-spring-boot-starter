package io.github.includehoanylog.jokbo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "error-jokbo")
public class ErrorJokboProperties {

    /**
     * 에러 족보 자동 스캔 기능 활성화 여부 (기본값: true)
     */
    private boolean enabled = true;

    /**
     * 스캔할 대상 프로젝트의 소스 코드 루트 경로 (기본값: src/main/java)
     */
    private String sourcePath = "src/main/java";

    /**
     * 에러 코드를 정의해둔 커스텀 Enum 클래스의 풀 패키지 경로
     * (예: com.example.shop.exception.ErrorCode)
     */
    private String enumClass;

    /**
     * 스캔 타겟이 될 커스텀 예외 클래스들의 풀 패키지 경로 목록
     * (예: com.example.shop.exception.BusinessException)
     */
    private List<String> exceptionClasses = new ArrayList<>();
}