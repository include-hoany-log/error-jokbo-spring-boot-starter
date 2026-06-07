package io.github.includehoanylog.jokbo.scanner;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
public class ProjectAstParser {

    // 파서 초기화 (네비게이션 장착)
    public static void initialize(String sourcePath) {
        log.info("error-jokbo: JavaParser Symbol Solver 초기화 중... (경로: {})", sourcePath);

        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(new File(sourcePath)));

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);

        ParserConfiguration config = new ParserConfiguration().setSymbolResolver(symbolSolver);
        StaticJavaParser.setConfiguration(config);
    }

    // 소스 경로의 모든 자바 파일을 파싱
    public static List<CompilationUnit> parseAllJavaFiles(String sourcePath) {
        List<CompilationUnit> compilationUnits = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Paths.get(sourcePath))) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            compilationUnits.add(StaticJavaParser.parse(p));
                        } catch (Exception e) {
                            log.warn("파싱 실패: {}", p, e);
                        }
                    });
        } catch (Exception e) {
            log.error("소스 경로 순회 실패", e);
        }
        return compilationUnits;
    }
}