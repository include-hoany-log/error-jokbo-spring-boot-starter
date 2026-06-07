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
        log.info("error-jokbo: 딥-트레이싱 에러 추적 분석을 시작합니다... (Target: {})", basePackage);

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
                                    // 컨트롤러 이름과 메서드 이름을 조합해서 고유 키를 만듭니다!
                                    String className = controller.getNameAsString();
                                    String methodName = method.getNameAsString();
                                    String mapKey = className + "." + methodName;

                                    log.info("API [{}] 에서 딥-에러 발견!: {}", mapKey, apiErrors);

                                    // 🌟 수집된 에러들을 바구니에 저장!
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