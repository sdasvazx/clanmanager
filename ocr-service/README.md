# Clan Manager OCR Service

게임 파티 명단 스크린샷을 OpenAI Vision으로 읽어 파티별 닉네임 목록을 반환하는 FastAPI 서비스입니다.

## 실행

```bash
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8090 --reload
```

## 환경변수

```bash
OPENAI_API_KEY=sk-...

# 선택이지만 운영 배포에서는 반드시 설정 권장
OCR_API_KEY=랜덤하게_긴_비밀키

# 선택
CORS_ORIGINS=https://clanmanager-gray.vercel.app,http://localhost:5173
```

`OCR_API_KEY`를 설정하면 요청 헤더에 같은 값을 넣어야 합니다.

```http
X-OCR-API-Key: 랜덤하게_긴_비밀키
```

프론트 Vercel 환경변수:

```bash
VITE_OCR_API_BASE_URL=https://your-ocr-service.up.railway.app
VITE_OCR_API_KEY=랜덤하게_긴_비밀키
```

## API

```http
POST /api/attendance/ocr
Content-Type: multipart/form-data

file=<screenshot>
clan_hint=로망
```

응답:

```json
{
  "results": [
    { "party": 1, "nicknames": ["WANTED", "코아"] },
    { "party": 2, "nicknames": ["댕히"] }
  ]
}
```
