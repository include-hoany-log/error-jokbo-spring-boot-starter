# 🦖 Error-Jokbo (에러 족보)

> 비즈니스 예외(Exception)를 소스코드 정적 분석을 통해 Swagger UI에 에러 응답 명세로 자동 주입해 주는 스마트 라이브러리

Error-Jokbo는 기존의 런타임 AOP나 리플렉션 방식 대신, 프로젝트의 실제 자바 소스코드 자체를 정적 분석(AST 파싱)하여 어떤 API 엔드포인트에서 어떤 비즈니스 에러가 발생할 수 있는지 역추적(Deep Tracing)합니다. 이를 통해 Swagger(Springdoc) UI 문서의 응답(Responses) 그룹별 예시 드롭다운에 에러 코드를 자동으로 매핑하고 주입해 줍니다.

<br>

## 🚀 시작하기 전에: 요구사항 및 주의사항

이 라이브러리는 최신 기술 스택 환경을 기반으로 설계되었습니다. 도입 전 반드시 아래 버전을 확인해 주세요.

| 기술 스택 | 권장 버전 | 필수 사양 및 주의사항 |
| :--- | :--- | :--- |
| **Java (JDK)** | `Java 17` 이상 | JavaParser와 최신 문법 지원을 위해 JDK 17 이상이 필수적입니다. |
| **Spring Boot** | `Spring Boot 3.x` | `jakarta` 패키지 네임스페이스를 사용하는 Spring Boot 3점대 버전을 완벽히 지원합니다. |
| **Springdoc** | `Springdoc OpenAPI Starter 2.x` | 기존 `springfox` 계열이 아닌 최신 `springdoc-openapi-starter-webmvc-ui` 라이브러리와 연동됩니다. |

> #### ⚠️ 주의사항
> **정적 분석의 한계**: 이 라이브러리는 소스코드 파일(`.java`)을 직접 읽어서 구문 트리(AST)를 분석합니다. 따라서 실행 시점에 소스코드 파일의 경로가 올바르지 않거나, 소스코드가 없는 배포 환경(JAR 파일만 단독 실행되는 리눅스 운영 서버 등)에서는 로컬 소스 경로를 찾지 못할 수 있습니다. **주로 로컬 개발 환경(local 프로필)에서 Swagger 문서를 풍부하게 다듬고 검증하는 용도로 사용하시는 것을 적극 권장합니다.**

<br>

## 📦 설치 방법 (Installation)

### Gradle 설정

`build.gradle` 파일에 JitPack 리포지토리와 라이브러리 의존성을 추가합니다.

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' } // 🌟 반드시 이 줄이 추가되어야 합니다!
}

dependencies {
    // 🦖 Error-Jokbo 라이브러리 (JitPack 버전)
    implementation 'com.github.include-hoany-log:error-jokbo-spring-boot-starter:0.0.1'
}
```

<br>

## ⚙️ 설정 가이드 (Configuration)

`application.yml` (또는 `application.properties`) 파일에 라이브러리 동작을 위한 설정을 추가합니다. 각 항목의 의미는 다음과 같습니다.

```yaml
error-jokbo:
  # 1. 라이브러리 활성화 여부 (기본값: true)
  enabled: true

  # 2. 소스코드 정적 분석을 진행할 루트 경로 (기본값: "src/main/java")
  # 멀티 모듈이거나 표준 경로가 아니라면 실제 자바 소스 폴더 경로를 적어줍니다.
  source-path: "src/main/java"

  # 3. 프로젝트의 베이스 패키지 경로 (필수)
  # DeepErrorTracer가 외부 라이브러리 내부까지 파고들지 않고, 우리 프로젝트 코드만 추적하도록 제한하는 기준선이 됩니다.
  base-package: "com.example.board"

  # 4. 에러 코드를 관리하는 비즈니스 Enum 클래스의 전체 경로 (필수)
  # 내부에서 getMessage(), getStatusCode() 메서드를 호출하여 에러 세부 내용을 추출합니다.
  enum-class: "com.example.board.common.exception.ErrorCode"

  # 5. [선택] 프로젝트에서 실제 컨트롤러가 사용하는 커스텀 에러 응답 DTO 객체의 전체 경로
  # 생략할 경우: 자동으로 {"code": "에러코드", "message": "에러메시지"} 구조를 가진 기본 객체로 보여줍니다.
  # 설정할 경우: 개발자가 만든 DTO 구조를 리플렉션으로 재귀 분석하여 추가 필드(예: requestUri 등)까지 매핑해 줍니다.
  error-response-class: "com.example.board.common.dto.ErrorResponse"

  # 6. 소스코드 추적 시 비즈니스 예외로 인지하고 파싱할 커스텀 예외 클래스 목록 (필수, 리스트 형태)
  # 이 예외 객체가 new 키워드로 throw 되는 시점을 추적합니다.
  exception-classes:
    - "com.example.board.common.exception.BusinessException"
```

<br>

## 💻 사용 예시 및 코드 가이드 (Usage Example)

이 라이브러리가 프로젝트의 에러를 정상적으로 인식하게 하려면, 아래와 같은 일관된 예외 처리 규약(Contract) 구조를 가지면 됩니다.

### 1) 에러 코드 Enum 정의

에러 응답에 담길 고유 코드와 메시지, 그리고 HTTP 상태 코드를 반환하는 메서드(getMessage(), getStatusCode())가 포함된 Enum을 작성합니다.

```java
package com.example.board.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    POST_NOT_FOUND("404", "게시글을 찾을 수 없습니다."),
    INVALID_INPUT_VALUE("400", "올바르지 않은 입력값입니다.");

    private final String statusCode;
    private final String message;

    ErrorCode(String statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }

    public String getErrorCode() { return this.name(); }
}
```

### 2) 커스텀 예외(Exception) 정의

Enum으로 정의한 에러 코드를 담아 비즈니스 로직에서 사용할 커스텀 예외 클래스를 정의합니다. 이 예외 클래스 경로는 `application.yml`의 `exception-classes`에 명시되어야 합니다.

```java
package com.example.board.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final String code;
    private final String message;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getErrorCode();
        this.message = errorCode.getMessage();
    }
}
```

### 3) 커스텀 에러 응답 객체(DTO) 정의

프로젝트에서 전역 예외 처리기(`@RestControllerAdvice`) 등을 통해 클라이언트에게 최종 반환하는 응답 포맷 DTO입니다. 최소 규약으로 `code`와 `message`라는 이름의 필드를 가지고 있어야 하며, 추가 필드(`requestUri` 등)가 있어도 무방합니다.

```java
package com.example.board.common.dto;

import lombok.Getter;

@Getter
public class ErrorResponse {
    private String code;       // ✨ 계약 조건 필드: 에러 코드가 자동 매핑됩니다.
    private String message;    // ✨ 계약 조건 필드: 에러 메시지가 자동 매핑됩니다.
    private String requestUri; // 💡 커스텀 추가 필드: 타입별 기본 Mock 데이터("string")가 주입됩니다.
}
```

### 4) 비즈니스 로직(Controller / Service)에서의 사용

평소 개발하시는 것처럼 서비스 레이어나 컨트롤러 레이어에서 비즈니스 조건에 맞지 않을 때 예외를 던져줍니다.

```java
package com.example.board.web;

import com.example.board.common.exception.BusinessException;
import com.example.board.common.exception.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PostController {

    @GetMapping("/api/v1/posts/{postId}")
    public String getPost(@PathVariable Long postId) {
        // postId가 1L이 아니면 예외를 발생시키는 간단한 예시
        if (!postId.equals(1L)) {
            // 🦖 Error-Jokbo가 이 'throw new' 구문을 정적으로 분석하여
            // 현재 컨트롤러 메서드(/api/v1/posts/{postId})와 'POST_NOT_FOUND' 에러 코드를 매핑합니다!
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        return "게시글 조회 성공";
    }
}
```

<br>

## 🔮 화면 결과 및 확인 방법 (How it works in Swagger UI)

모든 코드가 작성되고 서버를 실행한 뒤, 브라우저를 열어 Swagger UI 화면으로 이동합니다.

**- 접속 주소:** `http://localhost:8080/swagger-ui/index.html`

가이드를 정상적으로 따랐다면, 별도의 Swagger 어노테이션(@ApiResponse 등)을 메서드 위에 수십 줄씩 적지 않았음에도 불구하고 아래와 같은 기능들이 자동으로 활성화됩니다.

- **HTTP 상태 코드별 자동 그룹화**: Enum의 `getStatusCode()` 결과에 따라 404 혹은 500 등의 응답 탭이 자동으로 생성됩니다.
- **Examples 드롭다운 리스트**: 해당 API에서 발생할 수 있는 비즈니스 에러 코드들이 드롭다운 리스트에 누적되어 노출됩니다.
- **재귀 필드 주입**: 개발자가 지정한 `error-response-class` 구조를 파고들어 `code`, `message` 외에 `requestUri: "string"` 같은 커스텀 레이아웃까지 완벽한 JSON 데이터 형태로 표현됩니다.
