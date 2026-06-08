package io.github.includehoanylog.jokbo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "error-jokbo")
public class ErrorJokboProperties {
    private boolean enabled = true;
    private String sourcePath = "src/main/java";
    private String basePackage;
    private String enumClass;
    private List<String> exceptionClasses;
}