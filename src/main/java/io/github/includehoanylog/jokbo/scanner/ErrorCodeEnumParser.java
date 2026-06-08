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
     * Parses the Error Enum class and returns a Map containing the Enum constant names as keys and error details as values.
     */
    public Map<String, ErrorCodeDetail> parse(String sourcePath, String enumClassPath) {
        Map<String, ErrorCodeDetail> errorDictionary = new HashMap<>();

        // 1. Convert the package path to the actual file system path (e.g., com.example.ErrorCode -> src/main/java/com/example/ErrorCode.java)
        String filePath = sourcePath + File.separator + enumClassPath.replace(".", File.separator) + ".java";
        File enumFile = new File(filePath);

        if (!enumFile.exists()) {
            log.error("error-jokbo: Could not find the specified Error Enum file. Path: {}", filePath);
            return errorDictionary;
        }

        try {
            // 2. Read the file using JavaParser and generate the Abstract Syntax Tree (AST)
            CompilationUnit cu = StaticJavaParser.parse(enumFile);

            // 3. Find and iterate through the Enum declarations within the file
            cu.findAll(EnumDeclaration.class).forEach(enumDecl -> {
                for (EnumConstantDeclaration constant : enumDecl.getEntries()) {
                    String name = constant.getNameAsString();

                    // Set default values
                    int status = 500;
                    String message = "Unknown Error";

                    // 4. Extract status code and message, assuming there are at least two constructor arguments
                    if (constant.getArguments().size() >= 2) {
                        Expression statusExpr = constant.getArguments().get(0);
                        Expression msgExpr = constant.getArguments().get(1);

                        status = extractHttpStatus(statusExpr);
                        message = msgExpr.toString().replaceAll("\"", ""); // Remove double quotes
                    }

                    errorDictionary.put(name, ErrorCodeDetail.builder()
                            .name(name)
                            .status(status)
                            .message(message)
                            .build());
                }
            });
            log.info("error-jokbo: Successfully built the Enum dictionary. Parsed a total of {} error codes.", errorDictionary.size());

        } catch (FileNotFoundException e) {
            log.error("error-jokbo: An error occurred while parsing the Enum file.", e);
        }

        return errorDictionary;
    }

    /**
     * A practical utility method to infer the numeric HTTP status from the expression.
     */
    private int extractHttpStatus(Expression expr) {
        // Case 1: The numeric status is provided directly in the constructor (e.g., 404)
        if (expr.isIntegerLiteralExpr()) {
            return expr.asIntegerLiteralExpr().asNumber().intValue();
        }

        // Case 2: A Spring HttpStatus enum is passed (e.g., HttpStatus.NOT_FOUND) - The most common scenario
        String exprStr = expr.toString().toUpperCase();
        if (exprStr.contains("BAD_REQUEST")) return 400;
        if (exprStr.contains("UNAUTHORIZED")) return 401;
        if (exprStr.contains("FORBIDDEN")) return 403;
        if (exprStr.contains("NOT_FOUND")) return 404;
        if (exprStr.contains("CONFLICT")) return 409;

        return 500; // Default fallback value upon parsing failure
    }
}