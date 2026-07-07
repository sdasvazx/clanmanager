if result_text.startswith("```"):
            result_text = re.sub(r"^
http://googleusercontent.com/immersive_entry_chip/0
여기가 아니라, 그 아래 **`parsed_json = json.loads(result_text)`** 부분의 들여쓰기(Indentation)가 완전히 바깥으로 삐져나와 있군요!

파이썬에서는 `try:` 블록 안에 있어야 할 코드들이 같은 칸 수만큼 들여쓰기가 되어 있어야 하는데, 지금 캡처 화면에서는 `parsed_json`과 `return` 줄이 `try`와 같은 라인(맨 앞으로)에 맞춰져 있어서 **문법 에러(IndentationError)**가 발생하는 상태가 맞습니다. 제가 드린 코드 양식과 다르게 들어가서 헷갈리셨을 것 같습니다.

---

### 🛠️ 왜 이렇게 되었는지 시각적으로 보여드릴게요

현재 파일 상태는 아래와 같이 `try` 바깥으로 튕겨 나가 있습니다.

```python
    # 5. GPT-4o API 호출 및 예외 제어
    try:
        response = client.chat.completions.create(
            # ... (중략) ...
        )

        result_text = response.choices[0].message.content.strip()
        print(f"📥 [GPT RAW RESPONSE]: {result_text}")

        if result_text.startswith("```"):
            result_text = re.sub(r"^
http://googleusercontent.com/immersive_entry_chip/1

---

### 🎯 완벽하게 해결된 정상 코드 (복사용)

이 들여쓰기 칸 수를 정확하게 안쪽으로 맞춰주셔야 에러가 안 납니다. 아래 코드는 `parsed_json` 줄까지 전부 `try` 내부 칸 수로 올바르게 정렬해 둔 코드입니다.

기존 내용을 싹 지우시고, 이 코드를 복사해서 붙여넣으시면 괄호나 들여쓰기 문제없이 한 번에 통과됩니다!

```python
import os
import base64
import json
import re
from fastapi import FastAPI, File, UploadFile, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from openai import OpenAI

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")

app = FastAPI(title="Clan Manager AI OCR Service", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/health")
def health_check():
    return {"status": "ok", "openai_key_configured": bool(OPENAI_API_KEY)}

@app.get("/")
def read_root():
    return {"message": "Clan Manager OCR Service is running"}

@app.post("/api/attendance/ocr")
async def gpt_vision_ocr(
    file: UploadFile = File(...),
    clan_hint: str = Form(None)
):
    if not OPENAI_API_KEY:
        print("❌ [CONFIG ERROR] OPENAI_API_KEY environment variable is missing!")
        return {"results": [], "error": "OPENAI_API_KEY is missing on server variables"}

    try:
        image_bytes = await file.read()
        base64_image = base64.b64encode(image_bytes).decode("utf-8")
    except Exception as e:
        print(f"❌ [IMAGE ENCODE ERROR] Failed to encode image: {str(e)}")
        return {"results": [], "error": "Image encoding failed"}

    try:
        client = OpenAI(api_key=OPENAI_API_KEY)
    except Exception as e:
        print(f"❌ [OPENAI INIT ERROR] Client instantiation failed: {str(e)}")
        return {"results": [], "error": "OpenAI client initialization failed"}

    prompt = """
    이 사진은 게임 길드의 출석 체크 명단이야.
    1번부터 10번 파티까지 순서대로 캐릭터들의 '닉네임'만 추출해서 JSON 배열로 응답해줘.

    [필수 규칙]
    1. '+' 표시가 있는 빈 칸은 완전히 무시하고 제외해.
    2. 'Lv.107' 같은 레벨 숫자와 무기 아이콘은 절대 포함하지 말고 오직 닉네임만 추출해.
    3. 다른 설명이나 마크다운(```json)은 절대 붙이지 말고, 오직 아래 구조의 순수한 JSON 데이터만 반환해.

    [출력 포맷]
    {
      "results": [
        {"party": 1, "nicknames": ["귀신나인바뺑", "귀신재림죽뺑", "귀신몽둥이"]},
        {"party": 2, "nicknames": ["귀신천신강림I", "귀신임사탕"]}
      ]
    }
    """

    try:
        response = client.chat.completions.create(
            model="gpt-4o",
            messages=[
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": prompt},
                        {
                            "type": "image_url",
                            "image_url": {"url": f"data:image/jpeg;base64,{base64_image}"}
                        }
                    ]
                }
            ],
            max_tokens=1500,
            temperature=0.0
        )

        result_text = response.choices[0].message.content.strip()
        print(f"📥 [GPT RAW RESPONSE]: {result_text}")

        if result_text.startswith("```"):
            result_text = re.sub(r"^
http://googleusercontent.com/immersive_entry_chip/2