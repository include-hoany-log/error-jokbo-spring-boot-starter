package io.github.includehoanylog.jokbo.scanner;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.expr.Expression;
import io.github.includehoanylog.jokbo.model.ErrorCodeDetail;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ErrorCodeEnumParser {

    /**
     * Enum 클래스를 파싱하여 Map<Enum상수명, 에러상세정보> 형태로 반환합니다.
     */
    public Map<String, ErrorCodeDetail> parse(String sourcePath, String enumClassPath) {
        Map<String, ErrorCodeDetail> errorDictionary = new HashMap<>();

        // 1. 패키지 경로를 실제 파일 시스템 경로로 변환 (예: com.example.ErrorCode -> src/main/java/com/example/ErrorCode.java)
        String filePath = sourcePath + File.separator + enumClassPath.replace(".", File.separator) + ".java";
        File enumFile = new File(filePath);

        if (!enumFile.exists()) {
            log.error("error-jokbo: 지정된 에러 Enum 파일을 찾을 수 없습니다. 경로: {}", filePath);
            return errorDictionary;
        }

        try {
            // 2. JavaParser로 파일 읽어서 추상 구문 트리(AST) 생성
            CompilationUnit cu = StaticJavaParser.parse(enumFile);

            // 3. 파일 내의 Enum 선언부를 찾아서 순회
            cu.findAll(EnumDeclaration.class).forEach(enumDecl -> {
                for (EnumConstantDeclaration constant : enumDecl.getEntries()) {
                    String name = constant.getNameAsString();

                    // 기본값 셋팅
                    int status = 500;
                    String message = "알 수 없는 에러";

                    // 4. 생성자 인자가 2개 이상이라고 가정하고 (상태코드, 메시지) 추출
                    if (constant.getArguments().size() >= 2) {
                        Expression statusExpr = constant.getArguments().get(0);
                        Expression msgExpr = constant.getArguments().get(1);

                        status = extractHttpStatus(statusExpr);
                        message = msgExpr.toString().replaceAll("\"", ""); // 쌍따옴표 제거
                    }

                    errorDictionary.put(name, ErrorCodeDetail.builder()
                            .name(name)
                            .status(status)
                            .message(message)
                            .build());
                }
            });
            log.info("error-jokbo: Enum 사전 구축 완료. 총 {}개의 에러 코드 파싱됨.", errorDictionary.size());

        } catch (FileNotFoundException e) {
            log.error("error-jokbo: Enum 파싱 중 오류 발생", e);
        }

        return errorDictionary;
    }

    /**
     * 상태 코드 표현식에서 숫자를 유추하는 실용적인 유틸 메서드
     */
    private int extractHttpStatus(Expression expr) {
        // 경우 1: 생성자에 404 처럼 숫자를 직접 적은 경우
        if (expr.isIntegerLiteralExpr()) {
            return expr.asIntegerLiteralExpr().asNumber().intValue();
        }

        // 경우 2: 스프링의 HttpStatus.NOT_FOUND 처럼 Enum을 넣은 경우 (가장 흔함)
        String exprStr = expr.toString().toUpperCase();
        if (exprStr.contains("BAD_REQUEST")) return 400;
        if (exprStr.contains("UNAUTHORIZED")) return 401;
        if (exprStr.contains("FORBIDDEN")) return 403;
        if (exprStr.contains("NOT_FOUND")) return 404;
        if (exprStr.contains("CONFLICT")) return 409;

        return 500; // 파싱 실패 시 기본값
    }
}