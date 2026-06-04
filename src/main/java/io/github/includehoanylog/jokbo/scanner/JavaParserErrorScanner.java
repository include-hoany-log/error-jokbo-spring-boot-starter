package io.github.includehoanylog.jokbo.scanner;

import io.github.includehoanylog.jokbo.model.ErrorDefinition;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class JavaParserErrorScanner implements ErrorScanner {

    @Override
    public List<ErrorDefinition> scan(String sourcePath, String enumClassPath) {
        List<ErrorDefinition> errors = new ArrayList<>();
        log.info("error-jokbo 스캔 시작 -> 경로: {}, 대상 에러 Enum: {}", sourcePath, enumClassPath);

        File rootDir = new File(sourcePath);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            log.warn("지정된 소스 경로가 존재하지 않거나 폴더가 아닙니다: {}", sourcePath);
            return errors;
        }

        // TODO: 3단계에서 JavaParser를 활용해 본격적으로 소스 코드를 파싱하는 로직이 들어옵니다.

        return errors;
    }
}
