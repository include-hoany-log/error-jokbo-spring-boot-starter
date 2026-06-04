package io.github.includehoanylog.jokbo.model;

import lombok.Builder;
import lombok.Getter;

@Getter
public class ErrorDefinition {

    private final String errorCode;       // 예: "USER_NOT_FOUND"
    private final String errorMessage;    // 예: "존재하지 않는 회원입니다."
    private final int httpStatus;         // 예: 404
    private final String className;       // 에러가 발생한 Controller/Service 클래스명
    private final String methodName;      // 에러가 발생한 메서드명

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
