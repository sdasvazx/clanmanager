import os
import base64
import json
import re
import urllib.request
import urllib.error

from fastapi import FastAPI, File, UploadFile, Form
from fastapi.middleware.cors import CORSMiddleware

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
    return {
        "status": "ok",
        "openai_key_configured": bool(OPENAI_API_KEY)
    }


@app.get("/")
def read_root():
    return {
        "message": "Clan Manager OCR Service is running"
    }


@app.post("/api/attendance/ocr")
async def gpt_vision_ocr(
    file: UploadFile = File(...),
    clan_hint: str = Form(None)
):
    if not OPENAI_API_KEY:
        print("❌ [CONFIG ERROR] OPENAI_API_KEY environment variable is missing!")
        return {
            "results": [],
            "error": "OPENAI_API_KEY is missing on server variables"
        }

    try:
        image_bytes = await file.read()
        base64_image = base64.b64encode(image_bytes).decode("utf-8")
    except Exception as e:
        print(f"❌ [IMAGE ENCODE ERROR] Failed to encode image: {str(e)}")
        return {
            "results": [],
            "error": "Image encoding failed"
        }

    prompt = """
이 사진은 게임 길드의 출석 체크 명단이야.

1번부터 10번 파티까지 순서대로 캐릭터들의 '닉네임'만 추출해서 JSON 배열로 응답해줘.

[필수 규칙]
1. '+' 표시가 있는 빈 칸은 완전히 무시하고 제외해.
2. 'Lv.107' 같은 레벨 숫자와 무기 아이콘은 절대 포함하지 마.
3. 오직 닉네임만 추출해.
4. 다른 설명이나 마크다운(```json)은 절대 붙이지 마.
5. 반드시 순수 JSON만 반환해.

[출력 포맷]
{
  "results": [
    {"party": 1, "nicknames": ["귀신나인바뺑", "귀신재림죽뺑", "귀신몽둥이"]},
    {"party": 2, "nicknames": ["귀신천신강림I", "귀신임사탕"]}
  ]
}
"""

    url = "https://api.openai.com/v1/chat/completions"

    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {OPENAI_API_KEY}"
    }

    payload = {
        "model": "gpt-4o",
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": prompt
                    },
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": f"data:image/jpeg;base64,{base64_image}"
                        }
                    }
                ]
            }
        ],
        "max_tokens": 1500,
        "temperature": 0.0
    }

    try:
        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers=headers,
            method="POST"
        )

        with urllib.request.urlopen(req, timeout=60) as res:
            response_data = json.loads(res.read().decode("utf-8"))

        result_text = response_data["choices"][0]["message"]["content"].strip()

        print(f"📥 [GPT RAW RESPONSE]: {result_text}")

        # 혹시 GPT가 ```json 코드블록으로 감싸서 보내면 제거
        if result_text.startswith("```"):
            result_text = re.sub(r"^```(?:json)?\s*", "", result_text)
            result_text = re.sub(r"\s*```$", "", result_text)

        parsed = json.loads(result_text)

        return {
            "results": parsed.get("results", [])
        }

    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8")
        print(f"❌ [OPENAI HTTP ERROR]: {error_body}")

        return {
            "results": [],
            "error": "OpenAI API request failed",
            "detail": error_body
        }

    except urllib.error.URLError as e:
        print(f"❌ [OPENAI URL ERROR]: {str(e)}")

        return {
            "results": [],
            "error": "OpenAI API connection failed"
        }

    except json.JSONDecodeError as e:
        print(f"❌ [JSON PARSE ERROR]: {str(e)}")

        return {
            "results": [],
            "error": "GPT response JSON parsing failed"
        }

    except Exception as e:
        print(f"❌ [SERVER ERROR]: {str(e)}")

        return {
            "results": [],
            "error": "Unexpected server error"
        }