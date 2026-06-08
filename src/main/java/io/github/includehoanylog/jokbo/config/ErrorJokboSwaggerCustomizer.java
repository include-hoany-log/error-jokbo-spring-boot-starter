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
        // Group by className#methodName structure and store in a map for fast lookups.
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

        // Check if there are any errors mapped to the current API endpoint method.
        if (errorCache.containsKey(cacheKey)) {
            List<ErrorDefinition> matchedErrors = errorCache.get(cacheKey);
            ApiResponses apiResponses = operation.getResponses();

            if (apiResponses == null) {
                apiResponses = new ApiResponses();
                operation.setResponses(apiResponses);
            }

            // Inject the found errors into the Swagger specification, grouped by HTTP status code.
            for (ErrorDefinition error : matchedErrors) {
                String statusStr = String.valueOf(error.getHttpStatus());

                // Check if Swagger already has a response for this status code; if not, create a new one.
                ApiResponse apiResponse = apiResponses.computeIfAbsent(statusStr, k -> new ApiResponse()
                        .description(error.getHttpStatus() + " Business Exception"));

                // Define the error message structure (populate the Swagger example fields).
                if (apiResponse.getContent() == null) {
                    Schema<Map<String, Object>> errorSchema = new Schema<>();
                    errorSchema.setType("object");
                    errorSchema.addProperty("code", new Schema<>().type("string").example(error.getErrorCode()));
                    errorSchema.addProperty("message", new Schema<>().type("string").example(error.getErrorMessage()));

                    Content content = new Content().addMediaType("application/json",
                            new MediaType().schema(errorSchema));
                    apiResponse.setContent(content);
                } else {
                    // If there are multiple errors for the same status code, append them to the description.
                    apiResponse.setDescription(apiResponse.getDescription() + " / " + error.getErrorCode() + ": " + error.getErrorMessage());
                }
            }
            log.info("error-jokbo: Successfully injected Swagger documentation -> API: {}", cacheKey);
        }

        return operation;
    }
}