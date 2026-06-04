package io.github.includehoanylog.jokbo.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties(ErrorJokboProperties.class)
@ConditionalOnProperty(prefix = "error-jokbo", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ErrorJokboAutoConfiguration {

    // 나중에 여기에 JavaParser 스캐너나 Swagger Customizer 빈(Bean)을 등록할 예정입니다!

}
