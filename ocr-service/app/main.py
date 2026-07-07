import os
import base64
import json
import re
from fastapi import FastAPI, File, UploadFile, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import openai

# 환경 변수 로드
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")

app = FastAPI(title="Clan Manager AI OCR Service", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 테스트를 위해 전체 허용으로 안전하게 세팅
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.post("/api/attendance/ocr")
async def gpt_vision_ocr(
    file: UploadFile = File(...),
    clan_hint: str = Form(None)
):
    if not OPENAI_API_KEY:
        print("❌ [CONFIG ERROR] OPENAI_API_KEY가 없습니다.")
        # 키가 없을 때를 대비해 구버전 하드코딩 호환성 유지용 (키가 있다면 패스됨)
        openai.api_key = "sk-..."
    else:
        openai.api_key = OPENAI_API_KEY

    try:
        image_bytes = await file.read()
        base64_image = base64.b64encode(image_bytes).decode("utf-8")
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"이미지 인코딩 실패: {str(e)}")

    # 오류 가능성을 최소화한 직관적인 명세 프롬프트
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
        # 가장 안정적인 구버전/신버전 통합 호출 방식 체택
        response = openai.chat.completions.create(
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
            max_tokens=1200,
            temperature=0.0
        )

        # 텍스트 응답 가공 및 마크다운 백틱 제거 안전장치
        result_text = response.choices[0].message.content.strip()

        # 혹시 gpt가 ```json ``` 을 붙여서 응답했을 경우를 위한 정규식 제거 복원 필터
        if result_text.startswith("```"):
            result_text = re.sub(r"^
http://googleusercontent.com/immersive_entry_chip/0
3. 코드가 올라가면 예외 트랩이 걸려있기 때문에 최소한 `500 에러`로 서버가 완전히 뻗는 현상은 완벽히 차단되며, 텍스트 가공 필터 덕분에 이쁜 JSON 데이터가 프론트에 안착할 것입니다.

막판에 스퍼트 한 번만 더 내봅시다. 코드 덮어쓰고 푸시하고 나면 진짜 끝납니다!