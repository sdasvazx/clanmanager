import asyncio
import json
import difflib
import os
import re
import tempfile
import unicodedata
from concurrent.futures import ProcessPoolExecutor
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import cv2
import numpy as np
import pytesseract
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from PIL import Image


TESSERACT_CMD = os.getenv("TESSERACT_CMD")
if TESSERACT_CMD:
    pytesseract.pytesseract.tesseract_cmd = TESSERACT_CMD

OCR_LANG = os.getenv("OCR_LANG", "kor+eng")
MAX_WORKERS = int(os.getenv("OCR_WORKERS", "2"))
CONFIRMED_SCORE = float(os.getenv("OCR_CONFIRMED_SCORE", "0.86"))
CLAN_BOOST = float(os.getenv("OCR_CLAN_BOOST", "0.15"))

executor = ProcessPoolExecutor(max_workers=MAX_WORKERS)

app = FastAPI(title="Clan Manager OCR Service", version="0.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=os.getenv("CORS_ORIGINS", "*").split(","),
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@dataclass(frozen=True)
class ClanMember:
    name: str
    clan: str


# TODO: 실제 운영에서는 Spring API/DB에서 이 목록을 받아오도록 교체하면 됩니다.
FAKE_CLAN_MEMBERS: list[ClanMember] = [
    ClanMember("귀신나인바뺑", "귀신"),
    ClanMember("귀신재림쭈뺑", "귀신"),
    ClanMember("귀신몽둥이", "귀신"),
    ClanMember("귀신마왕I", "귀신"),
    ClanMember("또랑프", "귀신"),
    ClanMember("귀신천신강림", "귀신"),
    ClanMember("귀신임사탕", "귀신"),
    ClanMember("귀신다야땅땅", "귀신"),
    ClanMember("귀신가을", "귀신"),
    ClanMember("복댕댕이", "귀신"),
    ClanMember("댕히", "로망"),
    ClanMember("쎄복이", "귀신"),
    ClanMember("쌔뽁이", "귀신"),
    ClanMember("4악한키티", "귀신"),
    ClanMember("귀신쮸쮸빵빵", "귀신"),
    ClanMember("귀신술", "귀신"),
    ClanMember("사마귀44", "귀신"),
    ClanMember("허리피자", "귀신"),
    ClanMember("WANTED", "로망"),
    ClanMember("MiuMiuMin", "로망"),
    ClanMember("Skia", "귀신"),
    ClanMember("귀신GRAY", "귀신"),
    ClanMember("검고냥", "로망"),
    ClanMember("VH유비", "로망"),
    ClanMember("VH아밍", "로망"),
]


@dataclass
class OcrBox:
    text: str
    x1: int
    y1: int
    x2: int
    y2: int
    conf: float

    @property
    def cx(self) -> float:
        return (self.x1 + self.x2) / 2

    @property
    def cy(self) -> float:
        return (self.y1 + self.y2) / 2

    @property
    def width(self) -> int:
        return max(1, self.x2 - self.x1)

    @property
    def height(self) -> int:
        return max(1, self.y2 - self.y1)


@dataclass
class RawName:
    text: str
    x1: int
    y1: int
    x2: int
    y2: int
    level: str
    slot: int | None
    party: int | None


def normalize_text(value: str) -> str:
    value = unicodedata.normalize("NFKC", value or "")
    value = re.sub(r"\s+", "", value)
    value = value.replace("|", "I").replace("１", "1").replace("Ｉ", "I")
    return re.sub(r"[^0-9A-Za-z가-힣]", "", value)


def normalize_for_similarity(value: str) -> str:
    text = normalize_text(value).lower()
    replacements = {
        "2": "z",
        "0": "o",
        "ㅣ": "i",
        "대": "댕",
        "덩": "댕",
        "댱": "댕",
        "더": "댕",
        "복대대이": "복댕댕이",
        "복덩덩이": "복댕댕이",
        "복댱댱이": "복댕댕이",
        "세복이": "쎄복이",
        "새복이": "쎄복이",
        "새폭이": "쎄복이",
        "vh": "vh",
    }
    for wrong, right in replacements.items():
        text = text.replace(wrong, right)
    return text


def similarity(a: str, b: str) -> float:
    ak = normalize_for_similarity(a)
    bk = normalize_for_similarity(b)
    if not ak or not bk:
        return 0.0
    if ak == bk:
        return 1.0
    if ak in bk or bk in ak:
        return 0.92 if min(len(ak), len(bk)) >= 2 else 0.75
    return difflib.SequenceMatcher(None, ak, bk).ratio()


def parse_members_json(members_json: str | None) -> list[ClanMember]:
    if not members_json:
        return FAKE_CLAN_MEMBERS
    try:
        rows = json.loads(members_json)
    except json.JSONDecodeError:
        return FAKE_CLAN_MEMBERS
    source_rows = rows if isinstance(rows, list) else []
    members: list[ClanMember] = []
    for row in source_rows:
        if not isinstance(row, dict):
            continue
        name = str(row.get("characterName") or row.get("name") or "").strip()
        clan = str(row.get("guildName") or row.get("clanName") or row.get("clan") or "미분류").strip()
        if name:
            members.append(ClanMember(name=name, clan=clan or "미분류"))
    return members or FAKE_CLAN_MEMBERS


def match_clan_member(raw_name: str, clan_hint: str | None = None, limit: int = 5, members: list[ClanMember] | None = None) -> dict[str, Any]:
    """문자열 유사도 + 클랜 가중치 기반 매칭.

    Lv. 앵커 위에서 글자 형체가 나온 슬롯은 점수가 낮아도 best 1명을 후보로 보장한다.
    """
    clan_hint = (clan_hint or "").strip()
    member_rows = members or FAKE_CLAN_MEMBERS
    ranked = []
    for member in member_rows:
        base_score = similarity(raw_name, member.name)
        boosted_score = base_score + (CLAN_BOOST if clan_hint and member.clan == clan_hint else 0)
        ranked.append({
            "name": member.name,
            "clan": member.clan,
            "score": round(min(boosted_score, 1.15), 4),
            "base_score": round(base_score, 4),
        })
    ranked.sort(key=lambda row: (row["score"], row["base_score"], row["name"]), reverse=True)
    best = ranked[0] if ranked else None
    return {
        "raw": raw_name,
        "confirmed": bool(best and best["score"] >= CONFIRMED_SCORE),
        "best": best,
        "suggestions": ranked[:limit],
    }


def preprocess_image(image_bytes: bytes) -> np.ndarray:
    image_array = np.frombuffer(image_bytes, np.uint8)
    image = cv2.imdecode(image_array, cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError("이미지 파일을 읽을 수 없습니다.")

    scaled = cv2.resize(image, None, fx=2.5, fy=2.5, interpolation=cv2.INTER_CUBIC)
    gray = cv2.cvtColor(scaled, cv2.COLOR_BGR2GRAY)

    denoised = cv2.fastNlMeansDenoising(gray, None, h=8, templateWindowSize=7, searchWindowSize=21)
    blur = cv2.GaussianBlur(denoised, (0, 0), 1.2)
    sharpened = cv2.addWeighted(denoised, 1.75, blur, -0.75, 0)

    # 흰 글씨/밝은 글씨를 살리되 배경 노이즈를 줄인다.
    binary = cv2.adaptiveThreshold(
        sharpened,
        255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY,
        31,
        7,
    )
    kernel = np.ones((1, 1), np.uint8)
    return cv2.morphologyEx(binary, cv2.MORPH_OPEN, kernel)


def run_tesseract_boxes(image: np.ndarray) -> list[OcrBox]:
    pil_image = Image.fromarray(image)
    config = "--oem 1 --psm 6 -c preserve_interword_spaces=1"
    data = pytesseract.image_to_data(
        pil_image,
        lang=OCR_LANG,
        config=config,
        output_type=pytesseract.Output.DICT,
    )
    boxes: list[OcrBox] = []
    for index, raw_text in enumerate(data.get("text", [])):
        text = normalize_text(raw_text)
        if not text:
            continue
        try:
            conf = float(data["conf"][index])
        except (ValueError, TypeError):
            conf = -1.0
        if conf < 0:
            continue
        x = int(data["left"][index])
        y = int(data["top"][index])
        w = int(data["width"][index])
        h = int(data["height"][index])
        boxes.append(OcrBox(text=text, x1=x, y1=y, x2=x + w, y2=y + h, conf=conf))
    return boxes


def is_level_box(box: OcrBox) -> bool:
    text = normalize_text(box.text)
    level_match = re.match(r"^(Lv|LV|Iv|L[vV])?1[0-1][0-9]$", text, re.IGNORECASE)
    if level_match:
        number_match = re.search(r"1[0-1][0-9]", text)
        return bool(number_match and 100 <= int(number_match.group()) <= 110)
    return text.isdigit() and 100 <= int(text) <= 110


def is_noise_text(text: str) -> bool:
    key = normalize_text(text)
    if not key:
        return True
    if re.fullmatch(r"(Lv|LV|Iv)?1[0-1][0-9]", key, re.IGNORECASE):
        return True
    if key in {"Lv", "LV", "IV", "I", "l"}:
        return True
    return False


def merge_name_boxes(boxes: list[OcrBox]) -> str:
    if not boxes:
        return ""
    boxes = sorted(boxes, key=lambda box: box.x1)
    chunks = []
    prev: OcrBox | None = None
    for box in boxes:
        if prev and box.x1 - prev.x2 > max(18, prev.height * 1.7):
            chunks.append(" ")
        chunks.append(box.text)
        prev = box
    return normalize_text("".join(chunks))


def infer_party_slot(anchor: OcrBox, image_width: int, image_height: int) -> tuple[int | None, int | None]:
    # 게임 화면 기본 배치: 좌측 1/3/5/7/9, 우측 2/4/6/8/10
    column = 0 if anchor.cx < image_width / 2 else 1
    row_height = image_height / 5
    row = max(0, min(4, int(anchor.cy // row_height)))
    party = row * 2 + 1 + column

    panel_width = image_width / 2
    local_x = anchor.cx - (column * panel_width)
    slot = max(1, min(5, int(local_x / (panel_width / 5)) + 1))
    return slot, party


def extract_raw_names_from_boxes(boxes: list[OcrBox], image_shape: tuple[int, int]) -> list[RawName]:
    image_height, image_width = image_shape[:2]
    levels = [box for box in boxes if is_level_box(box)]
    raw_names: list[RawName] = []
    used_level_keys = set()

    for level in levels:
        level_key = (level.x1 // 8, level.y1 // 8, level.text)
        if level_key in used_level_keys:
            continue
        used_level_keys.add(level_key)

        y_min = level.y1 - max(90, int(level.height * 4.2))
        y_max = level.y1 - max(2, int(level.height * 0.18))
        x_pad = max(28, int(level.width * 0.55))
        candidates = [
            box for box in boxes
            if box.y2 >= y_min
            and box.y1 <= y_max
            and box.x2 >= level.x1 - x_pad
            and box.x1 <= level.x2 + x_pad
            and not is_noise_text(box.text)
        ]

        # 닉네임이 여러 박스로 쪼개진 경우를 대비해 같은 줄 후보만 남긴다.
        if candidates:
            nearest_y = max(candidates, key=lambda box: box.y2).cy
            line_candidates = [box for box in candidates if abs(box.cy - nearest_y) <= max(24, box.height * 1.4)]
        else:
            line_candidates = []

        raw = merge_name_boxes(line_candidates)
        if not raw:
            continue
        slot, party = infer_party_slot(level, image_width, image_height)
        raw_names.append(RawName(
            text=raw,
            x1=min(box.x1 for box in line_candidates),
            y1=min(box.y1 for box in line_candidates),
            x2=max(box.x2 for box in line_candidates),
            y2=max(box.y2 for box in line_candidates),
            level=level.text,
            slot=slot,
            party=party,
        ))

    # 같은 슬롯 중복 제거
    deduped: dict[tuple[int | None, int | None, str], RawName] = {}
    for item in raw_names:
        key = (item.party, item.slot, normalize_for_similarity(item.text))
        deduped[key] = item
    return list(deduped.values())


def analyze_image(image_bytes: bytes, clan_hint: str | None, members_json: str | None = None) -> dict[str, Any]:
    members = parse_members_json(members_json)
    processed = preprocess_image(image_bytes)
    boxes = run_tesseract_boxes(processed)
    raw_names = extract_raw_names_from_boxes(boxes, processed.shape)

    confirmed = []
    candidates = []
    for raw in raw_names:
        matched = match_clan_member(raw.text, clan_hint=clan_hint, members=members)
        best = matched["best"]
        if matched["confirmed"] and best:
            confirmed.append({
                "raw": raw.text,
                "name": best["name"],
                "clan": best["clan"],
                "score": best["score"],
                "slot": raw.slot,
                "party": raw.party,
                "bbox": [raw.x1, raw.y1, raw.x2, raw.y2],
                "level": raw.level,
            })
        else:
            # 최소 1명 후보 보장: suggestions[0]은 점수가 낮아도 포함된다.
            candidates.append({
                "raw": raw.text,
                "slot": raw.slot,
                "party": raw.party,
                "bbox": [raw.x1, raw.y1, raw.x2, raw.y2],
                "level": raw.level,
                "suggestions": matched["suggestions"],
            })

    confirmed.sort(key=lambda row: ((row["party"] or 99), (row["slot"] or 99), row["name"]))
    candidates.sort(key=lambda row: ((row["party"] or 99), (row["slot"] or 99), row["raw"]))
    return {
        "confirmed": confirmed,
        "candidates": candidates,
        "debug": {
            "anchors": len([box for box in boxes if is_level_box(box)]),
            "raw_names": len(raw_names),
            "ocr_boxes": len(boxes),
            "clan_hint": clan_hint or "",
            "member_source_count": len(members),
        },
    }


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/attendance/ocr")
async def attendance_ocr(
    file: UploadFile = File(...),
    clan_hint: str | None = Form(default=None),
    members_json: str | None = Form(default=None),
) -> dict[str, Any]:
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="이미지 파일만 업로드할 수 있습니다.")

    image_bytes = await file.read()
    if not image_bytes:
        raise HTTPException(status_code=400, detail="빈 파일입니다.")

    loop = asyncio.get_running_loop()
    try:
        return await loop.run_in_executor(executor, analyze_image, image_bytes, clan_hint, members_json)
    except pytesseract.TesseractNotFoundError as exc:
        raise HTTPException(
            status_code=500,
            detail="Tesseract 실행 파일을 찾을 수 없습니다. TESSERACT_CMD 환경변수를 확인하세요.",
        ) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"OCR 처리 실패: {exc}") from exc


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=8090, reload=True)
