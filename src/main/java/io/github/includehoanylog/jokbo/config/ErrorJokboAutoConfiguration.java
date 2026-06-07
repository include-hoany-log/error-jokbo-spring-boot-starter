package io.github.includehoanylog.jokbo.config;

import com.github.javaparser.ast.CompilationUnit;
import io.github.includehoanylog.jokbo.scanner.JavaParserErrorScanner;
import io.github.includehoanylog.jokbo.scanner.ProjectAstParser;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

@Slf4j
@Configuration
@EnableConfigurationProperties(ErrorJokboProperties.class)
@ConditionalOnProperty(prefix = "error-jokbo", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ErrorJokboAutoConfiguration {

    @Bean
    public JavaParserErrorScanner javaParserErrorScanner(ErrorJokboProperties properties) {
        log.info("error-jokbo: 오픈소스 라이브러리 가동 중...");

        ProjectAstParser.initialize(properties.getSourcePath());
        List<CompilationUnit> allParsedFiles = ProjectAstParser.parseAllJavaFiles(properties.getSourcePath());

        JavaParserErrorScanner scanner = new JavaParserErrorScanner(allParsedFiles, properties.getBasePackage());
        scanner.scanAndMapErrors();

        return scanner;
    }

    // 🌟 동적 HTTP 상태 코드 분리형 스웨거 렌더링 엔진
    @Bean
    public OperationCustomizer errorJokboOperationCustomizer(JavaParserErrorScanner scanner, ErrorJokboProperties properties) {
        return (operation, handlerMethod) -> {

            String className = handlerMethod.getBeanType().getSimpleName();
            String methodName = handlerMethod.getMethod().getName();
            String mapKey = className + "." + methodName;

            Set<String> errorCodes = scanner.getEndpointErrorMap().get(mapKey);

            if (errorCodes != null && !errorCodes.isEmpty()) {
                ApiResponses responses = operation.getResponses();

                errorCodes.forEach(errorCode -> {
                    try {
                        Class<?> enumClass = Class.forName(properties.getEnumClass());
                        Object[] enumConstants = enumClass.getEnumConstants();

                        String errorMessage = "에러 메시지를 찾을 수 없습니다.";
                        String statusCode = "500"; // 👈 기본값은 500으로 설정

                        // 1. 엔티티와 일치하는 Enum 상수 추적
                        for (Object constant : enumConstants) {
                            if (((Enum<?>) constant).name().equals(errorCode)) {

                                // 2. getMessage() 호출하여 에러 메시지 획득
                                try {
                                    Method getMessageMethod = enumClass.getMethod("getMessage");
                                    errorMessage = (String) getMessageMethod.invoke(constant);
                                } catch (NoSuchMethodException e) {
                                    log.debug("Enum에 getMessage() 메서드가 없습니다.");
                                }

                                // 3. 🌟 핵심: getStatusCode() 호출하여 HTTP 상태 코드 동적 획득!
                                try {
                                    Method getStatusCodeMethod = enumClass.getMethod("getStatusCode");
                                    statusCode = (String) getStatusCodeMethod.invoke(constant);
                                } catch (NoSuchMethodException e) {
                                    log.debug("Enum에 getStatusCode() 메서드가 없습니다. 기본값 500을 사용합니다.");
                                }
                                break;
                            }
                        }

                        // 4. 🌟 해당 에러 코드에 명시된 HTTP 상태 코드(예: 400 또는 500) 그룹 틀을 찾거나 생성
                        ApiResponse apiResponse = responses.get(statusCode);
                        if (apiResponse == null) {
                            String description = statusCode.startsWith("4") ? "클라이언트 요청 오류" : "서버 내부 오류";
                            apiResponse = new ApiResponse().description(description);

                            MediaType mediaType = new MediaType().schema(new Schema<>().type("object"));
                            apiResponse.setContent(new Content().addMediaType("application/json", mediaType));

                            responses.addApiResponse(statusCode, apiResponse);
                        }

                        // 5. 해당 상태 코드 그룹의 미디어 타입 꺼내기
                        MediaType mediaType = apiResponse.getContent().get("application/json");

                        // 6. JSON 예시 데이터 바인딩
                        java.util.Map<String, String> exampleMap = new java.util.LinkedHashMap<>();
                        exampleMap.put("code", errorCode);
                        exampleMap.put("message", errorMessage);

                        io.swagger.v3.oas.models.examples.Example example = new io.swagger.v3.oas.models.examples.Example();
                        example.setValue(exampleMap);
                        example.setSummary(errorMessage);

                        // 7. 드롭다운 목록에 최종 안착
                        mediaType.addExamples(errorCode, example);

                    } catch (Exception e) {
                        log.warn("error-jokbo: Enum 동적 파싱 중 예외 발생 (코드: {})", errorCode, e);
                    }
                });
            }
            return operation;
        };
    }
}