# OCR 서버 배포 체크리스트

클랜 출석 스크린샷 인식은 별도 Python OCR 서비스가 담당합니다. 프론트는 더 이상 브라우저 안에서 Tesseract를 실행하지 않고, 아래 OCR 서버만 호출합니다.

## Railway OCR 서비스

Railway에서 `ocr-service` 폴더를 루트 디렉터리로 잡아 배포합니다.

필수 환경변수:

```text
OPENAI_API_KEY=sk-...
OCR_API_KEY=프론트와_공유할_임의의_긴_키
CORS_ALLOWED_ORIGINS=https://clanmanager-gray.vercel.app,http://localhost:5173
```

확인 주소:

```text
GET https://your-ocr-service.up.railway.app/health
```

정상이라면 `openai_key_configured: true`가 보여야 합니다.

## Vercel 프론트 환경변수

Vercel 프로젝트에는 아래 값을 설정합니다.

```text
VITE_API_BASE_URL=https://your-spring-backend.up.railway.app/api
VITE_OCR_API_BASE_URL=https://your-ocr-service.up.railway.app
VITE_OCR_API_KEY=Railway_OCR_API_KEY와_같은_값
VITE_PARTICIPATION_PERIOD_START=2026-07-08
```

환경변수 변경 후에는 Vercel에서 재배포해야 프론트에 반영됩니다.

## 자주 나는 문제

- OCR 결과가 비거나 실패하면 Railway OCR 서비스의 `OPENAI_API_KEY`를 먼저 확인합니다.
- 프론트에서 OCR 호출이 막히면 OCR 서비스의 `CORS_ALLOWED_ORIGINS`에 Vercel 주소가 들어있는지 확인합니다.
- `401 Invalid OCR API key`가 나오면 Railway `OCR_API_KEY`와 Vercel `VITE_OCR_API_KEY` 값이 같은지 확인합니다.
