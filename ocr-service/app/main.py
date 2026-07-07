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
CONFIRMED_SCORE = float(os.getenv("OCR_CONFIRMED_SCORE", "0.65"))
MIN_CANDIDATE_SCORE = float(os.getenv("OCR_MIN_CANDIDATE_SCORE", "0.50"))
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


@dataclass
class SlotCrop:
    image: np.ndarray
    party: int
    slot: int
    x1: int
    y1: int
    x2: int
    y2: int


@dataclass
class PartyPanel:
    image: np.ndarray
    party: int
    x1: int
    y1: int
    x2: int
    y2: int


def normalize_text(value: str) -> str:
    value = unicodedata.normalize("NFKC", value or "")
    value = re.sub(r"\s+", "", value)
    value = value.replace("|", "I").replace("１", "1").replace("Ｉ", "I")
    return re.sub(r"[^0-9A-Za-z가-힣]", "", value)


def normalize_text(value: str) -> str:
    value = unicodedata.normalize("NFKC", value or "")
    value = re.sub(r"\s+", "", value)
    value = value.replace("|", "I").replace("!", "I")
    return re.sub(r"[^0-9A-Za-z\uac00-\ud7a3]", "", value)


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
        is_hint_clan = bool(clan_hint and member.clan == clan_hint)
        boosted_score = base_score + (CLAN_BOOST if is_hint_clan else 0)
        ranked.append({
            "name": member.name,
            "clan": member.clan,
            "score": round(min(boosted_score, 1.15), 4),
            "base_score": round(base_score, 4),
            "hint_clan": is_hint_clan,
        })
    ranked.sort(key=lambda row: (row["hint_clan"], row["score"], row["base_score"], row["name"]), reverse=True)
    if clan_hint:
        clan_ranked = [row for row in ranked if row["hint_clan"]]
        if clan_ranked and clan_ranked[0]["score"] >= MIN_CANDIDATE_SCORE:
            ranked = clan_ranked
    ranked = [row for row in ranked if row["score"] >= MIN_CANDIDATE_SCORE]
    best = ranked[0] if ranked else None
    return {
        "raw": raw_name,
        "confirmed": bool(best and best["score"] >= CONFIRMED_SCORE),
        "best": best,
        "suggestions": ranked[:limit],
    }


def crop_roster_content(image: np.ndarray) -> np.ndarray:
    """Trim wide dark/white margins before OCR.

    Chat apps often add padding around the roster screenshot. Tesseract then
    spends most of its attention on empty space and the party grid inference is
    skewed.  This keeps the visible roster rectangle while falling back to the
    original image if no safe crop is found.
    """
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    mask = gray > 18
    rows = np.where(mask.any(axis=1))[0]
    cols = np.where(mask.any(axis=0))[0]
    if rows.size == 0 or cols.size == 0:
        return image

    y1, y2 = int(rows[0]), int(rows[-1])
    x1, x2 = int(cols[0]), int(cols[-1])
    height, width = image.shape[:2]
    pad_y = max(4, int((y2 - y1 + 1) * 0.025))
    pad_x = max(4, int((x2 - x1 + 1) * 0.025))
    y1 = max(0, y1 - pad_y)
    y2 = min(height - 1, y2 + pad_y)
    x1 = max(0, x1 - pad_x)
    x2 = min(width - 1, x2 + pad_x)

    cropped = image[y1:y2 + 1, x1:x2 + 1]
    if cropped.shape[0] < height * 0.25 or cropped.shape[1] < width * 0.25:
        return image
    return cropped


def preprocess_image(image_bytes: bytes) -> list[np.ndarray]:
    image_array = np.frombuffer(image_bytes, np.uint8)
    image = cv2.imdecode(image_array, cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError("이미지 파일을 읽을 수 없습니다.")

    image = crop_roster_content(image)
    scaled = cv2.resize(image, None, fx=3.0, fy=3.0, interpolation=cv2.INTER_CUBIC)
    gray = cv2.cvtColor(scaled, cv2.COLOR_BGR2GRAY)

    denoised = cv2.fastNlMeansDenoising(gray, None, h=7, templateWindowSize=7, searchWindowSize=21)
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
    binary = cv2.morphologyEx(binary, cv2.MORPH_OPEN, kernel)
    inverted = cv2.bitwise_not(binary)
    contrast = cv2.convertScaleAbs(sharpened, alpha=1.6, beta=18)
    return [binary, inverted, contrast]


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


def run_tesseract_boxes_variants(images: list[np.ndarray]) -> list[OcrBox]:
    """Run OCR on a few cheap visual variants and keep the best boxes.

    This is still one server request, but it protects against cases where
    Tesseract only likes inverted or contrast-boosted Discord screenshots.
    """
    merged: dict[tuple[str, int, int], OcrBox] = {}
    for image in images:
        for box in run_tesseract_boxes(image):
            key = (box.text, round(box.x1 / 12), round(box.y1 / 12))
            current = merged.get(key)
            if current is None or box.conf > current.conf:
                merged[key] = box
    return list(merged.values())


def run_tesseract_text(image: np.ndarray, psm: int = 7) -> str:
    pil_image = Image.fromarray(image)
    config = f"--oem 1 --psm {psm} -c preserve_interword_spaces=1"
    raw = pytesseract.image_to_string(pil_image, lang=OCR_LANG, config=config)
    return clean_name_text(raw)


def clean_name_text(value: str) -> str:
    value = unicodedata.normalize("NFKC", value or "")
    value = re.sub(r"(?i)L\s*[vV]\s*\.?\s*1[0-1][0-9].*", "", value)
    value = re.sub(r"\b1[0-1][0-9]\b.*", "", value)
    value = value.replace("\n", " ").replace("\r", " ")
    value = value.replace("|", "I").replace("!", "I")
    value = re.sub(r"[^\w가-힣]+", "", value, flags=re.UNICODE)
    value = re.sub(r"^[0-9]+", "", value)
    value = re.sub(r"[0-9]+$", "", value)
    return normalize_text(value)


def process_slot_ocr(slot_img: np.ndarray, psm: int = 7) -> str:
    """OCR only the nickname area of a single character slot."""
    try:
        h, w = slot_img.shape[:2]
        if h == 0 or w == 0:
            return ""

        resized = cv2.resize(slot_img, None, fx=3.0, fy=3.0, interpolation=cv2.INTER_CUBIC)
        gray = cv2.cvtColor(resized, cv2.COLOR_BGR2GRAY)
        blurred = cv2.GaussianBlur(gray, (3, 3), 0)
        _, thresh = cv2.threshold(blurred, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
        config = f"--oem 1 --psm {psm} -c preserve_interword_spaces=1"
        text = pytesseract.image_to_string(thresh, lang=OCR_LANG, config=config)
        return clean_name_text(text)
    except Exception as e:
        print(f"❌ [OCR ENGINE ERROR] Tesseract failed: {str(e)}")
        return ""


def is_noise_ocr_name(value: str) -> bool:
    text = normalize_text(value)
    if not text:
        return True
    if len(text) <= 1:
        return True
    has_korean = bool(re.search(r"[가-힣]", text))
    if len(text) <= 2 and not has_korean:
        return True
    if re.fullmatch(r"[A-Za-z0-9]{1,3}", text) and not has_korean:
        return True
    digits = sum(ch.isdigit() for ch in text)
    if digits and digits >= max(2, len(text) * 0.45):
        return True
    return False


def is_noise_ocr_name(value: str) -> bool:
    text = normalize_text(value)
    if not text:
        return True
    if len(text) <= 1:
        return True
    has_korean = bool(re.search(r"[\uac00-\ud7a3]", text))
    if len(text) <= 2 and not has_korean:
        return True
    if re.fullmatch(r"[A-Za-z0-9]{1,3}", text) and not has_korean:
        return True
    digits = sum(ch.isdigit() for ch in text)
    if digits and digits >= max(2, len(text) * 0.45):
        return True
    return False


def make_slot_ocr_variants(slot_image: np.ndarray) -> list[np.ndarray]:
    if slot_image.size == 0:
        return []
    scaled = cv2.resize(slot_image, None, fx=5.0, fy=5.0, interpolation=cv2.INTER_CUBIC)
    gray = cv2.cvtColor(scaled, cv2.COLOR_BGR2GRAY)
    denoised = cv2.fastNlMeansDenoising(gray, None, h=5, templateWindowSize=7, searchWindowSize=21)
    blur = cv2.GaussianBlur(denoised, (0, 0), 1.0)
    sharpened = cv2.addWeighted(denoised, 2.0, blur, -1.0, 0)
    contrast = cv2.convertScaleAbs(sharpened, alpha=1.8, beta=20)
    binary = cv2.adaptiveThreshold(
        contrast,
        255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY,
        31,
        5,
    )
    inverted = cv2.bitwise_not(binary)
    return [contrast, binary, inverted]


def slot_has_possible_name(slot_image: np.ndarray) -> bool:
    if slot_image.size == 0:
        return False
    gray = cv2.cvtColor(slot_image, cv2.COLOR_BGR2GRAY)
    bright_ratio = float(np.mean(gray > 105))
    mid_ratio = float(np.mean((gray > 45) & (gray <= 105)))
    return bright_ratio > 0.01 or mid_ratio > 0.08


def detect_cyan_party_anchors(image: np.ndarray) -> list[tuple[int, int, int, int]]:
    """Find the cyan/blue party number plates with OpenCV contours."""
    hsv = cv2.cvtColor(image, cv2.COLOR_BGR2HSV)
    lower = np.array([75, 30, 30], dtype=np.uint8)
    upper = np.array([145, 255, 255], dtype=np.uint8)
    mask = cv2.inRange(hsv, lower, upper)
    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (3, 3))
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, kernel)
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    height, width = image.shape[:2]
    image_area = height * width
    anchors: list[tuple[int, int, int, int]] = []
    for contour in contours:
        x, y, w, h = cv2.boundingRect(contour)
        area = cv2.contourArea(contour)
        box_area = max(1, w * h)
        ratio = w / max(1, h)
        fill = area / box_area
        if area < max(12, image_area * 0.000006):
            continue
        if area > image_area * 0.008:
            continue
        if not (10 < w < 80 and 10 < h < 80):
            continue
        if not (0.45 <= ratio <= 1.65):
            continue
        if fill < 0.28:
            continue
        anchors.append((x, y, x + w, y + h))

    anchors.sort(key=lambda box: (box[1], box[0]))
    deduped: list[tuple[int, int, int, int]] = []
    for box in anchors:
        x1, y1, x2, y2 = box
        if any(abs(x1 - px1) <= max(8, (x2 - x1)) and abs(y1 - py1) <= max(8, (y2 - y1)) for px1, py1, px2, py2 in deduped):
            continue
        deduped.append(box)
    return deduped[:10]


def cluster_anchor_rows(anchors: list[tuple[int, int, int, int]]) -> list[list[tuple[int, int, int, int]]]:
    if not anchors:
        return []
    median_height = float(np.median([y2 - y1 for _, y1, _, y2 in anchors]))
    threshold = max(8.0, median_height * 2.2)
    rows: list[list[tuple[int, int, int, int]]] = []
    for anchor in sorted(anchors, key=lambda box: (box[1], box[0])):
        cy = (anchor[1] + anchor[3]) / 2
        target = None
        for row in rows:
            row_cy = float(np.mean([(box[1] + box[3]) / 2 for box in row]))
            if abs(cy - row_cy) <= threshold:
                target = row
                break
        if target is None:
            rows.append([anchor])
        else:
            target.append(anchor)
    return [sorted(row, key=lambda box: box[0]) for row in rows]


def derive_party_panels_from_anchors(image: np.ndarray) -> list[PartyPanel]:
    cropped = crop_roster_content(image)
    anchors = detect_cyan_party_anchors(cropped)
    rows = cluster_anchor_rows(anchors)
    rows = sorted(rows, key=lambda row: min(box[1] for box in row))[:5]
    if not rows:
        return []

    height, width = cropped.shape[:2]
    left_xs = [row[0][0] for row in rows if row]
    right_xs = [row[1][0] for row in rows if len(row) > 1]
    if right_xs:
        left_x = int(round(float(np.median(left_xs))))
        right_x = int(round(float(np.median(right_xs))))
        panel_width = max(1, right_x - left_x)
    else:
        left_x = int(round(float(np.median(left_xs))))
        panel_width = max(1, width - left_x)
        right_x = left_x + panel_width

    row_tops = [min(box[1] for box in row) for row in rows]
    row_gaps = [b - a for a, b in zip(row_tops, row_tops[1:]) if b > a]
    panel_height = int(round(float(np.median(row_gaps)))) if row_gaps else max(1, height - row_tops[0])
    panel_height = max(1, panel_height)

    panels: list[PartyPanel] = []
    for row_index, row in enumerate(rows):
        top = int(min(box[1] for box in row))
        for col_index, _anchor in enumerate(row[:2]):
            party = row_index * 2 + 1 + col_index
            x1 = left_x if col_index == 0 else right_x
            x2 = min(width, x1 + panel_width)
            y1 = max(0, top)
            y2 = min(height, y1 + panel_height)
            if x2 - x1 < 8 or y2 - y1 < 8:
                continue
            print(f" Detected Panel Party {party}: {x1}, {y1} to {x2}, {y2}")
            panels.append(PartyPanel(
                image=cropped[y1:y2, x1:x2],
                party=party,
                x1=x1,
                y1=y1,
                x2=x2,
                y2=y2,
            ))
    return panels


def find_party_panels_by_anchors(image: np.ndarray) -> list[PartyPanel]:
    """Public cyan-anchor panel detector used by the attendance OCR pipeline."""
    return derive_party_panels_from_anchors(image)


def split_party_slots(image: np.ndarray) -> list[SlotCrop]:
    """Split by cyan party-number anchors, then by five character slots."""
    panels = find_party_panels_by_anchors(image)
    slots: list[SlotCrop] = []

    for party_panel in panels:
        panel = party_panel.image
        p_height, p_width = panel.shape[:2]
        for slot_index in range(5):
            slot_x1 = int(round(slot_index * p_width / 5))
            slot_x2 = int(round((slot_index + 1) * p_width / 5))
            name_y1 = int(round(p_height * 0.34))
            name_y2 = int(round(p_height * 0.68))
            pad_x = max(1, int(round(p_width * 0.012)))
            x1 = max(0, slot_x1 - pad_x)
            x2 = min(p_width, slot_x2 + pad_x)
            slot_image = panel[name_y1:name_y2, x1:x2]
            if not slot_has_possible_name(slot_image):
                continue
            slots.append(SlotCrop(
                image=slot_image,
                party=party_panel.party,
                slot=slot_index + 1,
                x1=party_panel.x1 + x1,
                y1=party_panel.y1 + name_y1,
                x2=party_panel.x1 + x2,
                y2=party_panel.y1 + name_y2,
            ))
    return slots


def best_text_for_slot(slot: SlotCrop, members: list[ClanMember], clan_hint: str | None) -> str:
    texts: list[str] = []
    for psm in (7, 8):
        text = process_slot_ocr(slot.image, psm=psm)
        if text and not is_noise_ocr_name(text):
            texts.append(text)
    for variant in make_slot_ocr_variants(slot.image):
        for psm in (7, 8):
            text = run_tesseract_text(variant, psm=psm)
            if text and not is_noise_ocr_name(text):
                texts.append(text)

    unique_texts = []
    seen = set()
    for text in texts:
        key = normalize_for_similarity(text)
        if not key or key in seen:
            continue
        seen.add(key)
        unique_texts.append(text)
    if not unique_texts:
        return ""

    def rank_text(text: str) -> tuple[float, int]:
        matched = match_clan_member(text, clan_hint=clan_hint, members=members)
        best = matched.get("best") or {}
        score = float(best.get("score") or 0)
        # Prefer short nickname-like text over a long merged garbage string.
        length_penalty = max(0, len(text) - 14) * 0.035
        return (score - length_penalty, -abs(len(text) - 5))

    best_text = max(unique_texts, key=rank_text)
    matched = match_clan_member(best_text, clan_hint=clan_hint, members=members)
    best = matched.get("best") or {}
    if float(best.get("score") or 0) < MIN_CANDIDATE_SCORE:
        return ""
    return best_text


def extract_raw_names_from_slots(image_bytes: bytes, members: list[ClanMember], clan_hint: str | None) -> list[RawName]:
    image_array = np.frombuffer(image_bytes, np.uint8)
    image = cv2.imdecode(image_array, cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError("?대?吏 ?뚯씪???쎌쓣 ???놁뒿?덈떎.")

    raw_names: list[RawName] = []
    for slot in split_party_slots(image):
        raw = best_text_for_slot(slot, members, clan_hint)
        if not raw:
            continue
        raw_names.append(RawName(
            text=raw,
            x1=slot.x1,
            y1=slot.y1,
            x2=slot.x2,
            y2=slot.y2,
            level="",
            slot=slot.slot,
            party=slot.party,
        ))

    deduped: dict[tuple[int | None, int | None], RawName] = {}
    for item in raw_names:
        current = deduped.get((item.party, item.slot))
        if current is None:
            deduped[(item.party, item.slot)] = item
            continue
        current_score = (match_clan_member(current.text, clan_hint=clan_hint, members=members).get("best") or {}).get("score") or 0
        next_score = (match_clan_member(item.text, clan_hint=clan_hint, members=members).get("best") or {}).get("score") or 0
        if next_score > current_score:
            deduped[(item.party, item.slot)] = item
    return list(deduped.values())


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
    raw_names = extract_raw_names_from_slots(image_bytes, members, clan_hint)
    processed_variants = []
    boxes: list[OcrBox] = []
    if len(raw_names) < 3:
        processed_variants = preprocess_image(image_bytes)
        boxes = run_tesseract_boxes_variants(processed_variants)
        raw_names.extend(extract_raw_names_from_boxes(boxes, processed_variants[0].shape))

    raw_by_position: dict[tuple[int | None, int | None], RawName] = {}
    for raw in raw_names:
        key = (raw.party, raw.slot)
        current = raw_by_position.get(key)
        if current is None:
            raw_by_position[key] = raw
            continue
        current_score = (match_clan_member(current.text, clan_hint=clan_hint, members=members).get("best") or {}).get("score") or 0
        next_score = (match_clan_member(raw.text, clan_hint=clan_hint, members=members).get("best") or {}).get("score") or 0
        if next_score > current_score:
            raw_by_position[key] = raw
    raw_names = list(raw_by_position.values())

    confirmed = []
    candidates = []
    for raw in raw_names:
        matched = match_clan_member(raw.text, clan_hint=clan_hint, limit=3, members=members)
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
            "slot_scan": True,
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
