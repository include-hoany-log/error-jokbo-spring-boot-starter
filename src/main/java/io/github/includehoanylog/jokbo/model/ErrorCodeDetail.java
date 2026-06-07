package io.github.includehoanylog.jokbo.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class ErrorCodeDetail {
    private final String name;       // 예: "USER_NOT_FOUND"
    private final int status;        // 예: 404
    private final String message;    // 예: "존재하지 않는 회원입니다."
}