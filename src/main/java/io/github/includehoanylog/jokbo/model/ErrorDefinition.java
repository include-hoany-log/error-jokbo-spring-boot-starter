package io.github.includehoanylog.jokbo.model;

import lombok.Builder;
import lombok.Getter;

@Getter
public class ErrorDefinition {

    private final String errorCode;       // e.g., "USER_NOT_FOUND"
    private final String errorMessage;    // e.g., "User does not exist."
    private final int httpStatus;         // e.g., 404
    private final String className;       // Name of the Controller/Service class where the error occurred
    private final String methodName;      // Name of the method where the error occurred

    @Builder
    public ErrorDefinition(String errorCode,
                           String errorMessage,
                           int httpStatus,
                           String className,
                           String methodName) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.httpStatus = httpStatus;
        this.className = className;
        this.methodName = methodName;
    }

}