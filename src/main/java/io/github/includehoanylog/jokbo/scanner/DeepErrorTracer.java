package io.github.includehoanylog.jokbo.scanner; // Verify package name

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ThrowStmt;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DeepErrorTracer {

    private final String basePackage;
    private final List<CompilationUnit> allParsedFiles;
    private final Set<String> visitedMethods = new HashSet<>();

    public DeepErrorTracer(String basePackage, List<CompilationUnit> allParsedFiles) {
        this.basePackage = basePackage;
        this.allParsedFiles = allParsedFiles;
    }

    public Set<String> trace(MethodDeclaration method) {
        Set<String> errors = new HashSet<>();
        recursiveTrace(method, errors);
        return errors;
    }

    private void recursiveTrace(MethodDeclaration method, Set<String> errors) {
        // 1. Generate a simple visitation key to prevent infinite loops (especially in Lombok environments)
        String className = "Unknown";
        if (method.findAncestor(ClassOrInterfaceDeclaration.class).isPresent()) {
            className = method.findAncestor(ClassOrInterfaceDeclaration.class).get().getNameAsString();
        }
        String simpleSignature = className + "." + method.getNameAsString();

        if (!visitedMethods.add(simpleSignature)) return; // Skip if already analyzed!

        // 2. Collect custom errors directly thrown in the current method
        method.findAll(ThrowStmt.class).forEach(stmt -> {
            String exprStr = stmt.getExpression().toString();
            if (exprStr.contains(".")) {
                String extracted = exprStr.substring(exprStr.lastIndexOf(".") + 1);
                // Core: Remove all special characters (like parentheses) leaving only alphanumeric characters and underscores
                extracted = extracted.replaceAll("[^A-Za-z0-9_]", "");
                errors.add(extracted);
            }
        });

        // 3. Trace method calls (Includes fallback strategy for unresolved types)
        method.findAll(MethodCallExpr.class).forEach(call -> {
            String calledMethodName = call.getNameAsString();

            try {
                // Plan A: Strict type inference (Default JavaParser behavior)
                var resolved = call.resolve();
                if (resolved.getPackageName().startsWith(basePackage)) {
                    resolved.toAst().ifPresent(ast -> {
                        if (ast instanceof MethodDeclaration) {
                            recursiveTrace((MethodDeclaration) ast, errors);
                        }
                    });
                }
            } catch (Exception ignore) {
                // Plan B: If type inference fails, deduce the class name from the Spring Bean variable name and dive in!
                call.getScope().ifPresent(scope -> {
                    String variableName = scope.toString(); // e.g., "userService"

                    if (!variableName.isEmpty() && Character.isLowerCase(variableName.charAt(0))) {
                        // Capitalize the first letter (e.g., userService -> UserService)
                        String guessedClassName = variableName.substring(0, 1).toUpperCase() + variableName.substring(1);

                        // Search for the deduced class (or its Impl) across all parsed files and recursively trace its methods!
                        for (CompilationUnit cu : allParsedFiles) {
                            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
                                String targetName = clazz.getNameAsString();
                                if (targetName.equals(guessedClassName) || targetName.equals(guessedClassName + "Impl")) {
                                    clazz.getMethodsByName(calledMethodName).forEach(m -> recursiveTrace(m, errors));
                                }
                            });
                        }
                    }
                });
            }
        });
    }
}