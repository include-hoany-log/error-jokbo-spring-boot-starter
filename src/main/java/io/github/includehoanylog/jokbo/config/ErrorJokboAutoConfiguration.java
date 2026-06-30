package io.github.includehoanylog.jokbo.config;

import com.github.javaparser.ast.CompilationUnit;
import io.github.includehoanylog.jokbo.scanner.JavaParserErrorScanner;
import io.github.includehoanylog.jokbo.scanner.ProjectAstParser;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

@Slf4j
@Configuration
@EnableConfigurationProperties(ErrorJokboProperties.class)
@ConditionalOnProperty(prefix = "error-jokbo", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ErrorJokboAutoConfiguration {

    // 🌟 이 부분이 실수로 지워졌을 겁니다! 다시 부활시켰습니다.
    @Bean
    public JavaParserErrorScanner javaParserErrorScanner(ErrorJokboProperties properties) {
        log.info("error-jokbo: Starting open-source library...");

        ProjectAstParser.initialize(properties.getSourcePath());
        List<CompilationUnit> allParsedFiles = ProjectAstParser.parseAllJavaFiles(properties.getSourcePath());

        JavaParserErrorScanner scanner = new JavaParserErrorScanner(allParsedFiles, properties.getBasePackage());
        scanner.scanAndMapErrors();

        return scanner;
    }

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
                        String statusCode = "500";

                        for (Object constant : enumConstants) {
                            if (((Enum<?>) constant).name().equals(errorCode)) {
                                try {
                                    Method getMessageMethod = enumClass.getMethod("getMessage");
                                    errorMessage = (String) getMessageMethod.invoke(constant);
                                } catch (NoSuchMethodException e) { log.debug("Method getMessage() not found."); }

                                try {
                                    Method getStatusCodeMethod = enumClass.getMethod("getStatusCode");
                                    statusCode = (String) getStatusCodeMethod.invoke(constant);
                                } catch (NoSuchMethodException e) { log.debug("Method getStatusCode() not found."); }
                                break;
                            }
                        }

                        Class<?> responseClass = Class.forName(properties.getErrorResponseClass());

                        ApiResponse apiResponse = responses.get(statusCode);
                        if (apiResponse == null) {
                            String description = statusCode.startsWith("4") ? "Client Request Error" : "Internal Server Error";
                            apiResponse = new ApiResponse().description(description);

                            ResolvedSchema resolvedSchema = ModelConverters.getInstance()
                                    .resolveAsResolvedSchema(new AnnotatedType(responseClass));

                            MediaType mediaType = new MediaType().schema(resolvedSchema.schema);
                            apiResponse.setContent(new Content().addMediaType("application/json", mediaType));
                            responses.addApiResponse(statusCode, apiResponse);
                        }

                        MediaType mediaType = apiResponse.getContent().get("application/json");

                        Set<Class<?>> visitedClasses = new HashSet<>();
                        Map<String, Object> exampleMap = buildExampleMap(responseClass, errorCode, errorMessage, visitedClasses);

                        io.swagger.v3.oas.models.examples.Example example = new io.swagger.v3.oas.models.examples.Example();
                        example.setValue(exampleMap);
                        example.setSummary(errorMessage);

                        mediaType.addExamples(errorCode, example);

                    } catch (Exception e) {
                        log.warn("error-jokbo: Exception occurred during dynamic custom response mapping (Code: {})", errorCode, e);
                    }
                });
            }
            return operation;
        };
    }

    private Map<String, Object> buildExampleMap(Class<?> clazz, String errorCode, String errorMessage, Set<Class<?>> visitedClasses) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();

        if (!visitedClasses.add(clazz)) {
            return map;
        }

        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (field.isSynthetic()) continue;

                String fieldName = field.getName();
                if (map.containsKey(fieldName)) continue;

                if ("code".equals(fieldName)) {
                    map.put(fieldName, errorCode);
                } else if ("message".equals(fieldName)) {
                    map.put(fieldName, errorMessage);
                } else {
                    Class<?> fieldType = field.getType();
                    map.put(fieldName, getExampleValueForField(field, fieldType, errorCode, errorMessage, visitedClasses));
                }
            }
            currentClass = currentClass.getSuperclass();
        }

        visitedClasses.remove(clazz);
        return map;
    }

    private Object getExampleValueForField(Field field, Class<?> fieldType, String errorCode, String errorMessage, Set<Class<?>> visitedClasses) {
        if (fieldType == String.class) return "string";
        if (fieldType == int.class || fieldType == Integer.class) return 0;
        if (fieldType == long.class || fieldType == Long.class) return 0L;
        if (fieldType == boolean.class || fieldType == Boolean.class) return false;
        if (fieldType == double.class || fieldType == Double.class) return 0.0;
        if (fieldType.isEnum()) {
            Object[] constants = fieldType.getEnumConstants();
            return constants.length > 0 ? constants[0].toString() : null;
        }

        if (Collection.class.isAssignableFrom(fieldType)) {
            List<Object> listExample = new ArrayList<>();
            Type genericType = field.getGenericType();

            if (genericType instanceof ParameterizedType) {
                Type[] actualTypeArguments = ((ParameterizedType) genericType).getActualTypeArguments();
                if (actualTypeArguments.length > 0 && actualTypeArguments[0] instanceof Class<?>) {
                    Class<?> genericClass = (Class<?>) actualTypeArguments[0];
                    if (!isSimpleType(genericClass)) {
                        listExample.add(buildExampleMap(genericClass, errorCode, errorMessage, new HashSet<>(visitedClasses)));
                    } else {
                        listExample.add(getExampleValueForField(null, genericClass, errorCode, errorMessage, visitedClasses));
                    }
                }
            }
            return listExample;
        }

        if (!fieldType.getName().startsWith("java.")) {
            return buildExampleMap(fieldType, errorCode, errorMessage, new HashSet<>(visitedClasses));
        }

        return null;
    }

    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive() || clazz == String.class || Number.class.isAssignableFrom(clazz) || clazz == Boolean.class || clazz.isEnum();
    }
}