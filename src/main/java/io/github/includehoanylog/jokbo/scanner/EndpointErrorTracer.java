package io.github.includehoanylog.jokbo.scanner;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ThrowStmt;
import io.github.includehoanylog.jokbo.model.ErrorCodeDetail;
import io.github.includehoanylog.jokbo.model.ErrorDefinition;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class EndpointErrorTracer {

    private final Map<String, ErrorCodeDetail> errorDictionary;
    private final List<String> targetExceptions;

    public EndpointErrorTracer(Map<String, ErrorCodeDetail> errorDictionary, List<String> targetExceptions) {
        this.errorDictionary = errorDictionary;
        this.targetExceptions = targetExceptions;
    }

    public List<ErrorDefinition> trace(List<CompilationUnit> astList) {
        List<ErrorDefinition> results = new ArrayList<>();

        for (CompilationUnit cu : astList) {
            // 1. @RestController나 @Controller가 붙은 클래스만 필터링
            List<ClassOrInterfaceDeclaration> controllers = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(c -> c.isAnnotationPresent("RestController") || c.isAnnotationPresent("Controller"))
                    .toList();

            for (ClassOrInterfaceDeclaration controller : controllers) {
                String className = controller.getNameAsString();

                // 2. API 엔드포인트 메서드 찾기 (@GetMapping 등)
                List<MethodDeclaration> apiMethods = controller.findAll(MethodDeclaration.class).stream()
                        .filter(this::isMappingMethod)
                        .toList();

                for (MethodDeclaration method : apiMethods) {
                    String methodName = method.getNameAsString();

                    // 3. 해당 메서드 내에서 'throw' 구문 찾기
                    List<ThrowStmt> throwStmts = method.findAll(ThrowStmt.class);

                    for (ThrowStmt throwStmt : throwStmts) {
                        // throw 뒤에 있는게 객체 생성(new)인지 확인
                        if (throwStmt.getExpression().isObjectCreationExpr()) {
                            ObjectCreationExpr newExpr = throwStmt.getExpression().asObjectCreationExpr();
                            String exceptionName = newExpr.getType().getNameAsString();

                            // 4. 우리가 타겟으로 삼은 예외 클래스인지 확인
                            if (isTargetException(exceptionName)) {
                                // 예: new BusinessException(ErrorCode.USER_NOT_FOUND) -> 첫번째 인자 추출
                                if (!newExpr.getArguments().isEmpty()) {
                                    String argStr = newExpr.getArguments().get(0).toString();

                                    // 'ErrorCode.USER_NOT_FOUND' 에서 'USER_NOT_FOUND' 부분만 추출
                                    String enumKey = extractEnumConstant(argStr);

                                    // 5. 사전에 있는 에러면 최종 바구니(DTO)에 담기!
                                    if (errorDictionary.containsKey(enumKey)) {
                                        ErrorCodeDetail detail = errorDictionary.get(enumKey);
                                        results.add(ErrorDefinition.builder()
                                                .errorCode(detail.getName())
                                                .errorMessage(detail.getMessage())
                                                .httpStatus(detail.getStatus())
                                                .className(className)
                                                .methodName(methodName)
                                                .build());

                                        log.info("error-jokbo: 에러 감지됨 -> [{}] {} in {}",
                                                detail.getStatus(), detail.getName(), methodName);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return results;
    }

    /**
     * API 통신용 메서드인지 어노테이션으로 판별
     */
    private boolean isMappingMethod(MethodDeclaration method) {
        return method.isAnnotationPresent("GetMapping") ||
                method.isAnnotationPresent("PostMapping") ||
                method.isAnnotationPresent("PutMapping") ||
                method.isAnnotationPresent("DeleteMapping") ||
                method.isAnnotationPresent("PatchMapping") ||
                method.isAnnotationPresent("RequestMapping") ||
                method.isAnnotationPresent("Operation"); // Swagger 명세 기준 추가
    }

    /**
     * 패키지 경로를 제외한 클래스명만 비교 (예: com.ex.BusinessException -> BusinessException)
     */
    private boolean isTargetException(String exceptionName) {
        return targetExceptions.stream()
                .map(fullPath -> fullPath.substring(fullPath.lastIndexOf(".") + 1))
                .anyMatch(target -> target.equals(exceptionName));
    }

    /**
     * ErrorCode.USER_NOT_FOUND 형태에서 '.' 뒷부분(Enum 상수명)만 추출
     */
    private String extractEnumConstant(String argStr) {
        if (argStr.contains(".")) {
            return argStr.substring(argStr.lastIndexOf(".") + 1);
        }
        return argStr; // static import 된 경우 등
    }
}