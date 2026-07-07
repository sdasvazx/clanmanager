import base64
import json
import os

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from openai import OpenAI


app = FastAPI(title="Clan Manager OCR Service", version="1.0.0")

allowed_origins = [
    origin.strip()
    for origin in (
        os.getenv("ALLOWED_ORIGINS")
        or os.getenv("CORS_ORIGINS")
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
You are a Korean game roster OCR expert.
Your only job is to extract character nicknames from a game party roster screenshot.

Rules:
1. Scan the image from party 1 through party 10.
2. Each party may contain up to 5 character slots.
3. Extract only character nicknames.
4. Ignore empty slots, blank circles, plus signs, weapon/class icons, crowns, party number badges, and all Lv./level text.
5. Never include strings like "Lv. 107", "Lv", numbers from levels, weapon names, or UI labels.
6. Preserve Korean characters exactly.
7. Preserve English uppercase/lowercase exactly, e.g. "MiuMiuMin", "Skia", "WANTED".
8. If a nickname is visually unclear, infer the most likely nickname from the visible text, but do not invent extra people.
9. Return JSON only.

Required JSON schema:
{
  "results": [
    { "party": 1, "nicknames": ["Name1", "Name2"] },
    { "party": 2, "nicknames": ["Name3"] }
  ]
}
""".strip()


def build_user_prompt(clan_hint: str | None) -> str:
    hint_text = f'\nThe user says this screenshot is mainly for clan "{clan_hint}". Use that only as context for reading names.' if clan_hint else ""
    return f"""
Read this game party roster screenshot carefully.
Extract nicknames by party number.
Ignore all levels, icons, empty slots, plus signs, and non-name UI text.
Return only the strict JSON object with a "results" array.
{hint_text}
""".strip()


def normalize_ai_payload(payload: dict) -> dict:
    raw_results = payload.get("results")
    if not isinstance(raw_results, list):
        raise HTTPException(status_code=502, detail="AI response did not contain a results array.")

    normalized_results: list[dict] = []
    for item in raw_results:
        if not isinstance(item, dict):
            continue

        try:
            party = int(item.get("party"))
        except (TypeError, ValueError):
            continue

        if party < 1 or party > 10:
            continue

        nicknames = item.get("nicknames")
        if not isinstance(nicknames, list):
            continue

        cleaned_names: list[str] = []
        for nickname in nicknames:
            if not isinstance(nickname, str):
                continue
            name = nickname.strip()
            if not name:
                continue
            if name.lower().startswith("lv"):
                continue
            cleaned_names.append(name)

        normalized_results.append({
            "party": party,
            "nicknames": cleaned_names,
        })

    normalized_results.sort(key=lambda result: result["party"])
    return {"results": normalized_results}


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/api/attendance/ocr")
async def recognize_attendance(
    file: UploadFile = File(...),
    clan_hint: str | None = Form(None),
) -> dict:
    if not os.getenv("OPENAI_API_KEY"):
        raise HTTPException(status_code=500, detail="OPENAI_API_KEY is not configured.")

    image_bytes = await file.read()
    if not image_bytes:
        raise HTTPException(status_code=400, detail="No image file was uploaded.")

    content_type = file.content_type or "image/png"
    image_base64 = base64.b64encode(image_bytes).decode("utf-8")
    image_url = f"data:{content_type};base64,{image_base64}"

    client = OpenAI()

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

    return normalize_ai_payload(payload)
