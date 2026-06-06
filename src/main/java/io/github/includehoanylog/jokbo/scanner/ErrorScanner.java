package io.github.includehoanylog.jokbo.scanner;

import io.github.includehoanylog.jokbo.model.ErrorDefinition;
import java.util.List;

public interface ErrorScanner {
    /**
     * 지정된 소스 경로를 탐색하여 비즈니스 에러 정의들을 추출합니다.
     */
    List<ErrorDefinition> scan(String sourcePath, String enumClassPath, List<String> targetExceptionClasses);
}