package io.github.includehoanylog.jokbo.scanner;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Getter // 👈 추가! (나중에 스웨거가 맵을 꺼내갈 수 있도록)
public class JavaParserErrorScanner {

    private final String basePackage;
    private final List<CompilationUnit> allParsedFiles;

    // 🌟 핵심: 수집한 에러를 담아둘 바구니 (Key: "UserInfoController.getUserInfo", Value: ["NOT_FOUND_USER"])
    private final Map<String, Set<String>> endpointErrorMap = new HashMap<>();

    public JavaParserErrorScanner(List<CompilationUnit> allParsedFiles, String basePackage) {
        this.allParsedFiles = allParsedFiles;
        this.basePackage = basePackage;
    }

    public void scanAndMapErrors() {
        // 🌟 시작 로그를 전문적인 영어로 변경
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
                                    String className = controller.getNameAsString();
                                    String methodName = method.getNameAsString();
                                    String mapKey = className + "." + methodName;

                                    // 🌟 대망의 전문적인 영어 로그 포맷 적용
                                    // 출력 예시: [UserInfoController.getUserInfo] [NOT_FOUND_USER] Registered Swagger error specification.
                                    log.info("[{}] {} Registered Swagger error specification.", mapKey, apiErrors);

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
}