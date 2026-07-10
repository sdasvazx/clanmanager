# 운영 DB 안전 설정

Spring Boot 설정의 `spring.jpa.hibernate.ddl-auto`는 `JPA_DDL_AUTO` 환경변수로 제어합니다.

```text
JPA_DDL_AUTO=update
```

초기 배포처럼 테이블 자동 생성이 필요할 때는 `update`를 사용합니다.

클랜원들이 실제로 사용하기 시작한 뒤에는 의도치 않은 DB 구조 변경을 막기 위해 아래처럼 바꾸는 것을 권장합니다.

```text
JPA_DDL_AUTO=validate
```

`validate`는 애플리케이션이 DB 구조를 자동 변경하지 않고, 현재 엔티티와 DB 스키마가 맞는지만 검사합니다.
