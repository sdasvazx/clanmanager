import base64
import json
import os

from fastapi import FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from openai import OpenAI


OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
OCR_API_KEY = os.getenv("OCR_API_KEY") or os.getenv("OCR_SERVICE_API_KEY")

app = FastAPI(title="Clan Manager AI OCR Service", version="1.1.0")

allowed_origins = [
    origin.strip()
    for origin in (
        os.getenv("ALLOWED_ORIGINS")
        or os.getenv("CORS_ORIGINS")
        or os.getenv("CORS_ALLOWED_ORIGINS")
        or "http://localhost:5173,http://localhost:3000,https://clanmanager-gray.vercel.app"
    ).split(",")
    if origin.strip()
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins or ["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


SYSTEM_PROMPT = """
당신은 게임 파티 명단 스크린샷에서 캐릭터 닉네임만 추출하는 OCR 전문가입니다.

규칙:
1. 이미지를 1번 파티부터 10번 파티까지 순서대로 확인합니다.
2. 각 파티는 최대 5명입니다.
3. 캐릭터 닉네임만 추출합니다.
4. 빈 슬롯, + 버튼, 파티 번호, 왕관, 무기/직업 아이콘은 무시합니다.
5. Lv. 107 같은 레벨 텍스트와 숫자는 절대 포함하지 않습니다.
6. 한글 닉네임은 보이는 그대로 보존합니다.
7. 영어 닉네임은 대소문자를 보이는 그대로 보존합니다. 예: MiuMiuMin, Skia, WANTED
8. 보이지 않는 인원은 만들지 않습니다.
9. 설명, 마크다운, 코드블록 없이 JSON 객체만 반환합니다.
10. 한 칸에 사람이 있으면 해당 슬롯의 닉네임을 최대한 읽고, 빈 원형 칸이나 + 표시는 제외합니다.
11. 댕히, 복댕댕이, 쎄복이, VH여포, VH봉가, VH야민처럼 뭉개지기 쉬운 닉네임은 획을 끝까지 확인해서 보이는 그대로 추출합니다.
12. 한글과 영어가 섞인 닉네임은 공백이나 특수문자를 임의로 추가하지 않습니다.

반드시 아래 형식으로만 응답하세요:
{
  "results": [
    { "party": 1, "nicknames": ["Name1", "Name2"] },
    { "party": 2, "nicknames": ["Name3"] }
  ]
}
""".strip()


def verify_ocr_key(x_ocr_api_key: str | None) -> None:
    if OCR_API_KEY and x_ocr_api_key != OCR_API_KEY:
        raise HTTPException(status_code=401, detail="Invalid OCR API key.")


def build_user_prompt(clan_hint: str | None) -> str:
    hint = f'\n기준 클랜 힌트: "{clan_hint}"' if clan_hint else ""
    return f"""
이 게임 파티 명단 스크린샷을 읽고 파티별 닉네임 목록만 JSON으로 반환하세요.
레벨, 아이콘, 빈칸, + 버튼은 제외하세요.
{hint}
""".strip()


def normalize_payload(payload: dict) -> dict:
    raw_results = payload.get("results")
    if not isinstance(raw_results, list):
        raise HTTPException(status_code=502, detail="AI response did not contain a results array.")

    results: list[dict] = []
    for row in raw_results:
        if not isinstance(row, dict):
            continue
        try:
            party = int(row.get("party"))
        except (TypeError, ValueError):
            continue
        if party < 1 or party > 10:
            continue

        nicknames = row.get("nicknames")
        if not isinstance(nicknames, list):
            continue

        cleaned: list[str] = []
        seen: set[str] = set()
        for nickname in nicknames:
            if not isinstance(nickname, str):
                continue
            name = nickname.strip()
            if not name or name.lower().startswith("lv"):
                continue
            key = name.casefold()
            if key in seen:
                continue
            seen.add(key)
            cleaned.append(name)

        results.append({"party": party, "nicknames": cleaned})

    results.sort(key=lambda item: item["party"])
    return {"results": results}


@app.get("/")
def read_root() -> dict:
    return {"message": "Clan Manager OCR Service is running"}


@app.get("/health")
def health_check() -> dict:
    return {
        "status": "ok",
        "openai_key_configured": bool(OPENAI_API_KEY),
        "ocr_api_key_required": bool(OCR_API_KEY),
    }


@app.post("/api/attendance/ocr")
async def gpt_vision_ocr(
    file: UploadFile = File(...),
    clan_hint: str | None = Form(None),
    x_ocr_api_key: str | None = Header(None, alias="X-OCR-API-Key"),
) -> dict:
    verify_ocr_key(x_ocr_api_key)

    if not OPENAI_API_KEY:
        raise HTTPException(status_code=500, detail="OPENAI_API_KEY is not configured.")

    image_bytes = await file.read()
    if not image_bytes:
        raise HTTPException(status_code=400, detail="Uploaded image file is empty.")

    content_type = file.content_type or "image/jpeg"
    base64_image = base64.b64encode(image_bytes).decode("utf-8")
    image_url = f"data:{content_type};base64,{base64_image}"

    client = OpenAI(api_key=OPENAI_API_KEY)

    try:
        completion = client.chat.completions.create(
            model="gpt-4o",
            temperature=0.0,
            response_format={"type": "json_object"},
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": build_user_prompt(clan_hint)},
                        {"type": "image_url", "image_url": {"url": image_url}},
                    ],
                },
            ],
        )
    except Exception as exc:
        print(f"❌ [OPENAI VISION ERROR] {str(exc)}")
        raise HTTPException(status_code=502, detail=f"OpenAI Vision request failed: {str(exc)}") from exc

    content = completion.choices[0].message.content
    if not content:
        raise HTTPException(status_code=502, detail="OpenAI Vision returned an empty response.")

    try:
        payload = json.loads(content)
    except json.JSONDecodeError as exc:
        print(f"❌ [OPENAI JSON ERROR] {content}")
        raise HTTPException(status_code=502, detail="OpenAI Vision returned invalid JSON.") from exc

    return normalize_payload(payload)
