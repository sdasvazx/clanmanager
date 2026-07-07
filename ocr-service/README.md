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

## API

```http
POST /api/attendance/ocr
Content-Type: multipart/form-data

file=<screenshot>
clan_hint=로망
```

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
