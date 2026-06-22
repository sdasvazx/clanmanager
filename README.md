# Clan Manager

게임 클랜의 캐릭터, 공지, 활동 참석을 관리하는 Spring Boot + React 프로젝트입니다.

## 실행 전 준비

1. Java 21, MySQL 8 이상, Node.js 20 이상을 설치합니다.
2. MySQL에서 데이터베이스를 만듭니다.

```sql
CREATE DATABASE clanmanager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

3. `src/main/resources/application-example.yml`을 참고해 DB 접속 정보를 설정합니다. 실제 설정은 환경 변수로 주입하는 방식을 권장합니다.

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/clanmanager?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your-mysql-password"
```

`application.yaml`에는 비밀번호를 넣지 않습니다. 환경 변수가 없으면 로컬 기본 URL과 `root` 계정이 사용됩니다.

## 백엔드 실행

```powershell
.\gradlew.bat bootRun
```

서버는 `http://localhost:8080`에서 실행됩니다. 첫 실행 시 다음 활동 종류와 반복 일정이 자동 등록됩니다.

- 매일 13시/17시/21시 보스
- 토요일 22시 에노크, 마슈미드
- 수요일·금요일 21시 30분 정예던전보스
- 클랜임무, 수호, 쟁탈전

이미 존재하는 활동은 다시 만들지 않으므로 재실행해도 데이터가 중복되지 않습니다.

## 프론트엔드 실행

별도 터미널에서 실행합니다.

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

브라우저에서 `http://localhost:5173`을 엽니다. API 주소를 바꾸려면 `frontend/.env`에 `VITE_API_BASE_URL=http://localhost:8080/api` 형식으로 지정하세요.

## 테스트

```powershell
.\gradlew.bat test
```

테스트는 MySQL 대신 인메모리 H2 데이터베이스를 사용합니다.

## Postman API

가져오기(Import)로 `postman/Clan Manager.postman_collection.json`을 추가하세요. 컬렉션 변수 `baseUrl`의 기본값은 `http://localhost:8080/api`입니다.

|기능|메서드|경로|
|---|---|---|
|회원가입|POST|`/auth/register`|
|로그인|POST|`/auth/login`|
|회원 목록|GET|`/members`|
|회원 단건|GET|`/members/{memberId}`|
|회원 검색|GET|`/members/search?keyword=...`|
|내 정보/참여율|GET|`/members/{memberId}/my-info`|
|직급 변경|PATCH|`/members/{memberId}/rank?rank=...`|
|상태 변경|PATCH|`/members/{memberId}/status?status=...`|
|공지 목록|GET|`/notices`|
|공지 등록|POST|`/notices`|
|활동 종류 목록|GET|`/activities`|
|활동 일정 목록|GET|`/activities/schedules`|
|참석 기록|POST|`/attendances`|
|전체 참석 기록|GET|`/attendances`|
|회원별 참석 기록|GET|`/attendances/member/{memberId}`|
|날짜별 참석 기록|GET|`/attendances/date/{yyyy-MM-dd}`|

참여율은 `ATTENDED` 기록만 세며, 최다 참석 캐릭터의 횟수를 100%로 해서 계산합니다.

> 현재 로그인 API는 화면 식별용 결과만 반환하며 세션/JWT 인증은 아직 구현하지 않았습니다. 운영 환경에 배포하기 전에는 비밀번호 해싱과 권한 검증을 추가해야 합니다.
