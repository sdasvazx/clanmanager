# Clan Manager OCR Service

게임 파티 명단 스크린샷을 받아 OpenCV 전처리 + Tesseract Bounding Box 기반으로 닉네임을 추출하는 FastAPI 서비스입니다.

## 설치

1. Python 패키지 설치

```bash
pip install -r requirements.txt
```

2. Tesseract OCR 설치

Windows라면 Tesseract 설치 후 `kor.traineddata`, `eng.traineddata`가 필요합니다.

환경변수로 실행 파일 위치를 지정할 수 있습니다.

```bash
set TESSERACT_CMD=C:\Program Files\Tesseract-OCR\tesseract.exe
```

## 실행

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8090 --reload
```

## Railway 배포

OCR 서버는 기존 Spring 백엔드와 별도 Railway 서비스로 배포하는 것을 권장합니다.

1. Railway에서 현재 GitHub 저장소 `sdasvazx/clanmanager`를 새 서비스로 추가
2. 서비스 설정에서 Root Directory를 `ocr-service`로 지정
3. Dockerfile 자동 감지 또는 `ocr-service/Dockerfile` 사용
4. 배포 후 Public Networking 도메인을 생성
5. 생성된 주소의 `/health`가 `{"status":"ok"}`를 반환하면 정상

권장 환경변수:

```bash
OCR_LANG=kor+eng
OCR_WORKERS=2
OCR_CONFIRMED_SCORE=0.86
OCR_CLAN_BOOST=0.15
CORS_ORIGINS=https://clanmanager-gray.vercel.app,http://localhost:5173
```

Railway가 제공하는 `PORT` 환경변수는 Docker CMD에서 자동 사용합니다.

## API

```http
POST /api/attendance/ocr
Content-Type: multipart/form-data

file=<screenshot>
clan_hint=로망
members_json=[{"characterName":"WANTED","clanName":"로망"}]
```

프론트엔드에서 이 서버를 사용하려면 `frontend/.env`에 아래 값을 추가합니다.

```bash
VITE_OCR_API_BASE_URL=http://localhost:8090
```

운영 배포에서는 Vercel 프로젝트 환경변수에 아래처럼 OCR 서버 배포 주소를 넣습니다.

```bash
VITE_OCR_API_BASE_URL=https://your-ocr-service.up.railway.app
```

값이 비어 있거나 서버 호출이 실패하면 프론트는 기존 브라우저 OCR로 자동 전환합니다.

응답 예시:

```json
{
  "confirmed": [
    {
      "raw": "WANTED",
      "name": "WANTED",
      "clan": "로망",
      "score": 1.15,
      "slot": 3,
      "party": 1
    }
  ],
  "candidates": [
    {
      "raw": "복덩덩이",
      "slot": 5,
      "party": 2,
      "suggestions": [
        { "name": "복댕댕이", "clan": "귀신", "score": 0.78 }
      ]
    }
  ],
  "debug": {
    "anchors": 12,
    "raw_names": 12
  }
}
```
