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
            // 1. Filter classes annotated with @RestController or @Controller
            List<ClassOrInterfaceDeclaration> controllers = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(c -> c.isAnnotationPresent("RestController") || c.isAnnotationPresent("Controller"))
                    .toList();

            for (ClassOrInterfaceDeclaration controller : controllers) {
                String className = controller.getNameAsString();

                // 2. Identify API endpoint methods (e.g., @GetMapping, etc.)
                List<MethodDeclaration> apiMethods = controller.findAll(MethodDeclaration.class).stream()
                        .filter(this::isMappingMethod)
                        .toList();

                for (MethodDeclaration method : apiMethods) {
                    String methodName = method.getNameAsString();

                    // 3. Search for 'throw' statements within the matched method
                    List<ThrowStmt> throwStmts = method.findAll(ThrowStmt.class);

                    for (ThrowStmt throwStmt : throwStmts) {
                        // Verify if the expression being thrown is an object instantiation (i.e., 'new' keyword)
                        if (throwStmt.getExpression().isObjectCreationExpr()) {
                            ObjectCreationExpr newExpr = throwStmt.getExpression().asObjectCreationExpr();
                            String exceptionName = newExpr.getType().getNameAsString();

                            // 4. Check if the instantiated exception matches our target exception list
                            if (isTargetException(exceptionName)) {
                                // e.g., Extract the first argument from: new BusinessException(ErrorCode.USER_NOT_FOUND)
                                if (!newExpr.getArguments().isEmpty()) {
                                    String argStr = newExpr.getArguments().get(0).toString();

                                    // Extract the constant name (e.g., 'USER_NOT_FOUND' from 'ErrorCode.USER_NOT_FOUND')
                                    String enumKey = extractEnumConstant(argStr);

                                    // 5. If the parsed enum key exists in the dictionary, map it to the ErrorDefinition DTO!
                                    if (errorDictionary.containsKey(enumKey)) {
                                        ErrorCodeDetail detail = errorDictionary.get(enumKey);
                                        results.add(ErrorDefinition.builder()
                                                .errorCode(detail.getName())
                                                .errorMessage(detail.getMessage())
                                                .httpStatus(detail.getStatus())
                                                .className(className)
                                                .methodName(methodName)
                                                .build());

                                        log.info("error-jokbo: Error detected -> [{}] {} in API: {}",
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
     * Determines if a method acts as an API endpoint by checking its mapping annotations.
     */
    private boolean isMappingMethod(MethodDeclaration method) {
        return method.isAnnotationPresent("GetMapping") ||
                method.isAnnotationPresent("PostMapping") ||
                method.isAnnotationPresent("PutMapping") ||
                method.isAnnotationPresent("DeleteMapping") ||
                method.isAnnotationPresent("PatchMapping") ||
                method.isAnnotationPresent("RequestMapping") ||
                method.isAnnotationPresent("Operation"); // Includes Swagger specification annotations
    }

    /**
     * Compares the exception class name by omitting the package path.
     * (e.g., 'com.ex.BusinessException' becomes 'BusinessException')
     */
    private boolean isTargetException(String exceptionName) {
        return targetExceptions.stream()
                .map(fullPath -> fullPath.substring(fullPath.lastIndexOf(".") + 1))
                .anyMatch(target -> target.equals(exceptionName));
    }

    /**
     * Extracts the Enum constant name by stripping the class prefix.
     * (e.g., returns 'USER_NOT_FOUND' from 'ErrorCode.USER_NOT_FOUND')
     */
    private String extractEnumConstant(String argStr) {
        if (argStr.contains(".")) {
            return argStr.substring(argStr.lastIndexOf(".") + 1);
        }
        return argStr; // Handles cases like static imports
    }
}