package io.github.includehoanylog.jokbo.scanner;

import io.github.includehoanylog.jokbo.model.ErrorDefinition;
import lombok.extern.slf4j.Slf4j;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class JavaParserErrorScanner implements ErrorScanner {

    @Override
    public List<ErrorDefinition> scan(String sourcePath, String enumClassPath, List<String> targetExceptionClasses) {
        List<ErrorDefinition> errors = new ArrayList<>();

        if (targetExceptionClasses == null || targetExceptionClasses.isEmpty()) {
            log.warn("error-jokbo: 타겟 예외 클래스가 설정되지 않았습니다. 스캔을 건너뜁니다.");
            return errors;
        }

        log.info("error-jokbo 스캔 시작 -> 경로: {}, 대상 Enum: {}, 타겟 예외: {}",
                sourcePath, enumClassPath, targetExceptionClasses);

        File rootDir = new File(sourcePath);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            log.warn("지정된 소스 경로가 존재하지 않거나 폴더가 아닙니다: {}", sourcePath);
            return errors;
        }

        // TODO: JavaParser 파싱 로직

        return errors;
    }
}