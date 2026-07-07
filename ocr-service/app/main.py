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


@app.get("/")
def read_root():
    return {"message": "Clan Manager OCR Service is running"}


@app.get("/health")
def health_check():
    return {
        "status": "ok",
        "openai_key_configured": bool(OPENAI_API_KEY)
    }


@app.post("/api/attendance/ocr")
async def gpt_vision_ocr(
    file: UploadFile = File(...),
    clan_hint: str = Form(None)
):
    if not OPENAI_API_KEY:
        print("❌ [CONFIG ERROR] OPENAI_API_KEY is missing")
        return {
            "results": [],
            "error": "OPENAI_API_KEY is missing on server variables"
        }

    try:
        image_bytes = await file.read()

        if not image_bytes:
            return {
                "results": [],
                "error": "Uploaded image file is empty"
            }

        base64_image = base64.b64encode(image_bytes).decode("utf-8")

    except Exception as e:
        print(f"❌ [IMAGE ENCODE ERROR]: {str(e)}")
        return {
            "results": [],
            "error": "Image encoding failed"
        }

    prompt = """
이 사진은 게임 길드의 출석 체크 명단이야.

1번부터 10번 파티까지 순서대로 캐릭터들의 '닉네임'만 추출해서 JSON으로 응답해줘.

[필수 규칙]
1. '+' 표시가 있는 빈 칸은 완전히 무시하고 제외해.
2. 'Lv.107' 같은 레벨 숫자는 절대 포함하지 마.
3. 무기 아이콘, 직업 아이콘, 장비 아이콘은 절대 포함하지 마.
4. 오직 캐릭터 닉네임만 추출해.
5. 닉네임을 임의로 수정하거나 추측하지 마.
6. 잘 안 보이는 닉네임은 가능한 범위에서 그대로 읽어.
7. 설명문, 마크다운, ```json 코드블록은 절대 붙이지 마.
8. 반드시 순수 JSON만 반환해.

[출력 포맷]
{
  "results": [
    {
      "party": 1,
      "nicknames": ["닉네임1", "닉네임2", "닉네임3"]
    },
    {
      "party": 2,
      "nicknames": ["닉네임1", "닉네임2"]
    }
  ]
}
"""

    if clan_hint:
        prompt += f"\n\n[참고 클랜명 또는 힌트]\n{clan_hint}\n"

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
        "temperature": 0
    }

    try:
        req = urllib.request.Request(
            url=url,
            data=json.dumps(payload).encode("utf-8"),
            headers=headers,
            method="POST"
        )

        with urllib.request.urlopen(req, timeout=60) as res:
            response_body = res.read().decode("utf-8")
            response_data = json.loads(response_body)

        result_text = response_data["choices"][0]["message"]["content"].strip()

        print(f"📥 [GPT RAW RESPONSE]: {result_text}")

        if result_text.startswith("```"):
            result_text = re.sub(r"^```(?:json)?\s*", "", result_text)
            result_text = re.sub(r"\s*```$", "", result_text)

        parsed = json.loads(result_text)

        results = parsed.get("results", [])

        if not isinstance(results, list):
            return {
                "results": [],
                "error": "Invalid results format from GPT"
            }

        return {
            "results": results
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
            "error": "OpenAI API connection failed",
            "detail": str(e)
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
            "error": "Unexpected server error",
            "detail": str(e)
        }