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

    // Initialize the parser (Configure the symbol solver for deep navigation)
    public static void initialize(String sourcePath) {
        log.info("error-jokbo: Initializing JavaParser Symbol Solver... (Path: {})", sourcePath);

        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(new File(sourcePath)));

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);

        ParserConfiguration config = new ParserConfiguration().setSymbolResolver(symbolSolver);
        StaticJavaParser.setConfiguration(config);
    }

    // Parse all Java files within the specified source directory
    public static List<CompilationUnit> parseAllJavaFiles(String sourcePath) {
        List<CompilationUnit> compilationUnits = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Paths.get(sourcePath))) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            compilationUnits.add(StaticJavaParser.parse(p));
                        } catch (Exception e) {
                            log.warn("Failed to parse file: {}", p, e);
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to traverse the source directory", e);
        }
        return compilationUnits;
    }
}