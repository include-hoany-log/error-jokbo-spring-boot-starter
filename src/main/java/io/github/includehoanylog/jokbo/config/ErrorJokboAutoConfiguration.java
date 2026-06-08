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
        log.info("error-jokbo: Starting open-source library...");

        ProjectAstParser.initialize(properties.getSourcePath());
        List<CompilationUnit> allParsedFiles = ProjectAstParser.parseAllJavaFiles(properties.getSourcePath());

        JavaParserErrorScanner scanner = new JavaParserErrorScanner(allParsedFiles, properties.getBasePackage());
        scanner.scanAndMapErrors();

        return scanner;
    }

    // 🌟 Dynamic HTTP Status Code Segregated Swagger Rendering Engine
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

                        String errorMessage = "Error message not found.";
                        String statusCode = "500"; // 👈 Default value set to 500

                        // 1. Trace the Enum constant matching the entity
                        for (Object constant : enumConstants) {
                            if (((Enum<?>) constant).name().equals(errorCode)) {

                                // 2. Obtain error message by calling getMessage()
                                try {
                                    Method getMessageMethod = enumClass.getMethod("getMessage");
                                    errorMessage = (String) getMessageMethod.invoke(constant);
                                } catch (NoSuchMethodException e) {
                                    log.debug("Method getMessage() not found in Enum.");
                                }

                                // 3. 🌟 Core: Dynamically obtain HTTP status code by calling getStatusCode()!
                                try {
                                    Method getStatusCodeMethod = enumClass.getMethod("getStatusCode");
                                    statusCode = (String) getStatusCodeMethod.invoke(constant);
                                } catch (NoSuchMethodException e) {
                                    log.debug("Method getStatusCode() not found in Enum. Using default value 500.");
                                }
                                break;
                            }
                        }

                        // 4. 🌟 Find or create the response group for the specified HTTP status code (e.g., 400 or 500)
                        ApiResponse apiResponse = responses.get(statusCode);
                        if (apiResponse == null) {
                            String description = statusCode.startsWith("4") ? "Client Request Error" : "Internal Server Error";
                            apiResponse = new ApiResponse().description(description);

                            MediaType mediaType = new MediaType().schema(new Schema<>().type("object"));
                            apiResponse.setContent(new Content().addMediaType("application/json", mediaType));

                            responses.addApiResponse(statusCode, apiResponse);
                        }

                        // 5. Extract the media type for the corresponding status code group
                        MediaType mediaType = apiResponse.getContent().get("application/json");

                        // 6. Bind JSON example data
                        java.util.Map<String, String> exampleMap = new java.util.LinkedHashMap<>();
                        exampleMap.put("code", errorCode);
                        exampleMap.put("message", errorMessage);

                        io.swagger.v3.oas.models.examples.Example example = new io.swagger.v3.oas.models.examples.Example();
                        example.setValue(exampleMap);
                        example.setSummary(errorMessage);

                        // 7. Finally, land in the dropdown list
                        mediaType.addExamples(errorCode, example);

                    } catch (Exception e) {
                        log.warn("error-jokbo: Exception occurred during dynamic Enum parsing (Code: {})", errorCode, e);
                    }
                });
            }
            return operation;
        };
    }
}