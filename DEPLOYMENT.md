# 배포 가이드: 월 $10 안쪽 MVP

목표는 클랜원 약 200명이 빠르게 사용할 수 있는 최소 운영 환경입니다.

## 추천 구성

- 백엔드: Railway Hobby
- 데이터베이스: Railway MySQL
- 프론트엔드: Vercel 무료 또는 Railway 정적 배포
- 예상 비용: Railway 사용량 기준 대략 월 $5~$10 선에서 시작

## Railway 백엔드 배포

1. Railway에서 새 프로젝트를 만듭니다.
2. GitHub 저장소 `sdasvazx/clanmanager`를 연결합니다.
3. 백엔드 서비스는 루트 디렉터리를 사용합니다.
4. Railway가 `Dockerfile`을 감지해서 Spring Boot 앱을 빌드합니다.
5. MySQL 서비스를 추가합니다.
6. 백엔드 서비스 환경변수를 설정합니다.

```text
DB_URL=jdbc:mysql://${MYSQLHOST}:${MYSQLPORT}/${MYSQLDATABASE}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=${MYSQLUSER}
DB_PASSWORD=${MYSQLPASSWORD}
CORS_ALLOWED_ORIGINS=https://your-frontend-domain.vercel.app,http://localhost:5173
JPA_SHOW_SQL=false
JPA_DDL_AUTO=update
```

Railway의 MySQL 변수 이름은 프로젝트 생성 방식에 따라 다를 수 있으니, MySQL 서비스의 Variables 탭에서 실제 이름을 확인하세요.

## 프론트엔드 배포

Vercel을 쓰는 경우:

1. Vercel에서 GitHub 저장소를 연결합니다.
2. Root Directory를 `frontend`로 설정합니다.
3. Build Command는 `npm run build`입니다.
4. Output Directory는 `dist`입니다.
5. 환경변수를 설정합니다.

```text
VITE_API_BASE_URL=https://your-backend-domain.up.railway.app/api
```

프론트 배포 후에는 Railway 백엔드의 `CORS_ALLOWED_ORIGINS`에 프론트 주소를 반드시 추가해야 합니다.

## 운영 전 체크리스트

- [ ] Railway 백엔드 도메인 접속 확인
- [ ] Vercel 프론트 도메인 접속 확인
- [ ] 회원가입/로그인 확인
- [ ] 첫 가입자 또는 첫 회원이 운영자인지 확인
- [ ] 관리자 설정에서 운영자 권한 변경 확인
- [ ] 클랜금고 입금/분배/잔액수정 확인
- [ ] MySQL 데이터가 재배포 후에도 남아있는지 확인
- [ ] Railway 사용량 알림/예산 알림 설정

## 비용을 $10 안쪽으로 지키는 팁

- 처음에는 Railway Hobby 사용량 한도를 낮게 잡습니다.
- 로그를 너무 많이 찍지 않도록 `JPA_SHOW_SQL=false`를 유지합니다.
- 이미지 업로드 원본 저장은 아직 하지 않습니다. 현재 OCR은 브라우저 안에서 처리됩니다.
- DB 백업은 초반엔 수동 export라도 주기적으로 받습니다.

