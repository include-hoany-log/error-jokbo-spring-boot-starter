package io.github.includehoanylog.jokbo.scanner; // 패키지명 확인 필수!

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
        // 1. 롬복 환경에서 무한루프 방지를 위한 심플한 방문 키 생성
        String className = "Unknown";
        if (method.findAncestor(ClassOrInterfaceDeclaration.class).isPresent()) {
            className = method.findAncestor(ClassOrInterfaceDeclaration.class).get().getNameAsString();
        }
        String simpleSignature = className + "." + method.getNameAsString();

        if (!visitedMethods.add(simpleSignature)) return; // 이미 분석한 곳은 스킵!

        // 2. 현재 메서드에서 직접 던지는 JichulError 수집 (수정됨)
        method.findAll(ThrowStmt.class).forEach(stmt -> {
            String exprStr = stmt.getExpression().toString();
            if (exprStr.contains(".")) {
                String extracted = exprStr.substring(exprStr.lastIndexOf(".") + 1);
                // 🌟 핵심: 영어, 숫자, 언더바(_)만 남기고 괄호 같은 특수문자는 모두 제거!
                extracted = extracted.replaceAll("[^A-Za-z0-9_]", "");
                errors.add(extracted);
            }
        });

        // 3. 메서드 호출 추적 (⭐️ 대망의 플랜 B 장착 ⭐️)
        method.findAll(MethodCallExpr.class).forEach(call -> {
            String calledMethodName = call.getNameAsString();

            try {
                // 플랜 A: 엄격한 타입 추론 (JavaParser 기본 동작)
                var resolved = call.resolve();
                if (resolved.getPackageName().startsWith(basePackage)) {
                    resolved.toAst().ifPresent(ast -> {
                        if (ast instanceof MethodDeclaration) {
                            recursiveTrace((MethodDeclaration) ast, errors);
                        }
                    });
                }
            } catch (Exception ignore) {
                // 플랜 B: 타입 추론 실패 시, 스프링 빈 변수명으로 클래스를 유추해서 강제 다이브!
                call.getScope().ifPresent(scope -> {
                    String variableName = scope.toString(); // 예: "userService"

                    if (!variableName.isEmpty() && Character.isLowerCase(variableName.charAt(0))) {
                        // 첫 글자 대문자화 (userService -> UserService)
                        String guessedClassName = variableName.substring(0, 1).toUpperCase() + variableName.substring(1);

                        // 전체 파일에서 유추한 클래스(혹은 Impl)를 찾아서 그 안의 메서드로 침투!
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