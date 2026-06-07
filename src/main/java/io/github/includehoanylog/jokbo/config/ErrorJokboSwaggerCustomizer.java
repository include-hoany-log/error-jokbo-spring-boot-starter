package io.github.includehoanylog.jokbo.config;

import io.github.includehoanylog.jokbo.model.ErrorDefinition;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class ErrorJokboSwaggerCustomizer implements OperationCustomizer {

    private final Map<String, List<ErrorDefinition>> errorCache;

    public ErrorJokboSwaggerCustomizer(List<ErrorDefinition> errorDefinitions) {
        // 빠른 검색을 위해 클래스명#메서드명 구조로 그룹핑하여 맵에 보관합니다.
        this.errorCache = errorDefinitions.stream()
                .collect(Collectors.groupingBy(
                        err -> err.getClassName() + "#" + err.getMethodName()
                ));
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        String className = handlerMethod.getBeanType().getSimpleName();
        String methodName = handlerMethod.getMethod().getName();
        String cacheKey = className + "#" + methodName;

        // 현재 API 엔드포인트 메서드에 매핑된 에러가 있는지 확인
        if (errorCache.containsKey(cacheKey)) {
            List<ErrorDefinition> matchedErrors = errorCache.get(cacheKey);
            ApiResponses apiResponses = operation.getResponses();

            if (apiResponses == null) {
                apiResponses = new ApiResponses();
                operation.setResponses(apiResponses);
            }

            // 발견된 에러들을 HTTP 상태 코드별로 스웨거 명세에 주입
            for (ErrorDefinition error : matchedErrors) {
                String statusStr = String.valueOf(error.getHttpStatus());

                // 기존에 스웨거가 해당 상태 코드의 응답을 가지고 있는지 확인, 없으면 새로 생성
                ApiResponse apiResponse = apiResponses.computeIfAbsent(statusStr, k -> new ApiResponse()
                        .description(error.getHttpStatus() + " 비즈니스 예외 발생"));

                // 에러 메시지 구조 정의 (스웨거 예시 필드 채우기)
                if (apiResponse.getContent() == null) {
                    Schema<Map<String, Object>> errorSchema = new Schema<>();
                    errorSchema.setType("object");
                    errorSchema.addProperty("code", new Schema<>().type("string").example(error.getErrorCode()));
                    errorSchema.addProperty("message", new Schema<>().type("string").example(error.getErrorMessage()));

                    Content content = new Content().addMediaType("application/json",
                            new MediaType().schema(errorSchema));
                    apiResponse.setContent(content);
                } else {
                    // 같은 상태 코드에 여러 에러가 있다면 description에 누적 표시
                    apiResponse.setDescription(apiResponse.getDescription() + " / " + error.getErrorCode() + ": " + error.getErrorMessage());
                }
            }
            log.info("error-jokbo: Swagger 문서 주입 성공 -> API: {}", cacheKey);
        }

        return operation;
    }
}