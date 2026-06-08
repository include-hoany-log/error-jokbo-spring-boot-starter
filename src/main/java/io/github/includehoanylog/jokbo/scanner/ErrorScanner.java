package io.github.includehoanylog.jokbo.scanner;

import io.github.includehoanylog.jokbo.model.ErrorDefinition;
import java.util.List;

public interface ErrorScanner {
    /**
     * Scans the specified source path to extract business error definitions.
     */
    List<ErrorDefinition> scan(String sourcePath, String enumClassPath, List<String> targetExceptionClasses);
}