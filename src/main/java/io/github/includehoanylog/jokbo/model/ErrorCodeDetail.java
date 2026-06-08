package io.github.includehoanylog.jokbo.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class ErrorCodeDetail {
    private final String name;       // e.g., "USER_NOT_FOUND"
    private final int status;        // e.g., 404
    private final String message;    // e.g., "User does not exist."
}