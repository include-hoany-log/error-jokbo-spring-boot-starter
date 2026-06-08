package io.github.includehoanylog.jokbo.scanner;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Getter
public class JavaParserErrorScanner {

    private final String basePackage;
    private final List<CompilationUnit> allParsedFiles;

    // Key: "UserInfoController.getUserInfo", Value: ["NOT_FOUND_USER"]
    private final Map<String, Set<String>> endpointErrorMap = new HashMap<>();

    public JavaParserErrorScanner(List<CompilationUnit> allParsedFiles, String basePackage) {
        this.allParsedFiles = allParsedFiles;
        this.basePackage = basePackage;
    }

    public void scanAndMapErrors() {
        log.info("error-jokbo: Starting deep-tracing error analysis... (Target: {})", basePackage);

        for (CompilationUnit cu : allParsedFiles) {
            cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(clazz -> clazz.getAnnotationByName("RestController").isPresent() ||
                            clazz.getAnnotationByName("Controller").isPresent())
                    .forEach(controller -> {
                        controller.getMethods().forEach(method -> {
                            if (isApiMethod(method)) {

                                DeepErrorTracer tracer = new DeepErrorTracer(basePackage, allParsedFiles);
                                Set<String> apiErrors = tracer.trace(method);

                                if (!apiErrors.isEmpty()) {
                                    // Unique key for Swagger mapping (Must be maintained for Swagger recognition!)
                                    String className = controller.getNameAsString();
                                    String methodName = method.getNameAsString();
                                    String mapKey = className + "." + methodName;

                                    // 🌟 1. Extract endpoint path and HTTP method for log output
                                    String httpMethod = extractHttpMethod(method);
                                    String endpointPath = extractEndpointPath(controller, method);

                                    // 🌟 2. Print intuitive URL-based English log!
                                    // Example: [GET /api/v1/user] [NOT_FOUND_USER] Registered Swagger error specification.
                                    log.info("[{} {}] {} Registered Swagger error specification.", httpMethod, endpointPath, apiErrors);

                                    endpointErrorMap.put(mapKey, apiErrors);
                                }
                            }
                        });
                    });
        }
    }

    private boolean isApiMethod(MethodDeclaration method) {
        return method.getAnnotationByName("GetMapping").isPresent() ||
                method.getAnnotationByName("PostMapping").isPresent() ||
                method.getAnnotationByName("PutMapping").isPresent() ||
                method.getAnnotationByName("DeleteMapping").isPresent() ||
                method.getAnnotationByName("PatchMapping").isPresent() ||
                method.getAnnotationByName("RequestMapping").isPresent();
    }

    // 💡 Extract HTTP method type (GET, POST, etc.)
    private String extractHttpMethod(MethodDeclaration method) {
        if (method.getAnnotationByName("GetMapping").isPresent()) return "GET";
        if (method.getAnnotationByName("PostMapping").isPresent()) return "POST";
        if (method.getAnnotationByName("PutMapping").isPresent()) return "PUT";
        if (method.getAnnotationByName("DeleteMapping").isPresent()) return "DELETE";
        if (method.getAnnotationByName("PatchMapping").isPresent()) return "PATCH";
        return "API";
    }

    // 💡 Extract actual URL path from Controller and Method annotations
    private String extractEndpointPath(ClassOrInterfaceDeclaration controller, MethodDeclaration method) {
        String basePath = getAnnotationValue(controller, "RequestMapping");

        String methodPath = "";
        for (String mapping : List.of("GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping", "RequestMapping")) {
            String path = getAnnotationValue(method, mapping);
            if (!path.isEmpty()) {
                methodPath = path;
                break;
            }
        }

        // Combine and refine paths (e.g., /api/v1/user + /delete -> /api/v1/user/delete)
        String fullPath = basePath + (methodPath.startsWith("/") ? "" : "/") + methodPath;
        fullPath = fullPath.replaceAll("//+", "/"); // Remove potential duplicate slashes

        // Remove trailing slash if present (e.g., /api/v1/user/ -> /api/v1/user)
        if (fullPath.endsWith("/") && fullPath.length() > 1) {
            fullPath = fullPath.substring(0, fullPath.length() - 1);
        }

        return fullPath.isEmpty() ? "/" : fullPath;
    }

    // 💡 Helper method to extract string values (StringLiteral) from annotations
    private String getAnnotationValue(NodeWithAnnotations<?> node, String annotationName) {
        return node.getAnnotationByName(annotationName)
                .flatMap(anno -> anno.findAll(StringLiteralExpr.class).stream().findFirst())
                .map(StringLiteralExpr::asString)
                .orElse("");
    }
}