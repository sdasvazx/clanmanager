import React, { useEffect, useMemo, useState } from 'react';
import { createWorker } from 'tesseract.js';
import './roster.css';
import './vault.css';
import './manager.css';
import './notice.css';
import './theme.css';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';
const ROULETTE_URL = 'https://lazygyu.github.io/roulette/';

const menu = [
  ['lobby', '로', '로비'],
  ['my-info', '내', '내 정보 확인'],
  ['participation', '참', '참여율 조회'],
  ['attendance', '보', '보스 참여내역 조회'],
  ['payment', '분', '분배금 조회'],
  ['ledger', '통', '통장현황'],
  ['book', '장', '장부 조회'],
  ['inventory', '재', '재고현황'],
  ['bidding', '아', '아이템입찰'],
  ['collection', '컬', '컬렉템 지급현황'],
  ['mypage', '마', '마이페이지'],
  ['admin', '관', '관리자 설정'],
];

const adminOnlyPages = new Set(['ledger', 'roster', 'admin', 'member-admin', 'pinball', 'spec-history']);

const adminCards = [
  ['✓', '출석체크/보스설정', 'mint', 'attendance'],
  ['♙', '클랜원/전투력 관리', 'blue', 'member-admin'],
  ['⚙', '가중치 설정', 'orange', 'participation'],
  ['✿', '기타 설정', 'indigo', 'admin'],
  ['▣', '스펙/장비 수정기록', 'amber', 'spec-history'],
  ['▥', '참여율 선택조회', 'cyan', 'participation'],
  ['⚠', '게헨나감지', 'red', 'roster'],
  ['🎯', '핀볼', 'purple', 'pinball'],
];

const favoriteStorageKey = (member) => `clanmanager:favorites:${member?.memberId || 'guest'}`;
const pageMetaList = [
  ...menu.map(([id, icon, label]) => ({ id, icon, label })),
  { id: 'member-admin', icon: '관', label: '클랜원/전투력 관리' },
  { id: 'pinball', icon: '핀', label: '핀볼' },
  { id: 'spec-history', icon: '스', label: '스펙/장비 기록' },
  { id: 'roster', icon: '스', label: '출석 OCR' },
  { id: 'item-request', icon: '신', label: '아이템 신청' },
];
const pageMetaMap = new Map(pageMetaList.map((item) => [item.id, item]));
const pageMeta = (id) => pageMetaMap.get(id) || { id, icon: '★', label: id };
const readFavorites = (member) => {
  try {
    const parsed = JSON.parse(localStorage.getItem(favoriteStorageKey(member)) || '[]');
    return Array.isArray(parsed) ? parsed.filter((id) => pageMetaMap.has(id)) : [];
  } catch {
    return [];
  }
};
const writeFavorites = (member, ids) => localStorage.setItem(favoriteStorageKey(member), JSON.stringify(ids));

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.message ?? '요청 처리 중 오류가 발생했습니다.');
  return body;
}

const formatNumber = (value) => Number(value || 0).toLocaleString();
const money = (value) => `${formatNumber(value)} 다이아`;
const today = () => new Date().toISOString().slice(0, 10);
const PARTICIPATION_PERIOD_START = '2026-07-08';
const MS_PER_DAY = 24 * 60 * 60 * 1000;
const formatLocalDate = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
const dateOnly = (value) => {
  const date = new Date(`${value}T00:00:00`);
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
};
const addDays = (value, days) => {
  const date = dateOnly(value);
  date.setDate(date.getDate() + days);
  return formatLocalDate(date);
};
const getParticipationPeriod = (index) => {
  const start = addDays(PARTICIPATION_PERIOD_START, index * 14);
  const end = addDays(start, 14);
  return { index, start, end };
};
const getCurrentParticipationPeriodIndex = () => {
  const diff = Math.floor((dateOnly(today()) - dateOnly(PARTICIPATION_PERIOD_START)) / MS_PER_DAY);
  return Math.max(0, Math.floor(diff / 14));
};
const defaultPeriodName = ({ index, start, end }) => `${index + 1}회차 (${start} ~ ${end})`;
const toMemberId = (value) => {
  const id = Number(value?.member?.memberId ?? value?.memberId ?? value);
  return Number.isFinite(id) ? id : null;
};
const copyToClipboard = async (text) => {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.setAttribute('readonly', '');
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.select();
  document.execCommand('copy');
  document.body.removeChild(textarea);
};
const normalize = (value) => String(value ?? '').toLowerCase().replace(/[^0-9a-z가-힣]/g, '');
const normalizeForOcrMatch = (value) => normalize(value)
  .replace(/2/g, 'z')
  .replace(/0/g, 'o')
  .replace(/[1il]/g, 'l')
  .replace(/vv/g, 'w')
  .replace(/rn/g, 'm');
const KOREAN_CHAR_PATTERN = /[\uAC00-\uD7A3]/;
const NICKNAME_CHAR_PATTERN = /[^0-9A-Za-z\uAC00-\uD7A3]/g;
const OCR_NOISE_WORDS = new Set(['lv', 'lvl', 'level', 'l', 'v', 'iv', 'il', 'i', '공격대', '파티', '자리', '빈칸', '추가']);
const OCR_CORRECTION_STORAGE_KEY = 'clanmanagerOcrCorrections';
const OCR_FILTER_STORAGE_KEY = 'clanmanagerOcrFilters';
const clanOptions = ['귀신', '운좋은사람들', '귀신Z', '로망'];
const bossOptions = ['13시 보스', '17시 보스', '21시 보스', '정예던전보스', '에노크', '마슈미드', '클랜임무', '수호', '쟁탈전'];
const bossCheckSlots = [
  { key: '01', title: '01시 (1성)', cutTime: '01:00', bossName: '01시 보스', score: 1 },
  { key: '05', title: '05시 (1성)', cutTime: '05:00', bossName: '05시 보스', score: 1 },
  { key: '09', title: '09시 (1성)', cutTime: '09:00', bossName: '09시 보스', score: 1 },
  { key: '13', title: '13시 (2성)', cutTime: '13:00', bossName: '13시 보스', score: 1 },
  { key: '17', title: '17시 (1성)', cutTime: '17:00', bossName: '17시 보스', score: 1 },
  { key: '21', title: '21시 (2성)', cutTime: '21:00', bossName: '21시 보스', score: 1 },
  { key: 'final', title: '결승전', cutTime: '22:00', bossName: '결승전', score: 1 },
  { key: 'mashumid', title: '마슈미드', cutTime: '22:00', bossName: '마슈미드', score: 1 },
  { key: 'enoch', title: '에노크', cutTime: '22:00', bossName: '에노크', score: 1 },
];
const clanDisplayOrder = ['귀신', '운좋은사람들', '귀신Z', '로망'];

function canonicalClanName(value) {
  const name = String(value ?? '').trim();
  const key = normalize(name);
  if (!key) return '미분류';
  if (key.includes('귀신z') || key.includes('귀신제트')) return '귀신Z';
  if (key.includes('운좋은')) return '운좋은사람들';
  if (key.includes('로망')) return '로망';
  if (key.includes('게헨나')) return '게헨나';
  if (key.includes('귀신')) return '귀신';
  return name;
}

function groupByClan(rows) {
  const map = new Map();
  rows.forEach((row) => {
    const clan = canonicalClanName(row.guildName || row.clanName);
    map.set(clan, [...(map.get(clan) || []), row]);
  });
  return [...map.entries()].sort(([a], [b]) => {
    const ai = clanDisplayOrder.indexOf(a);
    const bi = clanDisplayOrder.indexOf(b);
    return (ai === -1 ? 999 : ai) - (bi === -1 ? 999 : bi) || a.localeCompare(b, 'ko-KR');
  });
}

function extractOcrNames(text, registeredMembers = []) {
  const ocrKey = normalizeForOcrMatch;
  const memberByNormalized = new Map(registeredMembers.flatMap((m) => [...new Set([normalize(m.characterName), ocrKey(m.characterName)])].map((key) => [key, m.characterName])));
  const wholeText = String(text ?? '');
  const normalizedText = normalize(wholeText);
  const zFixedText = normalizedText.replace(/2/g, 'z');
  const matched = registeredMembers
    .filter((m) => normalize(m.characterName).length > 1 && (normalizedText.includes(normalize(m.characterName)) || zFixedText.includes(ocrKey(m.characterName))))
    .map((m) => m.characterName);
  const matchedKeys = new Set(matched.map(normalize));
  const blockedWords = new Set(['귀신', '귀신z', '로망', '운좋은', '운좋은사람들', '게헨나', '미분류', 'lv', 'level']);
  const guessed = wholeText
    .split(/\r?\n/)
    .flatMap((line) => line
      .replace(/Lv\.?\s*\d+/gi, ' ')
      .replace(/Level\s*\d+/gi, ' ')
      .replace(/[|()[\]{}"'\`~!@#$%^&*_+=:;<>?/\\]/g, ' ')
      .split(/\s+|,|·|ㆍ|•|-/)
    )
    .map((name) => name.trim())
    .filter((name) => /[가-힣]/.test(name))
    .map((name) => name.replace(/^[^0-9A-Za-z가-힣]+|[^0-9A-Za-z가-힣]+$/g, ''))
    .filter((name) => /^[0-9A-Za-z가-힣]{2,10}$/.test(name))
    .filter((name) => !/^[A-Za-z0-9]+$/.test(name))
    .filter((name) => !blockedWords.has(normalize(name)))
    .map((name) => memberByNormalized.get(normalize(name)) ?? memberByNormalized.get(ocrKey(name)) ?? '')
    .filter(Boolean)
    .filter((name) => !matchedKeys.has(normalize(name)));
  return [...new Set([...matched, ...guessed])];
}

function cleanOcrNicknameToken(value) {
  let token = String(value ?? '')
    .replace(/Lv\.?\s*\d+/gi, ' ')
    .replace(/Level\s*\d+/gi, ' ')
    .replace(/\bL\s*v\.?\s*\d+/gi, ' ')
    .replace(/^[#\s]*\d{1,2}\s*/, '')
    .replace(/\+\s*$/, '')
    .trim();
  token = token
    .replace(/[|｜¦]/g, 'I')
    .replace(/[‘’“”"']/g, '')
    .replace(NICKNAME_CHAR_PATTERN, '')
    .trim();
  const key = normalize(token);
  if (!key || OCR_NOISE_WORDS.has(key)) return '';
  if (/^\d+$/.test(key)) return '';
  if (/^lv?\d+$/i.test(key)) return '';
  if (token.length < 2 || token.length > 16) return '';
  if (!KOREAN_CHAR_PATTERN.test(token) && !/[A-Z]/.test(token) && token.length < 4) return '';
  return token;
}

function tokenizeOcrNicknames(text) {
  const lines = String(text ?? '')
    .replace(/Lv\.?\s*\d+/gi, '\n')
    .replace(/Level\s*\d+/gi, '\n')
    .replace(/\bL\s*v\.?\s*\d+/gi, '\n')
    .split(/\r?\n/);
  const tokens = [];
  lines.forEach((line) => {
    const cleanedLine = line
      .replace(/[()[\]{}<>]/g, ' ')
      .replace(/[~!@#$%^&*_+=:;?/\\]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
    cleanedLine.split(/[\s,·ㆍ•]+/).forEach((part) => {
      const token = cleanOcrNicknameToken(part);
      if (token) tokens.push(token);
    });
  });
  return [...new Set(tokens)];
}

function editDistance(a, b) {
  const left = normalizeForOcrMatch(a);
  const right = normalizeForOcrMatch(b);
  const matrix = Array.from({ length: left.length + 1 }, (_, row) => [row]);
  for (let column = 1; column <= right.length; column += 1) matrix[0][column] = column;
  for (let row = 1; row <= left.length; row += 1) {
    for (let column = 1; column <= right.length; column += 1) {
      matrix[row][column] = left[row - 1] === right[column - 1]
        ? matrix[row - 1][column - 1]
        : Math.min(matrix[row - 1][column - 1], matrix[row][column - 1], matrix[row - 1][column]) + 1;
    }
  }
  return matrix[left.length][right.length];
}

function similarityScore(a, b) {
  const left = normalizeForOcrMatch(a);
  const right = normalizeForOcrMatch(b);
  const leftVariants = ocrComparableVariants(left);
  const rightVariants = ocrComparableVariants(right);
  let best = 0;
  leftVariants.forEach((leftKey) => {
    rightVariants.forEach((rightKey) => {
      if (!leftKey || !rightKey) return;
      if (leftKey === rightKey) {
        best = Math.max(best, 1);
        return;
      }
      if (leftKey.includes(rightKey) || rightKey.includes(leftKey)) {
        best = Math.max(best, 0.92);
      }
      const distanceScore = 1 - (editDistance(leftKey, rightKey) / Math.max(leftKey.length, rightKey.length));
      const overlap = [...new Set(leftKey)].filter((char) => rightKey.includes(char)).length / Math.max(1, new Set([...leftKey, ...rightKey]).size);
      best = Math.max(best, distanceScore, overlap * 0.82);
    });
  });
  return best;
}

function ocrComparableVariants(value) {
  const key = String(value ?? '');
  const variants = new Set([key]);
  variants.add(key
    .replace(/땡|맹|덩|뎅|뎡/g, '댕')
    .replace(/햄|험|헴/g, '햄'));
  variants.add(key
    .replace(/바|버|ㅂ|나|냐/g, 'vh')
    .replace(/땡|맹|덩|뎅|뎡/g, '댕'));
  variants.add(key
    .replace(/vh/g, '바')
    .replace(/땡|맹|덩|뎅|뎡/g, '댕'));
  return [...variants].filter(Boolean);
}

const SPECIAL_DANG_NICKNAMES = ['\uBCF5\uB315\uB315\uC774', '\uB315\uD788', '\uC3C4\uBCF5\uC774'];
const SPECIAL_TARGET_ALIAS_MAP = {
  '\uB315\uD788': [
    '\uB315\uD788', '\uB300\uD788', '\uB315\uC774', '\uB315\uD76C', '\uB369\uD788', '\uB369\uC774', '\uB354\uD788', '\uB2F9\uD788', '\uB31D\uD788', '\uB301\uD788',
  ],
  '\uBCF5\uB315\uB315\uC774': [
    '\uBCF5\uB315\uB315\uC774', '\uBCF5\uB300\uB300\uC774', '\uBCF5\uB369\uB369\uC774', '\uBCF5\uB561\uB561\uC774', '\uBCF5\uB385\uB385\uC774', '\uBCF5\uB315\uB315',
    '\uBD81\uB315\uB315\uC774', '\uBBC5\uB315\uB315\uC774', '\uC695\uB315\uB315\uC774', '\uBE05\uB315\uB315\uC774', '\uBE61\uB315\uB315\uC774',
    '\uBCF5\uB315\uB300\uC774', '\uBCF5\uB300\uB315\uC774', '\uBCF5\uB31D\uB31D\uC774', '\uBCF5\uB301\uB301\uC774',
  ],
  '\uC3C4\uBCF5\uC774': [
    '\uC3C4\uBCF5\uC774', '\uC3CC\uBCF5\uC774', '\uC3C4\uBF41\uC774', '\uC3C4\uD3ED\uC774', '\uC3C4\uBCF5', '\uC3C4\uBD81\uC774',
    '\uC0C8\uBCF5\uC774', '\uC138\uBCF5\uC774', '\uC314\uBCF5\uC774', '\uC368\uBCF5\uC774', '\uC11C\uBCF5\uC774',
    '\uC0C8\uD3ED\uC774', '\uC138\uD3ED\uC774', '\uC0C8\uBF41\uC774', '\uC3C4\uBCF5\uD788',
  ],
};
const BOK_OCR_VARIANT_PATTERN = /[\uBCF5\uBD81\uBBC5\uC695\uBE05\uBE61]/g;
const DANG_OCR_VARIANT_PATTERN = /[\uB315\uB300\uB561\uB9F9\uB369\uB385\uB381\uB354\uB2F9\uB31D\uB301]/g;
const DANG_SUFFIX_OCR_VARIANT_PATTERN = /[\uC774\uD788\uD76C\uBBF8\uB2C8]/g;
const SSE_OCR_VARIANT_PATTERN = /[\uC3C4\uC3CC\uC138\uC0C8\uC314\uC368\uC11C]/g;
const POK_OCR_VARIANT_PATTERN = /[\uD3ED\uBF41\uBD81]/g;

function normalizeDangNickname(value) {
  return String(value ?? '')
    .toLowerCase()
    .replace(/[^0-9a-z\uAC00-\uD7A3]/g, '')
    .replace(/2/g, 'z')
    .replace(/0/g, 'o')
    .replace(/[1il]/g, 'l')
    .replace(/vv/g, 'w')
    .replace(/rn/g, 'm')
    .replace(BOK_OCR_VARIANT_PATTERN, '\uBCF5')
    .replace(DANG_OCR_VARIANT_PATTERN, '\uB315')
    .replace(SSE_OCR_VARIANT_PATTERN, '\uC3C4')
    .replace(POK_OCR_VARIANT_PATTERN, '\uBCF5')
    .replace(DANG_SUFFIX_OCR_VARIANT_PATTERN, '\uC774');
}

const SPECIAL_DANG_NICKNAME_KEYS = SPECIAL_DANG_NICKNAMES.map((name) => normalizeDangNickname(name));
const SPECIAL_TARGET_ALIAS_KEYS = Object.fromEntries(
  Object.entries(SPECIAL_TARGET_ALIAS_MAP).map(([name, aliases]) => [name, new Set(aliases.map((alias) => normalizeDangNickname(alias)).filter(Boolean))]),
);

function countDangChars(value) {
  return (String(value ?? '').match(/\uB315/g) || []).length;
}

function looksLikeBokDangDang(rawKey) {
  if (rawKey.includes('\uBCF5') && countDangChars(rawKey) >= 2) return true;
  if (rawKey.includes('\uBCF5') && rawKey.includes('\uB315') && rawKey.length <= 6) return true;
  if (countDangChars(rawKey) >= 2 && rawKey.length <= 6) return true;
  return similarityScore(rawKey, '\uBCF5\uB315\uB315\uC774') >= 0.62;
}

function looksLikeDangHi(rawKey) {
  if (!rawKey.startsWith('\uB315')) return false;
  if (rawKey.includes('\uB315\uC774')) return true;
  return rawKey.length <= 3 && similarityScore(rawKey, '\uB315\uD788') >= 0.62;
}

function looksLikeSseBokI(rawKey) {
  if (rawKey.includes('\uC3C4') && rawKey.includes('\uBCF5')) return true;
  if (rawKey.includes('\uBCF5') && rawKey.length <= 4) return similarityScore(rawKey, '\uC3C4\uBCF5\uC774') >= 0.58;
  return similarityScore(rawKey, '\uC3C4\uBCF5\uC774') >= 0.62;
}

function findSpecialDangMember(rawName, members, clanName = '') {
  const rawKey = normalizeDangNickname(rawName);
  if (!rawKey) return '';
  const targetClan = String(clanName ?? '').trim() ? canonicalClanName(clanName) : '';
  const candidates = members.filter((member) => {
    const nameKey = normalizeDangNickname(member.characterName);
    if (!SPECIAL_DANG_NICKNAME_KEYS.includes(nameKey)) return false;
    return !targetClan || canonicalClanName(member.guildName || member.clanName) === targetClan;
  });
  for (const member of candidates) {
    const targetKey = normalizeDangNickname(member.characterName);
    if (!targetKey) continue;
    if (rawKey === targetKey) return member.characterName;
    if (SPECIAL_TARGET_ALIAS_KEYS[member.characterName]?.has(rawKey)) return member.characterName;
    if ([...(SPECIAL_TARGET_ALIAS_KEYS[member.characterName] || [])].some((aliasKey) => aliasKey.length >= 3 && (rawKey.includes(aliasKey) || aliasKey.includes(rawKey)))) {
      return member.characterName;
    }
    if (targetKey === '\uBCF5\uB315\uB315\uC774') {
      if ((rawKey.includes(targetKey) || targetKey.includes(rawKey)) && rawKey.length >= 3) return member.characterName;
      if (looksLikeBokDangDang(rawKey)) return member.characterName;
    }
    if (targetKey === '\uB315\uC774') {
      if (rawKey.includes(targetKey)) return member.characterName;
      if (looksLikeDangHi(rawKey)) return member.characterName;
    }
    if (targetKey === '\uC3C4\uBCF5\uC774') {
      if (rawKey.includes(targetKey)) return member.characterName;
      if (looksLikeSseBokI(rawKey)) return member.characterName;
    }
  }
  return '';
}

function extractSpecialDangNamesFromText(text, members, clanName = '') {
  const chunks = new Set(tokenizeOcrNicknames(text));
  const compact = normalizeDangNickname(String(text ?? '').replace(/Lv\.?\s*\d+/gi, ' '));
  for (let index = 0; index < compact.length; index += 1) {
    for (let size = 2; size <= 8; size += 1) {
      const slice = compact.slice(index, index + size);
      if (slice.length >= 2 && (slice.includes('\uBCF5') || slice.includes('\uB315') || slice.includes('\uC3C4'))) {
        chunks.add(slice);
      }
    }
  }
  const names = [];
  chunks.forEach((chunk) => {
    const matched = findSpecialDangMember(chunk, members, clanName);
    if (matched) names.push(matched);
  });
  return [...new Set(names)];
}

const OCR_MEMBER_PREFIXES = ['귀신', '귀신z', '로망', '운좋은', '운좋은사람들'];

function isNoisyOcrCandidate(rawName) {
  const raw = String(rawName ?? '').trim();
  const key = normalizeForOcrMatch(raw);
  if (!key || key.length < 2) return true;
  if (/^\d+$/.test(key)) return true;
  if (/^lv?\d*$/i.test(key)) return true;
  if (/[가-힣]\d{2,}$/.test(raw) || /\d{2,}[가-힣]/.test(raw)) return true;
  if (/^귀신\d+$/i.test(raw) || /^로망\d+$/i.test(raw)) return true;
  if (key.length <= 2 && !/[a-z]/.test(key)) return true;
  return false;
}

function findPrefixOmittedMember(rawName, members, clanName = '') {
  if (isNoisyOcrCandidate(rawName)) return '';
  const rawKey = normalizeForOcrMatch(rawName);
  if (rawKey.length < 2 || /\d/.test(rawKey)) return '';
  const targetClan = String(clanName ?? '').trim() ? canonicalClanName(clanName) : '';
  const scopedMembers = members.filter((member) => !targetClan || canonicalClanName(member.guildName || member.clanName) === targetClan);
  const matches = scopedMembers.filter((member) => {
    const nameKey = normalizeForOcrMatch(member.characterName);
    return OCR_MEMBER_PREFIXES.some((prefix) => {
      const prefixKey = normalizeForOcrMatch(prefix);
      return nameKey === `${prefixKey}${rawKey}` || (nameKey.startsWith(prefixKey) && nameKey.slice(prefixKey.length) === rawKey);
    });
  });
  return matches.length === 1 ? matches[0].characterName : '';
}

function findSimilarMembers(rawName, members, clanName, limit = 3) {
  const targetClan = String(clanName ?? '').trim() ? canonicalClanName(clanName) : '';
  const specialName = findSpecialDangMember(rawName, members, clanName);
  const prefixOmittedName = findPrefixOmittedMember(rawName, members, clanName);
  return members
    .filter((member) => !targetClan || canonicalClanName(member.guildName || member.clanName) === targetClan)
    .map((member) => ({
      member,
      score: Math.max(
        similarityScore(rawName, member.characterName),
        specialName === member.characterName ? 0.98 : 0,
        prefixOmittedName === member.characterName ? 0.96 : 0,
      ),
    }))
    .filter(({ score }) => score >= 0.66)
    .sort((a, b) => b.score - a.score || a.member.characterName.localeCompare(b.member.characterName, 'ko-KR'))
    .slice(0, limit);
}

function readStoredOcrCorrections() {
  try {
    const parsed = JSON.parse(localStorage.getItem(OCR_CORRECTION_STORAGE_KEY) || '{}');
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

function saveStoredOcrCorrections(corrections) {
  localStorage.setItem(OCR_CORRECTION_STORAGE_KEY, JSON.stringify(corrections));
}

function readStoredOcrFilters() {
  try {
    const parsed = JSON.parse(localStorage.getItem(OCR_FILTER_STORAGE_KEY) || '[]');
    return Array.isArray(parsed) ? [...new Set(parsed.map(normalize).filter(Boolean))] : [];
  } catch {
    return [];
  }
}

function saveStoredOcrFilters(filters) {
  localStorage.setItem(OCR_FILTER_STORAGE_KEY, JSON.stringify([...new Set((filters || []).map(normalize).filter(Boolean))]));
}

function isOcrFilteredName(value, filters = []) {
  const key = normalize(value);
  const ocrKey = normalizeForOcrMatch(value);
  return (filters || []).some((filter) => {
    const filterKey = normalize(filter);
    if (!filterKey) return false;
    return key === filterKey || ocrKey === normalizeForOcrMatch(filterKey);
  });
}

function findStoredCorrection(rawName, corrections = {}) {
  const rawKey = normalize(rawName);
  const rawOcrKey = normalizeForOcrMatch(rawName);
  if (corrections[rawKey]) return corrections[rawKey];
  if (corrections[rawOcrKey]) return corrections[rawOcrKey];
  const matched = Object.entries(corrections).find(([wrong]) => {
    const wrongKey = normalize(wrong);
    const wrongOcrKey = normalizeForOcrMatch(wrong);
    if (!wrongKey) return false;
    if (rawKey === wrongKey || rawOcrKey === wrongOcrKey) return true;
    return wrongKey.length >= 3 && rawKey.length >= 3 && (rawKey.includes(wrongKey) || wrongKey.includes(rawKey));
  });
  return matched?.[1] || '';
}

function findAutoCorrectedMember(rawName, members, clanName = '', corrections = {}) {
  const correctedName = findStoredCorrection(rawName, corrections);
  const scopedMembers = members.filter((member) => (
    !clanName || canonicalClanName(member.guildName || member.clanName) === canonicalClanName(clanName)
  ));
  const byName = new Map(members.map((member) => [normalize(member.characterName), member.characterName]));
  const byScopedName = new Map(scopedMembers.map((member) => [normalize(member.characterName), member.characterName]));
  if (correctedName) return byScopedName.get(normalize(correctedName)) || byName.get(normalize(correctedName)) || correctedName;
  const specialDangName = findSpecialDangMember(rawName, members, clanName);
  if (specialDangName) return specialDangName;
  const prefixOmittedName = findPrefixOmittedMember(rawName, members, clanName);
  if (prefixOmittedName) return prefixOmittedName;
  return '';
}

function inferDominantClanFromNames(names, members) {
  const memberByName = new Map(members.map((member) => [normalize(member.characterName), member]));
  const counts = new Map();
  names.forEach((name) => {
    const matched = memberByName.get(normalize(name));
    if (!matched) return;
    const clan = canonicalClanName(matched.guildName || matched.clanName);
    if (!clan || clan === '미분류') return;
    counts.set(clan, (counts.get(clan) || 0) + 1);
  });
  const ranked = [...counts.entries()].sort((a, b) => b[1] - a[1]);
  if (!ranked.length) return '';
  const [clan, count] = ranked[0];
  const total = [...counts.values()].reduce((sum, value) => sum + value, 0);
  const second = ranked[1]?.[1] || 0;
  const threshold = Math.max(4, Math.ceil(total * 0.55));
  return count >= threshold && count >= second + 2 ? clan : '';
}

function candidateNamesFromOcrText(text, precise = false) {
  const cleaned = String(text ?? '')
    .replace(/Lv\.?\s*\d+/gi, '\n')
    .replace(/Level\s*\d+/gi, '\n')
    .replace(/[|()[\]{}"'\`~!@#$%^&*_+=:;<>?/\\]/g, '\n')
    .replace(/\s+/g, '\n');
  const lineCandidates = cleaned
    .split(/\r?\n|,|·|ㆍ|•|-/)
    .map((name) => name.trim())
    .map((name) => name.replace(/^[^0-9A-Za-z가-힣]+|[^0-9A-Za-z가-힣]+$/g, ''))
    .filter((name) => /^[0-9A-Za-z가-힣]{2,16}$/.test(name));
  const compact = precise ? normalize(String(text ?? '').replace(/Lv\.?\s*\d+/gi, ' ')).slice(0, 800) : '';
  const compactCandidates = [];
  if (precise) {
    for (let index = 0; index < compact.length; index += 1) {
      for (let size = 2; size <= 10; size += 1) {
        const slice = compact.slice(index, index + size);
        if (slice.length >= 2 && /[가-힣]/.test(slice)) compactCandidates.push(slice);
      }
    }
  }
  return [...new Set([...lineCandidates, ...compactCandidates])];
}

function candidateNamesFromOcrTextSafe(text, precise = false) {
  const tokens = tokenizeOcrNicknames(text);
  if (!precise) return tokens;
  const merged = [];
  for (let index = 0; index < tokens.length - 1; index += 1) {
    const left = tokens[index];
    const right = tokens[index + 1];
    if (left.length <= 4 && right.length <= 4) {
      const joined = cleanOcrNicknameToken(`${left}${right}`);
      if (joined) merged.push(joined);
    }
  }
  return [...new Set([...tokens, ...merged])];
}

function ambiguousCandidateRank(item) {
  const [best, second] = item?.suggestions || [];
  if (!best) return 0;
  const rawKey = normalizeForOcrMatch(item.raw);
  const nameKey = normalizeForOcrMatch(best.member.characterName);
  const margin = best.score - (second?.score || 0);
  const lengthPenalty = Math.abs(rawKey.length - nameKey.length) * 0.04;
  const shortPenalty = rawKey.length <= 3 ? 0.08 : 0;
  return best.score + margin - lengthPenalty - shortPenalty;
}

function buildOcrReview(text, members, clanName, options = {}) {
  const precise = Boolean(options.precise);
  const corrections = options.corrections || {};
  const filters = options.filters || [];
  const appliedCorrections = [];
  const exactNames = [...new Set([...extractOcrNames(text, members), ...extractSpecialDangNamesFromText(text, members, clanName)])]
    .filter((name) => !isOcrFilteredName(name, filters))
    .map((name) => {
      const corrected = findAutoCorrectedMember(name, members, clanName, corrections);
      if (!corrected || isOcrFilteredName(corrected, filters)) return name;
      if (normalize(corrected) !== normalize(name)) appliedCorrections.push({ wrong: name, right: corrected });
      return corrected;
    });
  const exactKeys = new Set(exactNames.map(normalize));
  const rawCandidates = candidateNamesFromOcrTextSafe(text, precise);
  const blockedWords = new Set(['귀신', '귀신z', '로망', '운좋은', '운좋은사람들', '게헨나', '미분류', 'lv', 'level']);
  const canAutoConfirm = (raw, suggestion) => {
    const rawKey = normalizeForOcrMatch(raw);
    const nameKey = normalizeForOcrMatch(suggestion.member.characterName);
    const lengthGap = Math.abs(rawKey.length - nameKey.length);
    if (rawKey.length < 2 || suggestion.score < 0.76) return false;
    if (lengthGap > 2 && suggestion.score < 0.92) return false;
    return true;
  };
  const shouldShowAmbiguous = (raw, suggestions) => {
    if (isNoisyOcrCandidate(raw)) return false;
    const [best, second] = suggestions;
    if (!best) return false;
    const rawKey = normalizeForOcrMatch(raw);
    const nameKey = normalizeForOcrMatch(best.member.characterName);
    const lengthGap = Math.abs(rawKey.length - nameKey.length);
    if (best.score < 0.88) return false;
    if (lengthGap > 2 && best.score < 0.96) return false;
    if (rawKey.length <= 3 && best.score < 0.93) return false;
    if (second && best.score - second.score < 0.12) return false;
    return true;
  };
  const confident = [];
  const bestAmbiguousByMember = new Map();
  rawCandidates
    .filter((raw) => !isOcrFilteredName(raw, filters))
    .filter((raw) => !isNoisyOcrCandidate(raw))
    .filter((raw) => normalize(raw).length >= 2 && !exactKeys.has(normalize(raw)))
    .filter((raw) => /[가-힣]/.test(raw) || normalize(raw).length >= 4)
    .filter((raw) => !blockedWords.has(normalize(raw)))
    .map((raw) => {
      const corrected = findAutoCorrectedMember(raw, members, clanName, corrections);
      if (corrected && isOcrFilteredName(corrected, filters)) return null;
      if (corrected && !exactKeys.has(normalize(corrected))) {
        confident.push(corrected);
        exactKeys.add(normalize(corrected));
        appliedCorrections.push({ wrong: raw, right: corrected });
        return null;
      }
      return raw;
    })
    .filter(Boolean)
    .map((raw) => ({ raw, suggestions: findSimilarMembers(raw, members, clanName, precise ? 5 : 3) }))
    .filter((item) => {
      if (!shouldShowAmbiguous(item.raw, item.suggestions)) return false;
      if (precise) {
        const [best, second] = item.suggestions;
        const clearWinner = best.score >= 0.72 && (!second || best.score - second.score >= 0.04);
        const nearExact = best.score >= 0.82;
        if ((clearWinner || nearExact) && canAutoConfirm(item.raw, best)) {
          confident.push(best.member.characterName);
          exactKeys.add(normalize(best.member.characterName));
          return false;
        }
      }
      return true;
    })
    .forEach((item) => {
      const bestName = item.suggestions[0].member.characterName;
      const current = bestAmbiguousByMember.get(bestName);
      if (!current || ambiguousCandidateRank(item) > ambiguousCandidateRank(current)) bestAmbiguousByMember.set(bestName, item);
    });
  const ambiguous = [...bestAmbiguousByMember.values()]
    .sort((a, b) => ambiguousCandidateRank(b) - ambiguousCandidateRank(a))
    .slice(0, 1);
  return { exactNames: [...new Set([...exactNames, ...confident])], ambiguous, appliedCorrections };
}
function namesFromText(value) {
  return [...new Set(String(value ?? '').split(/\r?\n|,/).map((name) => name.trim()).filter(Boolean))];
}

const canvasToBlob = (canvas) => new Promise((resolve) => canvas.toBlob(resolve, 'image/png'));

const loadImageElement = (file) => new Promise((resolve, reject) => {
  const image = new Image();
  image.onload = () => resolve(image);
  image.onerror = reject;
  image.src = URL.createObjectURL(file);
});

async function createOcrVariants(file, precise = false) {
  const image = await loadImageElement(file);
  const makeCanvas = (scale, mode, threshold = 120) => {
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(image.naturalWidth * scale));
    canvas.height = Math.max(1, Math.round(image.naturalHeight * scale));
    const context = canvas.getContext('2d');
    context.imageSmoothingEnabled = false;
    context.drawImage(image, 0, 0, canvas.width, canvas.height);
    if (mode) {
      const imageData = context.getImageData(0, 0, canvas.width, canvas.height);
      const { data } = imageData;
      for (let index = 0; index < data.length; index += 4) {
        const gray = (data[index] * 0.299) + (data[index + 1] * 0.587) + (data[index + 2] * 0.114);
        const value = mode === 'threshold' ? (gray > threshold ? 255 : 0) : Math.max(0, Math.min(255, (gray - threshold) * 1.65 + 140));
        data[index] = value;
        data[index + 1] = value;
        data[index + 2] = value;
      }
      context.putImageData(imageData, 0, 0);
    }
    return canvas;
  };
  const baseVariants = [
    file,
    await canvasToBlob(makeCanvas(2, 'contrast')),
  ];
  if (!precise) return baseVariants.filter(Boolean);
  return [
    ...baseVariants,
    await canvasToBlob(makeCanvas(2.4, 'threshold')),
    await canvasToBlob(makeCanvas(2.8, 'contrast')),
    await canvasToBlob(makeCanvas(3.2, 'threshold')),
  ].filter(Boolean);
}

async function createNameSlotOcrVariants(file, precise = true) {
  const image = await loadImageElement(file);
  const makeCanvas = (scale, mode, threshold = 112) => {
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(image.naturalWidth * scale));
    canvas.height = Math.max(1, Math.round(image.naturalHeight * scale));
    const context = canvas.getContext('2d');
    context.imageSmoothingEnabled = false;
    context.fillStyle = '#000';
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.drawImage(image, 0, 0, canvas.width, canvas.height);
    const imageData = context.getImageData(0, 0, canvas.width, canvas.height);
    const { data } = imageData;
    for (let index = 0; index < data.length; index += 4) {
      const r = data[index];
      const g = data[index + 1];
      const b = data[index + 2];
      const gray = (r * 0.299) + (g * 0.587) + (b * 0.114);
      const boosted = Math.max(r, g, b);
      const value = mode === 'threshold'
        ? (boosted > threshold || gray > threshold ? 255 : 0)
        : Math.max(0, Math.min(255, (boosted - threshold) * 1.9 + 150));
      data[index] = value;
      data[index + 1] = value;
      data[index + 2] = value;
    }
    context.putImageData(imageData, 0, 0);
    return canvas;
  };
  const baseVariants = [
    file,
    await canvasToBlob(makeCanvas(3.2, 'contrast', 104)),
  ];
  if (!precise) return baseVariants.filter(Boolean);
  return [
    ...baseVariants,
    await canvasToBlob(makeCanvas(4, 'contrast', 108)),
    await canvasToBlob(makeCanvas(4.6, 'threshold', 102)),
    await canvasToBlob(makeCanvas(5.4, 'contrast', 100)),
  ].filter(Boolean);
}

async function recognizeOcrVariantsWithWorker(file, worker, onProgress, precise = false) {
  const variants = await createOcrVariants(file, precise);
  const texts = [];
  for (let index = 0; index < variants.length; index += 1) {
    onProgress?.(Math.round((index / variants.length) * 100));
    const { data } = await worker.recognize(variants[index]);
    texts.push(data.text);
  }
  onProgress?.(100);
  return texts.join('\n');
}

async function recognizeNameSlotWithWorker(file, worker, precise = true) {
  const variants = await createNameSlotOcrVariants(file, precise);
  const texts = [];
  const pageSegModes = precise ? ['7', '6'] : ['7'];
  for (const pageSegMode of pageSegModes) {
    await worker.setParameters({
      preserve_interword_spaces: '1',
      user_defined_dpi: precise ? '450' : '360',
      tessedit_pageseg_mode: pageSegMode,
    });
    for (let index = 0; index < variants.length; index += 1) {
      const { data } = await worker.recognize(variants[index]);
      texts.push(data.text);
    }
  }
  await worker.setParameters({
    preserve_interword_spaces: '1',
    user_defined_dpi: '300',
    tessedit_pageseg_mode: '6',
  });
  return texts.join('\n');
}

async function recognizeImageTextMultiple(file, onProgress) {
  const worker = await createWorker('kor+eng', 1, {
    logger: (log) => {
      if (log.status === 'recognizing text') onProgress?.(Math.round(log.progress * 100));
    },
  });
  try {
    await worker.setParameters({
      preserve_interword_spaces: '1',
      user_defined_dpi: '300',
      tessedit_pageseg_mode: '6',
    });
    return await recognizeOcrVariantsWithWorker(file, worker, onProgress);
  } finally {
    await worker.terminate();
  }
}

async function createPartyPanelFiles(file) {
  const image = await loadImageElement(file);
  const columns = 2;
  const bounds = detectRosterContentBounds(image);
  const cropX = bounds.x;
  const cropY = bounds.y;
  const cropWidth = bounds.width;
  const cropHeight = bounds.height;
  const panelWidth = Math.floor(cropWidth / columns);
  const estimatedPanelHeight = Math.max(88, Math.round(panelWidth * 0.24));
  const rows = Math.max(1, Math.min(5, Math.round(cropHeight / estimatedPanelHeight)));
  const panelHeight = Math.floor(cropHeight / rows);
  const panels = [];
  for (let row = 0; row < rows; row += 1) {
    for (let column = 0; column < columns; column += 1) {
      const canvas = document.createElement('canvas');
      canvas.width = panelWidth;
      canvas.height = row === rows - 1 ? cropHeight - (row * panelHeight) : panelHeight;
      const context = canvas.getContext('2d');
      context.imageSmoothingEnabled = false;
      context.drawImage(image, cropX + (column * panelWidth), cropY + (row * panelHeight), panelWidth, canvas.height, 0, 0, panelWidth, canvas.height);
      const blob = await canvasToBlob(canvas);
      if (blob) panels.push({ partyNumber: (row * columns) + column + 1, file: blob });
    }
  }
  return panels.slice(0, 10);
}

async function createPartyNameSlotFiles(file, precise = true) {
  const image = await loadImageElement(file);
  const slots = [];
  const slotCount = 5;
  const slotWidth = image.naturalWidth / slotCount;
  const cropBands = precise ? [
    { top: 0.30, height: 0.38, label: 'name-high' },
    { top: 0.34, height: 0.34, label: 'name-wide' },
    { top: 0.39, height: 0.30, label: 'name-mid' },
    { top: 0.43, height: 0.28, label: 'name-center' },
    { top: 0.38, height: 0.46, label: 'name-level' },
  ] : [
    { top: 0.34, height: 0.38, label: 'name-fast' },
  ];
  for (let index = 0; index < slotCount; index += 1) {
    const sx = Math.max(0, Math.round((index * slotWidth) - (slotWidth * 0.08)));
    const sw = Math.min(image.naturalWidth - sx, Math.round(slotWidth * 1.16));
    for (const band of cropBands) {
      const cropTop = Math.max(0, Math.round(image.naturalHeight * band.top));
      const cropHeight = Math.max(18, Math.round(image.naturalHeight * band.height));
      const canvas = document.createElement('canvas');
      canvas.width = sw;
      canvas.height = Math.min(cropHeight, image.naturalHeight - cropTop);
      const context = canvas.getContext('2d');
      context.imageSmoothingEnabled = false;
      context.drawImage(image, sx, cropTop, sw, canvas.height, 0, 0, sw, canvas.height);
      const blob = await canvasToBlob(canvas);
      if (blob) slots.push({ slotNumber: index + 1, band: band.label, file: blob });
    }
  }
  return slots;
}

function detectRosterContentBounds(image) {
  const canvas = document.createElement('canvas');
  canvas.width = image.naturalWidth;
  canvas.height = image.naturalHeight;
  const context = canvas.getContext('2d');
  context.drawImage(image, 0, 0);
  const { data, width, height } = context.getImageData(0, 0, canvas.width, canvas.height);
  const columnScores = Array(width).fill(0);
  const rowScores = Array(height).fill(0);
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const index = (y * width + x) * 4;
      const r = data[index];
      const g = data[index + 1];
      const b = data[index + 2];
      const brightness = (r + g + b) / 3;
      const isTextOrLine = brightness > 72 || (g > 85 && b > 65) || (r > 95 && g > 80 && b < 70);
      if (isTextOrLine) {
        columnScores[x] += 1;
        rowScores[y] += 1;
      }
    }
  }
  const findRange = (scores, minScore, padding, max) => {
    let start = 0;
    let end = max - 1;
    while (start < max && scores[start] < minScore) start += 1;
    while (end > start && scores[end] < minScore) end -= 1;
    return {
      start: Math.max(0, start - padding),
      end: Math.min(max - 1, end + padding),
    };
  };
  const xRange = findRange(columnScores, Math.max(3, Math.round(height * 0.025)), 10, width);
  const yRange = findRange(rowScores, Math.max(8, Math.round(width * 0.012)), 6, height);
  const detectedWidth = xRange.end - xRange.start + 1;
  const detectedHeight = yRange.end - yRange.start + 1;
  if (detectedWidth < width * 0.35 || detectedHeight < height * 0.45) {
    return { x: 0, y: 0, width, height };
  }
  return {
    x: xRange.start,
    y: yRange.start,
    width: detectedWidth,
    height: detectedHeight,
  };
}

async function recognizePartyPanels(file, members, onProgress, options = {}) {
  const panels = await createPartyPanelFiles(file);
  const worker = await createWorker('kor+eng', 1);
  const precise = Boolean(options.precise);
  const names = [];
  const ambiguous = [];
  const appliedCorrections = [];
  const panelTexts = [];
  let dominantClan = '';
  try {
    await worker.setParameters({
      preserve_interword_spaces: '1',
      user_defined_dpi: '300',
      tessedit_pageseg_mode: '6',
    });
    for (let index = 0; index < panels.length; index += 1) {
      const panel = panels[index];
      let text = await recognizeOcrVariantsWithWorker(panel.file, worker, (progressValue) => {
        const baseWeight = precise ? 0.55 : 1;
        const overall = Math.round(((index + ((progressValue / 100) * baseWeight)) / Math.max(1, panels.length)) * 100);
        onProgress?.(overall);
      }, precise);
      const quickReview = !precise ? buildOcrReview(text, members, '', options) : null;
      const needsAdaptiveSlotScan = !precise && quickReview.exactNames.length < 4;
      if (precise || needsAdaptiveSlotScan) {
        const slotFiles = await createPartyNameSlotFiles(panel.file, precise);
        for (let slotIndex = 0; slotIndex < slotFiles.length; slotIndex += 1) {
          const slot = slotFiles[slotIndex];
          const slotText = await recognizeNameSlotWithWorker(slot.file, worker, precise);
          text += `\n${slotText}`;
          const slotStart = precise ? 0.55 : 0.82;
          const slotWeight = precise ? 0.45 : 0.18;
          const overall = Math.round(((index + slotStart + (((slotIndex + 1) / Math.max(1, slotFiles.length)) * slotWeight)) / Math.max(1, panels.length)) * 100);
          onProgress?.(overall);
        }
      }
      panelTexts.push({ panel, text });
    }
    const firstPassReviews = panelTexts.map(({ text }) => buildOcrReview(text, members, '', options));
    dominantClan = inferDominantClanFromNames(firstPassReviews.flatMap((review) => review.exactNames), members);
    panelTexts.forEach(({ panel, text }, index) => {
      const review = dominantClan
        ? buildOcrReview(text, members, dominantClan, options)
        : firstPassReviews[index];
      names.push(...review.exactNames.map((name) => ({ name, positions: [panel.partyNumber] })));
      ambiguous.push(...review.ambiguous.map((item) => ({ ...item, positions: [panel.partyNumber] })));
      appliedCorrections.push(...(review.appliedCorrections || []).map((item) => ({ ...item, positions: [panel.partyNumber] })));
    });
    const confirmedNameKeys = new Set(names.map((item) => normalize(item.name)));
    const bestAmbiguousByMember = new Map();
    ambiguous.forEach((item) => {
      const bestName = item.suggestions?.[0]?.member?.characterName;
      if (!bestName || confirmedNameKeys.has(normalize(bestName))) return;
      const current = bestAmbiguousByMember.get(bestName);
      if (!current || ambiguousCandidateRank(item) > ambiguousCandidateRank(current)) {
        bestAmbiguousByMember.set(bestName, item);
      }
    });
    const limitedAmbiguous = [...bestAmbiguousByMember.values()]
      .sort((a, b) => ambiguousCandidateRank(b) - ambiguousCandidateRank(a))
      .slice(0, 7);
    ambiguous.splice(0, ambiguous.length, ...limitedAmbiguous);
  } finally {
    await worker.terminate();
  }
  onProgress?.(100);
  return { names, ambiguous, dominantClan, appliedCorrections };
}

function splitKoreanTime(value) {
  const raw = String(value ?? '').slice(0, 5);
  const [hourText = '0', minuteText = '00'] = raw.split(':');
  const hour = Number(hourText);
  const period = hour >= 12 ? '오후' : '오전';
  const displayHour = hour % 12 || 12;
  return { period, time: `${String(displayHour).padStart(2, '0')}:${minuteText}` };
}

function splitDateTimeKoreanTime(value) {
  if (!value) return { period: '', time: '-' };
  return splitKoreanTime(new Date(value).toTimeString().slice(0, 5));
}

function AuthScreen({ onLogin }) {
  const [isRegister, setIsRegister] = useState(false);
  const [form, setForm] = useState({ characterName: '', password: '', combatPower: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError('');
    try {
      if (isRegister) {
        await request('/auth/register', {
          method: 'POST',
          body: JSON.stringify({ ...form, combatPower: Number(form.combatPower || 0) }),
        });
      }
      const loggedIn = await request('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ characterName: form.characterName, password: form.password }),
      });
      onLogin(loggedIn);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="auth-page">
      <section className="auth-card light-auth">
        <div className="auth-mark">C</div>
        <p className="auth-kicker">CLAN MANAGER</p>
        <h1>{isRegister ? '클랜에 합류하기' : '클랜 매니저'}</h1>
        <p>캐릭터 정보와 클랜 활동을 한곳에서 관리하세요.</p>
        <form onSubmit={submit}>
          <label>캐릭터 이름<input required value={form.characterName} onChange={(e) => setForm({ ...form, characterName: e.target.value })} /></label>
          <label>비밀번호<input required type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></label>
          {isRegister && <label>전투력<input required type="number" value={form.combatPower} onChange={(e) => setForm({ ...form, combatPower: e.target.value })} /></label>}
          {error && <p className="form-error">{error}</p>}
          <button className="primary-button" disabled={loading}>{loading ? '처리 중...' : isRegister ? '회원가입' : '로그인'}</button>
        </form>
        <button className="link-button" onClick={() => setIsRegister(!isRegister)}>{isRegister ? '이미 계정이 있어요' : '처음 오셨나요? 회원가입'}</button>
      </section>
    </main>
  );
}

function FavoriteLinks({ favorites, setPage, emptyText = '왼쪽 메뉴의 ☆를 눌러 자주 쓰는 페이지를 추가하세요.' }) {
  return (
    <section className="white-card favorite-links">
      <div className="section-heading compact">
        <div>
          <h2>즐겨찾기</h2>
          <p className="subtle">자주 쓰는 화면을 바로 열 수 있습니다.</p>
        </div>
      </div>
      {favorites.length ? (
        <div className="favorite-link-grid">
          {favorites.map((item) => (
            <button type="button" key={item.id} onClick={() => setPage(item.id)}>
              <span>{item.icon}</span>
              <b>{item.label}</b>
            </button>
          ))}
        </div>
      ) : (
        <p className="favorite-empty">{emptyText}</p>
      )}
    </section>
  );
}

function Shell({ member, page, setPage, onLogout, children, favorites = [], toggleFavorite }) {
  const [collapsed, setCollapsed] = useState(false);
  const [theme, setTheme] = useState(() => localStorage.getItem('clanTheme') || 'light');
  const [favoriteOnly, setFavoriteOnly] = useState(() => localStorage.getItem('clanFavoriteOnlyMenu') === 'true');
  const visibleMenu = menu
    .filter(([id]) => member.role === 'ADMIN' || !adminOnlyPages.has(id))
    .filter(([id]) => !favoriteOnly || favorites.includes(id));
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('clanTheme', theme);
  }, [theme]);
  useEffect(() => {
    localStorage.setItem('clanFavoriteOnlyMenu', favoriteOnly ? 'true' : 'false');
  }, [favoriteOnly]);
  return (
    <div className={`shell ${collapsed ? 'collapsed' : ''}`}>
      <header className="topbar">
        <button className="hamburger" onClick={() => setCollapsed(!collapsed)}>☰</button>
        <div className="brand-mark">C</div>
        <div className="topbar-spacer" />
        <button className="circle-button" title={theme === 'dark' ? '화이트 모드' : '다크 모드'} onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}>{theme === 'dark' ? '☀' : '☾'}</button>
        <button className="profile-menu name-only"><span>{member.characterName}</span></button>
        <button className="logout-icon" title="로그아웃" onClick={onLogout}>⇥</button>
      </header>
      <aside className="sidebar">
        <div className="menu-view-toggle">
          <button type="button" className={!favoriteOnly ? 'active' : ''} onClick={() => setFavoriteOnly(false)}>전체</button>
          <button type="button" className={favoriteOnly ? 'active' : ''} onClick={() => setFavoriteOnly(true)}>즐겨찾기</button>
        </div>
        {favoriteOnly && !visibleMenu.length && (
          <div className="favorite-menu-empty">☆를 눌러 자주 쓰는 메뉴를 추가하세요.</div>
        )}
        <nav>{visibleMenu.map(([id, icon, label]) => {
          const starred = favorites.includes(id);
          return (
            <div key={id} className={page === id ? 'menu-row active' : 'menu-row'}>
              <button className="menu-item" onClick={() => setPage(id)}><span className="menu-icon">{icon}</span><span>{label}</span></button>
              <button
                type="button"
                className={starred ? 'favorite-toggle active' : 'favorite-toggle'}
                title={starred ? '즐겨찾기 해제' : '즐겨찾기 추가'}
                onClick={() => toggleFavorite?.(id)}
              >
                {starred ? '★' : '☆'}
              </button>
            </div>
          );
        })}</nav>
        <button type="button" className="side-note item-request-shortcut" onClick={() => setPage('item-request')}>
          <b>아이템 신청</b>
          <small>신청내역 확인 및 지급 처리</small>
          <span>→</span>
        </button>
      </aside>
      <main className="content">{children}</main>
    </div>
  );
}

function NoticePanel({ member, notices, onReload }) {
  const [open, setOpen] = useState(false);
  const [expanded, setExpanded] = useState(true);
  const [form, setForm] = useState({ title: '', content: '' });
  const [message, setMessage] = useState('');
  const canManage = member.role === 'ADMIN';

  const save = async (event) => {
    event.preventDefault();
    setMessage('');
    try {
      await request('/notices', { method: 'POST', body: JSON.stringify({ ...form, createdByMemberId: member.memberId }) });
      setForm({ title: '', content: '' });
      setOpen(false);
      await onReload();
    } catch (err) {
      setMessage(err.message);
    }
  };

  const remove = async (notice) => {
    if (!window.confirm(`공지 "${notice.title}"을 삭제할까요?`)) return;
    setMessage('');
    try {
      await request(`/notices/${notice.noticeId}?memberId=${member.memberId}`, { method: 'DELETE' });
      await onReload();
    } catch (err) {
      setMessage(err.message);
    }
  };

  return (
    <section className="white-card notices">
      <div className="section-heading">
        <h2>공지</h2>
        <div className="notice-actions">
          <button className="outline-button no-margin" onClick={() => setExpanded(!expanded)}>{expanded ? '공지 접기' : '공지 펼치기'}</button>
          {canManage && <button className="small-primary" onClick={() => setOpen(!open)}>+ 추가</button>}
        </div>
      </div>
      {open && <form className="inline-form" onSubmit={save}><input required placeholder="공지 제목" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /><textarea required placeholder="공지 내용" value={form.content} onChange={(e) => setForm({ ...form, content: e.target.value })} /><button className="primary-button">공지 등록</button>{message && <p className="form-error">{message}</p>}</form>}
      {expanded && (notices.length ? notices.map((n) => <article key={n.noticeId} className="notice-row"><div className="notice-row-head"><b>{n.title}</b>{canManage && <button className="notice-delete-button" onClick={() => remove(n)}>삭제</button>}</div><p>{n.content}</p><small>{new Date(n.createdAt).toLocaleString('ko-KR')}</small></article>) : <div className="empty-state">아직 등록된 공지사항이 없습니다.</div>)}
    </section>
  );
}

function Lobby({ member, setPage, favoritePages = [] }) {
  const [notices, setNotices] = useState([]);
  const [members, setMembers] = useState([]);
  const [attendances, setAttendances] = useState([]);
  const [message, setMessage] = useState('');
  const load = async () => {
    const [noticeResult, memberResult, attendanceResult] = await Promise.allSettled([
      request('/notices'),
      request('/members'),
      request('/attendances'),
    ]);

    if (noticeResult.status === 'fulfilled') setNotices(Array.isArray(noticeResult.value) ? noticeResult.value : []);
    if (memberResult.status === 'fulfilled') setMembers(Array.isArray(memberResult.value) ? memberResult.value : []);
    if (attendanceResult.status === 'fulfilled') setAttendances(Array.isArray(attendanceResult.value) ? attendanceResult.value : []);

    const failed = [
      noticeResult.status === 'rejected' ? '공지' : null,
      memberResult.status === 'rejected' ? '클랜원' : null,
      attendanceResult.status === 'rejected' ? '참여기록' : null,
    ].filter(Boolean);
    setMessage(failed.length ? `${failed.join(', ')} 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.` : '');
  };
  useEffect(() => { load(); }, []);
  const participationRows = useMemo(() => {
    const counts = new Map();
    attendances.filter((row) => row.status === 'ATTENDED').forEach((row) => {
      const memberId = toMemberId(row);
      if (memberId !== null) counts.set(memberId, (counts.get(memberId) || 0) + 1);
    });
    const top = Math.max(0, ...members.map((m) => counts.get(toMemberId(m)) || 0));
    return members.map((m) => {
      const count = counts.get(toMemberId(m)) || 0;
      return { ...m, attendanceCount: count, participationRate: top ? Math.round((count / top) * 1000) / 10 : 0 };
    }).sort((a, b) => b.attendanceCount - a.attendanceCount || (b.combatPower || 0) - (a.combatPower || 0));
  }, [members, attendances]);
  return (
    <>
      <NoticePanel member={member} notices={notices} onReload={load} />
      {message && <div className="info-banner warning-banner">{message}</div>}
      <div className="page-title center"><h1>클랜 종합정보</h1><p>클랜별 참여율과 참여점수를 한눈에 확인합니다.</p></div>
      <FavoriteLinks favorites={favoritePages} setPage={setPage} />
      <ParticipationRanking rows={participationRows} totalCount={members.length} />
      <section className="white-card quick-actions"><h2>빠른 메뉴</h2><div><button onClick={() => setPage('attendance')}>오늘의 출석 확인</button><button onClick={() => setPage('participation')}>참여율 조회</button>{member.role === 'ADMIN' && <button onClick={() => setPage('ledger')}>클랜금고 관리</button>}</div></section>
    </>
  );
}

function ParticipationRanking({ rows, totalCount }) {
  const targetClans = clanDisplayOrder.slice(0, 4);
  const grouped = new Map(groupByClan(rows));

  return (
    <section className="white-card lobby-participation-card">
      <div className="section-heading">
        <div>
          <h2>🏆 클랜별 참여율 순위</h2>
          <p className="subtle">각 클랜별 상위 10명을 2x2로 보기 좋게 표시합니다.</p>
        </div>
        <span className="result-count">전체 {totalCount}명</span>
      </div>
      <div className="lobby-ranking-grid">
        {targetClans.map((clan) => {
          const list = grouped.get(clan) ?? [];
          return (
            <div className="clan-ranking-block lobby-ranking-card" key={clan}>
              <div className="section-heading">
                <h3>{clan}</h3>
                <span className="result-count">{list.length}명</span>
              </div>
              {list.length ? (
                <>
                  <table className="lobby-ranking-table">
                    <thead>
                      <tr>
                        <th>순위</th>
                        <th>닉네임</th>
                        <th>참여점수</th>
                        <th>참여율</th>
                      </tr>
                    </thead>
                    <tbody>
                      {list.slice(0, 10).map((m, i) => (
                        <tr key={m.memberId}>
                          <td>{i < 3 ? ['🥇', '🥈', '🥉'][i] : i + 1}</td>
                          <td>{m.characterName}</td>
                          <td>{m.attendanceCount}</td>
                          <td>{m.participationRate}%</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <p className="ranking-footnote">상위 10명 표시 중</p>
                </>
              ) : (
                <div className="empty-state compact">아직 참여 데이터가 없습니다.</div>
              )}
            </div>
          );
        })}
      </div>
      {!rows.length && <div className="empty-state">등록된 클랜원이 없습니다.</div>}
    </section>
  );
}

function Ranking({ title, rows, field, power, participation }) {
  return <section className="white-card ranking"><h2>{title}</h2><table><thead><tr><th>순위</th><th>닉네임</th>{participation ? <><th>참여횟수</th><th>참여율</th></> : <th>{field}</th>}</tr></thead><tbody>{rows.slice(0, 10).map((m, i) => <tr key={m.memberId}><td>{i < 3 ? ['🥇', '🥈', '🥉'][i] : i + 1}</td><td>{m.characterName}</td>{participation ? <><td>{m.attendanceCount}회</td><td className="blue-text">{m.participationRate}%</td></> : <td>{power ? formatNumber(m.combatPower) : '-'}</td>}</tr>)}</tbody></table>{!rows.length && <div className="empty-state">등록된 클랜원이 없습니다.</div>}</section>;
}

function AdminBackButton({ setPage }) {
  return setPage ? <button className="admin-back-button" onClick={() => setPage('admin')}>← 관리자설정으로</button> : null;
}

function MyInfo({ member }) {
  const [info, setInfo] = useState(null);
  useEffect(() => { request(`/members/${member.memberId}/my-info`).then(setInfo).catch(() => {}); }, [member.memberId]);
  if (!info) return <LoadingCard />;
  return <><div className="page-title"><h1>내 정보 확인</h1></div><ProfileCard member={member} info={info} /></>;
}

function ProfileCard({ member, info }) {
  const canSeeCombatPower = member?.role === 'ADMIN';
  return (
    <section className="white-card profile-overview">
      <div className="profile-name"><span className="avatar">{info.characterName.slice(0, 1)}</span><div><h2>{info.characterName}</h2><div className="pills"><span>{member.role === 'ADMIN' ? '운영자' : '클랜원'}</span>{canSeeCombatPower && <span>전투력 {formatNumber(info.combatPower)}</span>}</div></div></div>
      <div className="metric-grid"><Metric label="현재 참여율" value={`${info.participationRate}%`} caption={`참여 ${info.myAttendanceCount}회 / 1등 ${info.topAttendanceCount}회`} tone="blue" /><Metric label="기여율" value={`${info.participationRate}%`} caption="최고 참여 횟수를 100%로 계산" tone="green" /><Metric label="참석 횟수" value={`${info.myAttendanceCount}회`} caption="전체 활동 기준" tone="purple" /></div>
      <div className="formula-row"><div><b>참여율 계산 기준</b><p>내 참석 횟수 / 가장 많이 참석한 캐릭터의 참석 횟수 × 100</p></div><div><b>현재 100% 기준</b><p>{info.topAttendanceCount}회</p></div></div>
    </section>
  );
}

function Metric({ label, value, caption, tone }) { return <div className={`metric ${tone}`}><p>{label}</p><strong>{value}</strong><small>{caption}</small></div>; }

function Participation({ member, setPage }) {
  const [members, setMembers] = useState([]);
  const [attendances, setAttendances] = useState([]);
  const [periodIndex, setPeriodIndex] = useState(getCurrentParticipationPeriodIndex());
  const [periodNames, setPeriodNames] = useState({});
  const [editingPeriodName, setEditingPeriodName] = useState('');
  const [periodMessage, setPeriodMessage] = useState('');
  const [searchName, setSearchName] = useState('');
  const [memberBossHistory, setMemberBossHistory] = useState([]);
  const [memberBossHistoryLoading, setMemberBossHistoryLoading] = useState(false);

  useEffect(() => {
    Promise.allSettled([request('/members'), request('/attendances'), request('/participation-periods')]).then(([memberResult, attendanceResult, periodResult]) => {
      if (memberResult.status === 'fulfilled') setMembers(Array.isArray(memberResult.value) ? memberResult.value : []);
      if (attendanceResult.status === 'fulfilled') setAttendances(Array.isArray(attendanceResult.value) ? attendanceResult.value : []);
      if (periodResult.status === 'fulfilled' && Array.isArray(periodResult.value)) {
        setPeriodNames(Object.fromEntries(periodResult.value.map((period) => [period.periodIndex, period.periodName])));
      }
    });
  }, []);

  const period = getParticipationPeriod(periodIndex);
  const periodName = periodNames[periodIndex] || defaultPeriodName(period);
  useEffect(() => { setEditingPeriodName(periodName); }, [periodName]);
  const periodOptions = useMemo(() => {
    const current = getCurrentParticipationPeriodIndex();
    const maxIndex = Math.max(current + 2, periodIndex + 1);
    return Array.from({ length: maxIndex + 1 }, (_, index) => getParticipationPeriod(index));
  }, [periodIndex]);

  const filteredAttendances = useMemo(() => attendances.filter((row) => {
    if (row.status !== 'ATTENDED') return false;
    const attendanceDate = row.attendanceDate;
    return attendanceDate >= period.start && attendanceDate < period.end;
  }), [attendances, period.start, period.end]);

  const rows = useMemo(() => {
    const counts = new Map();
    filteredAttendances.forEach((row) => {
      const memberId = toMemberId(row);
      if (memberId !== null) counts.set(memberId, (counts.get(memberId) || 0) + 1);
    });
    const top = Math.max(0, ...members.map((m) => counts.get(toMemberId(m)) || 0));
    return members.map((m) => {
      const count = counts.get(toMemberId(m)) || 0;
      return { ...m, count, rate: top ? Math.round((count / top) * 1000) / 10 : 0, topCount: top };
    }).sort((a, b) => b.count - a.count || (b.combatPower || 0) - (a.combatPower || 0));
  }, [members, filteredAttendances]);

  const groups = groupByClan(rows);
  const searchedMember = useMemo(() => {
    const keyword = normalize(searchName);
    if (!keyword) return null;
    return rows.find((m) => normalize(m.characterName).includes(keyword)) || null;
  }, [rows, searchName]);
  useEffect(() => {
    if (!searchedMember?.memberId) {
      setMemberBossHistory([]);
      return;
    }
    setMemberBossHistoryLoading(true);
    request(`/boss-participations/member/${searchedMember.memberId}`)
      .then((history) => setMemberBossHistory(Array.isArray(history) ? history : []))
      .catch(() => setMemberBossHistory([]))
      .finally(() => setMemberBossHistoryLoading(false));
  }, [searchedMember?.memberId]);
  const canSeeCombatPower = member?.role === 'ADMIN';

  const savePeriodName = async () => {
    if (member?.role !== 'ADMIN') return;
    setPeriodMessage('');
    try {
      const saved = await request(`/participation-periods/${periodIndex}?adminMemberId=${member.memberId}`, {
        method: 'PUT',
        body: JSON.stringify({ startDate: period.start, endDate: period.end, periodName: editingPeriodName }),
      });
      setPeriodNames((prev) => ({ ...prev, [saved.periodIndex]: saved.periodName }));
      setPeriodMessage('회차 이름을 저장했습니다.');
    } catch (err) {
      setPeriodMessage(err.message);
    }
  };

  return (
    <>
      <AdminBackButton setPage={setPage} />
      <div className="page-title">
        <h1>참여율·기여율 조회</h1>
        <p>2주 회차별로 참여 횟수를 계산하고, 가장 많이 참석한 캐릭터를 100%로 잡습니다.</p>
      </div>
      <section className="white-card participation-period-card">
        <div className="info-banner">
          <b>참여율 계산 기준</b>
          <span>선택 회차 내 참석 횟수 / 회차 1등 참석 횟수 × 100</span>
        </div>
        <div className="participation-controls">
          <label>2주 회차
            <select value={periodIndex} onChange={(e) => setPeriodIndex(Number(e.target.value))}>
              {periodOptions.map((option) => (
                <option key={option.index} value={option.index}>{periodNames[option.index] || defaultPeriodName(option)}</option>
              ))}
            </select>
          </label>
          <label>회차 이름
            <input value={editingPeriodName} onChange={(e) => setEditingPeriodName(e.target.value)} placeholder="예: 7월 1차 보스 참여율" disabled={member?.role !== 'ADMIN'} />
          </label>
          <label>이름 검색
            <input value={searchName} onChange={(e) => setSearchName(e.target.value)} placeholder="캐릭터 이름 입력" />
          </label>
        </div>
        {member?.role === 'ADMIN' && <button className="primary-button period-save-button" onClick={savePeriodName}>회차 이름 저장</button>}
        {periodMessage && <p className="vault-message">{periodMessage}</p>}
        <p className="subtle">현재 기간: {period.start} 00:00 이상 ~ {period.end} 00:00 미만 · 다음 회차는 {period.end}부터 시작합니다.</p>
        {searchName && (
          <div className="participation-search-result">
            {searchedMember ? (
              <>
                <div>
                  <b>{searchedMember.characterName}</b>
                  <span>{searchedMember.guildName || '-'} · {searchedMember.characterClass || '-'}</span>
                </div>
                <div><small>참석</small><strong>{searchedMember.count}회</strong></div>
                <div><small>참여율</small><strong>{searchedMember.rate}%</strong></div>
                {canSeeCombatPower && <div><small>전투력</small><strong>{formatNumber(searchedMember.combatPower)}</strong></div>}
              </>
            ) : <p>검색한 이름의 클랜원을 찾지 못했습니다.</p>}
          </div>
        )}
        {searchedMember && (
          <div className="participation-history-card">
            <div className="section-heading">
              <h2>{searchedMember.characterName} 보스 참여 기록</h2>
              <span className="result-count">{memberBossHistory.length}건</span>
            </div>
            {memberBossHistoryLoading ? (
              <div className="empty-state">참여 기록을 불러오는 중입니다.</div>
            ) : memberBossHistory.length ? (
              <div className="participation-history-grid">
                {memberBossHistory.slice(0, 20).map((row) => (
                  <div className="participation-history-item" key={`${row.recordId}-${row.bossName}-${row.bossDate}`}>
                    <b>{row.bossDate}</b>
                    <span>{String(row.cutTime || '').slice(0, 5)} · {row.bossName}</span>
                    <small>{row.clanName || '-'} · {row.score || 1}점</small>
                  </div>
                ))}
              </div>
            ) : (
              <div className="empty-state">아직 보스 참여 기록이 없습니다.</div>
            )}
          </div>
        )}
      </section>
      <section className="white-card">
        {groups.map(([clan, list]) => (
          <div className="clan-ranking-block" key={clan}>
            <div className="section-heading"><h2>{clan}</h2><span className="result-count">{list.length}명</span></div>
            <div className="table-wrap">
              <table className="data-table">
                <thead><tr><th>순위</th><th>닉네임</th><th>참석</th><th>참여율</th><th>기여율</th></tr></thead>
                <tbody>{list.map((m, i) => <tr key={m.memberId}><td>{i + 1}</td><td>{m.characterName}</td><td>{m.count}회</td><td className="blue-text">{m.rate}%</td><td className="green-text">{m.rate}%</td></tr>)}</tbody>
              </table>
            </div>
          </div>
        ))}
        {!rows.length && <div className="empty-state">클랜원이 등록되면 이곳에 순위가 표시됩니다.</div>}
      </section>
    </>
  );
}

function Attendance({ member, setPage }) {
  const [records, setRecords] = useState([]);
  const [members, setMembers] = useState([]);
  const [batchRows, setBatchRows] = useState(() => bossCheckSlots.map((slot) => ({
    ...slot,
    files: [],
    fileReviews: [],
    names: [],
    ambiguous: [],
    appliedCorrections: [],
    cutInput: slot.cutTime,
    longTerm: false,
    doubleScore: false,
    precise: false,
    scanning: false,
    progress: 0,
    savedRecord: null,
    message: '',
  })));
  const [selectedRecord, setSelectedRecord] = useState(null);
  const [selectedClan, setSelectedClan] = useState('');
  const [selectedMembers, setSelectedMembers] = useState([]);
  const [selectedDraftByClan, setSelectedDraftByClan] = useState({});
  const [savingRoster, setSavingRoster] = useState(false);
  const [reviewEdit, setReviewEdit] = useState(null);
  const [form, setForm] = useState({ bossDate: today(), cutTime: '21:00', bossName: '21시 보스', score: 1, clanName: '로망', memo: '' });
  const [draftByClan, setDraftByClan] = useState({});
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState('');
  const [ocrStatus, setOcrStatus] = useState('');
  const [ocrAmbiguous, setOcrAmbiguous] = useState([]);
  const [draftEdit, setDraftEdit] = useState(null);
  const [ocrEdit, setOcrEdit] = useState(null);
  const [batchEdit, setBatchEdit] = useState(null);
  const [progress, setProgress] = useState(0);
  const [message, setMessage] = useState('');
  const [showCorrectionPanel, setShowCorrectionPanel] = useState(false);
  const [ocrCorrections, setOcrCorrections] = useState(() => readStoredOcrCorrections());
  const [correctionDraft, setCorrectionDraft] = useState({ wrong: '', right: '' });
  const [ocrFilters, setOcrFilters] = useState(() => readStoredOcrFilters());
  const [filterDraft, setFilterDraft] = useState('');

  const currentDraftNames = draftByClan[form.clanName] ?? '';
  const totalDraftCount = Object.values(draftByClan).reduce((sum, text) => sum + namesFromText(text).length, 0);
  const correctionEntries = useMemo(() => Object.entries(ocrCorrections).sort(([a], [b]) => a.localeCompare(b, 'ko-KR')), [ocrCorrections]);
  const filterEntries = useMemo(() => [...ocrFilters].sort((a, b) => a.localeCompare(b, 'ko-KR')), [ocrFilters]);
  const saveOcrCorrection = () => {
    const wrong = normalize(correctionDraft.wrong);
    const right = correctionDraft.right.trim();
    if (!wrong || !right) return;
    const next = { ...ocrCorrections, [wrong]: right };
    setOcrCorrections(next);
    saveStoredOcrCorrections(next);
    setCorrectionDraft({ wrong: '', right: '' });
  };
  const removeOcrCorrection = (wrongKey) => {
    const next = { ...ocrCorrections };
    delete next[wrongKey];
    setOcrCorrections(next);
    saveStoredOcrCorrections(next);
  };
  const saveOcrFilter = () => {
    const filter = normalize(filterDraft);
    if (!filter) return;
    const next = [...new Set([...ocrFilters, filter])];
    setOcrFilters(next);
    saveStoredOcrFilters(next);
    setFilterDraft('');
  };
  const removeOcrFilter = (filter) => {
    const next = ocrFilters.filter((item) => item !== filter);
    setOcrFilters(next);
    saveStoredOcrFilters(next);
  };
  const memberByNormalizedName = useMemo(() => new Map(members.map((candidate) => [normalize(candidate.characterName), candidate])), [members]);
  const memberNameSuggestions = (value, limit = 6) => {
    const query = normalize(value);
    if (!query) return members.slice(0, limit);
    return members
      .map((candidate) => ({
        member: candidate,
        score: normalize(candidate.characterName).includes(query) ? 1 : similarityScore(value, candidate.characterName),
      }))
      .filter(({ score }) => score >= 0.35)
      .sort((a, b) => b.score - a.score || a.member.characterName.localeCompare(b.member.characterName, 'ko-KR'))
      .slice(0, limit)
      .map(({ member: candidate }) => candidate);
  };
  const normalizeSources = (sources = []) => {
    const map = new Map();
    sources.filter(Boolean).forEach((source) => {
      const fileIndex = Number(source.fileIndex || 1);
      const position = Number(source.position || 0);
      if (!position) return;
      const fileName = source.fileName || `사진 ${fileIndex}`;
      map.set(`${fileIndex}:${position}:${fileName}`, { fileIndex, fileName, position });
    });
    return [...map.values()].sort((a, b) => (a.fileIndex - b.fileIndex) || (a.position - b.position));
  };
  const sourcesFromPositions = (positions = [], fileIndex = 1, fileName = `사진 ${fileIndex}`) => (
    (positions || []).map((position) => ({ fileIndex, fileName, position }))
  );
  const fallbackSources = (item, fallbackFileIndex = 1, fallbackFileName = `사진 ${fallbackFileIndex}`) => {
    const positions = item.positions?.length ? item.positions : [99];
    return sourcesFromPositions(positions, item.fileIndex || fallbackFileIndex, item.fileName || fallbackFileName);
  };
  const classifyRecognizedName = (name, positions = [], sources = []) => {
    const matchedMember = memberByNormalizedName.get(normalize(name));
    const clanName = matchedMember ? canonicalClanName(matchedMember.guildName || matchedMember.clanName) : '미분류';
    return { name, matched: Boolean(matchedMember), clanName, positions, sources: normalizeSources(sources) };
  };
  const uniqueRecognizedRows = (rows) => {
    const map = new Map();
    rows.filter((row) => row.name).forEach((row) => {
      const key = normalize(row.name);
      const previous = map.get(key);
      map.set(key, previous ? {
        ...previous,
        positions: [...new Set([...(previous.positions || []), ...(row.positions || [])])].sort((a, b) => a - b),
        sources: normalizeSources([...(previous.sources || []), ...(row.sources || [])]),
      } : { ...row, sources: normalizeSources(row.sources || sourcesFromPositions(row.positions)) });
    });
    return [...map.values()];
  };
  const groupedBatchReview = (row) => {
    const fileMap = new Map();
    const reviewFiles = row.fileReviews?.length ? row.fileReviews : (row.files || []).map((fileItem, index) => ({
      fileIndex: index + 1,
      fileName: fileItem?.name || `사진 ${index + 1}`,
    }));
    reviewFiles.forEach((fileItem, index) => {
      const fileIndex = index + 1;
      const reviewFileIndex = fileItem.fileIndex || fileIndex;
      const fileName = fileItem.fileName || `사진 ${reviewFileIndex}`;
      fileMap.set(`${reviewFileIndex}:${fileName}`, {
        fileIndex: reviewFileIndex,
        fileName,
        previewUrl: fileItem.previewUrl || '',
        dominantClan: fileItem.dominantClan || '',
        positions: new Map(),
      });
    });
    const ensurePosition = (source) => {
      const fileIndex = Number(source.fileIndex || 1);
      const fileName = source.fileName || `사진 ${fileIndex}`;
      const position = Number(source.position || 0) || 99;
      const fileKey = `${fileIndex}:${fileName}`;
      if (!fileMap.has(fileKey)) fileMap.set(fileKey, { fileIndex, fileName, positions: new Map() });
      const fileGroup = fileMap.get(fileKey);
      if (!fileGroup.positions.has(position)) fileGroup.positions.set(position, { position, names: [], ambiguous: [] });
      return fileGroup.positions.get(position);
    };
    (row.names || []).forEach((item) => {
      const sources = item.sources?.length ? item.sources : fallbackSources(item);
      sources.forEach((source) => {
        ensurePosition(source).names.push(item);
      });
    });
    (row.ambiguous || []).forEach((item) => {
      const sources = item.sources?.length ? item.sources : fallbackSources(item);
      sources.forEach((source) => {
        ensurePosition(source).ambiguous.push(item);
      });
    });
    return [...fileMap.values()]
      .sort((a, b) => a.fileIndex - b.fileIndex)
      .map((fileGroup) => ({
        ...fileGroup,
        positions: [...fileGroup.positions.values()].sort((a, b) => a.position - b.position),
      }));
  };
  const updateBatchRow = (key, updater) => setBatchRows((prev) => prev.map((row) => (
    row.key === key ? { ...row, ...(typeof updater === 'function' ? updater(row) : updater) } : row
  )));
  const addBatchFiles = (key, fileList) => {
    const nextFiles = [...(fileList || [])].filter((fileItem) => fileItem.type?.startsWith('image/'));
    if (!nextFiles.length) return;
    updateBatchRow(key, (row) => {
      const existingFiles = row.files || [];
      const existingReviews = row.fileReviews || [];
      const seen = new Set(existingFiles.map((fileItem) => `${fileItem.name}:${fileItem.size}:${fileItem.lastModified}`));
      const uniqueNextFiles = nextFiles.filter((fileItem) => {
        const fileKey = `${fileItem.name}:${fileItem.size}:${fileItem.lastModified}`;
        if (seen.has(fileKey)) return false;
        seen.add(fileKey);
        return true;
      });
      if (!uniqueNextFiles.length) return { message: `${existingFiles.length}장 선택됨 · 이미 추가된 사진은 제외` };
      const files = [...existingFiles, ...uniqueNextFiles];
      const newReviews = uniqueNextFiles.map((fileItem, index) => ({
        fileIndex: existingFiles.length + index + 1,
        fileName: fileItem.name || `사진 ${existingFiles.length + index + 1}`,
        previewUrl: URL.createObjectURL(fileItem),
        dominantClan: '',
      }));
      const fileReviews = [...existingReviews, ...newReviews].map((review, index) => ({
        ...review,
        fileIndex: index + 1,
        fileName: review.fileName || `사진 ${index + 1}`,
      }));
      return {
        files,
        fileReviews,
        names: [],
        ambiguous: [],
        appliedCorrections: [],
        savedRecord: null,
        message: `${files.length}장 선택됨 · 인식하려면 다시 글자인식을 눌러주세요`,
      };
    });
  };
  const removeBatchFile = (key, fileIndex) => {
    updateBatchRow(key, (row) => {
      const removeIndex = Number(fileIndex) - 1;
      const files = (row.files || []).filter((_, index) => index !== removeIndex);
      const fileReviews = (row.fileReviews || [])
        .filter((_, index) => index !== removeIndex)
        .map((review, index) => ({ ...review, fileIndex: index + 1 }));
      return {
        files,
        fileReviews,
        names: [],
        ambiguous: [],
        appliedCorrections: [],
        savedRecord: null,
        message: files.length ? `${files.length}장 선택됨 · 인식하려면 다시 글자인식을 눌러주세요` : '',
      };
    });
  };
  const selectBatchFiles = (key, event) => {
    addBatchFiles(key, event.target.files);
    event.target.value = '';
  };
  const dropBatchFiles = (key, event) => {
    event.preventDefault();
    addBatchFiles(key, event.dataTransfer.files);
  };
  const scanBatchRow = async (key) => {
    const row = batchRows.find((item) => item.key === key);
    if (!row?.files?.length) return;
    updateBatchRow(key, { scanning: true, progress: 0, message: '사진 글자를 읽는 중입니다.', ambiguous: [], appliedCorrections: [] });
    try {
      const foundNames = [];
      const foundAmbiguous = [];
      const appliedCorrections = [];
      const fileReviews = row.files.map((fileItem, index) => ({
        ...(row.fileReviews?.[index] || {}),
        fileIndex: index + 1,
        fileName: fileItem?.name || `사진 ${index + 1}`,
      }));
      for (let index = 0; index < row.files.length; index += 1) {
        const fileIndex = index + 1;
        const fileName = row.files[index]?.name || `사진 ${fileIndex}`;
        const result = await recognizePartyPanels(row.files[index], members, (progressValue) => {
          const overall = Math.round(((index + (progressValue / 100)) / row.files.length) * 100);
          updateBatchRow(key, { progress: overall });
        }, { precise: row.precise, corrections: ocrCorrections, filters: ocrFilters });
        fileReviews[index] = {
          ...fileReviews[index],
          dominantClan: result.dominantClan || '',
          recognizedCount: result.names.length,
          reviewCount: result.ambiguous.length,
        };
        foundNames.push(...result.names.map((item) => ({
          ...item,
          fileIndex,
          fileName,
          sources: sourcesFromPositions(item.positions, fileIndex, fileName),
        })));
        foundAmbiguous.push(...result.ambiguous.map((item, ambiguousIndex) => ({
          ...item,
          id: `${key}-${fileIndex}-${ambiguousIndex}-${item.raw}`,
          fileIndex,
          fileName,
          sources: sourcesFromPositions(item.positions, fileIndex, fileName),
        })));
        appliedCorrections.push(...(result.appliedCorrections || []).map((item, correctionIndex) => ({
          ...item,
          id: `${key}-${fileIndex}-${correctionIndex}-${item.wrong}-${item.right}`,
          fileIndex,
          fileName,
          sources: sourcesFromPositions(item.positions, fileIndex, fileName),
        })));
      }
      const names = uniqueRecognizedRows(foundNames.map((item) => classifyRecognizedName(item.name, item.positions, item.sources)));
      updateBatchRow(key, { names, ambiguous: foundAmbiguous, appliedCorrections, fileReviews, scanning: false, progress: 100, message: `${names.length}명 인식됨 · 확인필요 ${names.filter((item) => !item.matched).length + foundAmbiguous.length}개` });
    } catch (err) {
      updateBatchRow(key, { scanning: false, message: `OCR 실패: ${err.message}` });
    }
  };
  const updateBatchName = (key, oldName, nextName) => {
    const trimmed = nextName.trim();
    if (!trimmed) return;
    updateBatchRow(key, (row) => {
      const old = row.names.find((item) => item.name === oldName);
      const next = classifyRecognizedName(trimmed, old?.positions || [], old?.sources || []);
      return { names: uniqueRecognizedRows(row.names.map((item) => item.name === oldName ? next : item)) };
    });
  };
  const removeBatchName = (key, targetName) => updateBatchRow(key, (row) => ({ names: row.names.filter((item) => item.name !== targetName) }));
  const resolveBatchAmbiguous = (key, ambiguousKey, name) => updateBatchRow(key, (row) => {
    const trimmed = name.trim();
    if (!trimmed) return {};
    const target = row.ambiguous.find((item) => (item.id || item.raw) === ambiguousKey);
    return {
      names: uniqueRecognizedRows([...row.names, classifyRecognizedName(trimmed, target?.positions || [], target?.sources || [])]),
      ambiguous: row.ambiguous.filter((item) => (item.id || item.raw) !== ambiguousKey),
    };
  });
  const ignoreBatchAmbiguous = (key, ambiguousKey) => updateBatchRow(key, (row) => ({ ambiguous: row.ambiguous.filter((item) => (item.id || item.raw) !== ambiguousKey) }));
  const batchClanCounts = (names) => names.reduce((acc, item) => {
    acc[item.clanName] = (acc[item.clanName] || 0) + 1;
    return acc;
  }, {});
  const saveBatchRow = async (key) => {
    const row = batchRows.find((item) => item.key === key);
    if (!row) return;
    const confirmedNames = row.names.filter((item) => item.matched);
    const skippedCount = row.names.filter((item) => !item.matched).length + row.ambiguous.length;
    if (!confirmedNames.length) {
      updateBatchRow(key, { message: '먼저 사진을 넣고 인식해 주세요.' });
      return;
    }
    try {
      const saved = await request('/boss-participations', {
        method: 'POST',
        body: JSON.stringify({
          createdByMemberId: member.memberId,
          bossDate: form.bossDate,
          cutTime: row.cutInput || row.cutTime,
          bossName: row.bossName,
          score: Number(row.score || 1) * (row.doubleScore ? 2 : 1),
          memo: [row.longTerm ? '장기전' : '', row.doubleScore ? '새벽/쟁 일정 2배' : ''].filter(Boolean).join(', '),
          members: confirmedNames.map((item) => ({ characterName: item.name, clanName: item.clanName })),
        }),
      });
      updateBatchRow(key, { savedRecord: saved, message: `${saved.bossName} ${saved.totalCount}명 저장 완료${skippedCount ? ` · 주황색 ${skippedCount}개 제외` : ''}` });
      await load();
    } catch (err) {
      updateBatchRow(key, { message: err.message });
    }
  };
  const updateCurrentDraft = (value) => setDraftByClan((prev) => ({ ...prev, [form.clanName]: value }));
  const isRegisteredDraftName = (name, clanName = form.clanName) => members.some((candidate) => (
    normalize(candidate.characterName) === normalize(name)
    && (!clanName || canonicalClanName(candidate.guildName || candidate.clanName) === canonicalClanName(clanName))
  ));
  const addDraftName = (name) => {
    const merged = [...new Set([...namesFromText(currentDraftNames), name])];
    updateCurrentDraft(merged.join('\n'));
  };
  const replaceDraftName = (oldName, nextName) => {
    const list = namesFromText(currentDraftNames);
    updateCurrentDraft(list.map((name) => name === oldName ? nextName.trim() : name).filter(Boolean).join('\n'));
    setDraftEdit(null);
  };
  const removeDraftName = (targetName) => {
    updateCurrentDraft(namesFromText(currentDraftNames).filter((name) => name !== targetName).join('\n'));
    setDraftEdit(null);
  };
  const addResolvedOcrName = (raw, name) => {
    addDraftName(name);
    setOcrAmbiguous((prev) => prev.filter((item) => item.raw !== raw));
    setOcrEdit(null);
  };
  const ignoreOcrName = (raw) => {
    setOcrAmbiguous((prev) => prev.filter((item) => item.raw !== raw));
    setOcrEdit(null);
  };
  const rowsToDraftByClan = (rows) => rows.reduce((acc, row) => {
    const clanName = clanOptions.includes(row.clanName) ? row.clanName : clanOptions[0];
    acc[clanName] = [...namesFromText(acc[clanName] || ''), row.characterName].filter(Boolean).join('\n');
    return acc;
  }, {});
  const replaceSelectedDraftName = (clanName, oldName, nextName) => {
    const trimmed = nextName.trim();
    setSelectedDraftByClan((prev) => {
      const list = namesFromText(prev[clanName] || '');
      return { ...prev, [clanName]: list.map((name) => name === oldName ? trimmed : name).filter(Boolean).join('\n') };
    });
    setSelectedMembers((prev) => prev.map((row) => (
      row.clanName === clanName && row.characterName === oldName
        ? { ...row, characterName: trimmed, matched: isRegisteredDraftName(trimmed, clanName) }
        : row
    )));
  };
  const removeSelectedDraftName = (clanName, targetName) => {
    setSelectedDraftByClan((prev) => {
      const list = namesFromText(prev[clanName] || '').filter((name) => name !== targetName);
      return { ...prev, [clanName]: list.join('\n') };
    });
    setSelectedMembers((prev) => prev.filter((row) => !(row.clanName === clanName && row.characterName === targetName)));
    setReviewEdit(null);
  };

  const load = () => Promise.all([request('/boss-participations'), request('/members')])
    .then(([recordRows, memberRows]) => { setRecords(recordRows); setMembers(memberRows); })
    .catch((err) => setMessage(err.message));

  useEffect(() => { load(); }, []);

  const selectFile = (event) => {
    const nextFile = event.target.files?.[0];
    setFile(nextFile || null);
    setPreview(nextFile ? URL.createObjectURL(nextFile) : '');
    setOcrStatus('');
    setOcrAmbiguous([]);
    setOcrEdit(null);
    setProgress(0);
  };

  const scanImage = async () => {
    if (!file) return;
    setOcrAmbiguous([]);
    setOcrEdit(null);
    setOcrStatus('스샷 글자를 읽는 중입니다.');
    setProgress(0);
    try {
      const text = await recognizeImageTextMultiple(file, setProgress);
      const { exactNames: names, ambiguous } = buildOcrReview(text, members, form.clanName, { corrections: ocrCorrections, filters: ocrFilters });
      const merged = [...new Set([...namesFromText(currentDraftNames), ...names])];
      updateCurrentDraft(merged.join('\n'));
      setOcrAmbiguous(ambiguous);
      setOcrStatus(`${names.length}명 후보를 찾았습니다. 5회 보정 인식 결과를 합쳤습니다. 저장 전 명단을 확인해 주세요.`);
    } catch (err) {
      setOcrStatus(`OCR 처리 실패: ${err.message}`);
    }
  };

  const saveRecord = async (event) => {
    event.preventDefault();
    setMessage('');
    const entries = Object.entries(draftByClan)
      .flatMap(([clanName, text]) => namesFromText(text).map((characterName) => ({ characterName, clanName })));
    if (!entries.length) {
      setMessage('참여 명단을 먼저 입력하거나 스샷에서 인식해 주세요.');
      return;
    }
    try {
      await request('/boss-participations', {
        method: 'POST',
        body: JSON.stringify({
          createdByMemberId: member.memberId,
          bossDate: form.bossDate,
          cutTime: form.cutTime,
          bossName: form.bossName,
          score: Number(form.score || 1),
          memo: form.memo,
          members: entries,
        }),
      });
      setDraftByClan({});
      setFile(null);
      setPreview('');
      setOcrStatus('');
      await load();
      setMessage('보스 참여내역을 저장했습니다.');
    } catch (err) {
      setMessage(err.message);
    }
  };

  const openRoster = async (record, clanName = '') => {
    setSelectedRecord(record);
    setSelectedClan(clanName);
    setSelectedMembers([]);
    setSelectedDraftByClan({});
    setReviewEdit(null);
    try {
      const rows = await request(`/boss-participations/${record.recordId}/members`);
      setSelectedMembers(rows);
      setSelectedDraftByClan(rowsToDraftByClan(rows));
    } catch (err) {
      setMessage(err.message);
    }
  };

  const saveSelectedRoster = async () => {
    if (!selectedRecord) return;
    const entries = Object.entries(selectedDraftByClan)
      .flatMap(([clanName, text]) => namesFromText(text).map((characterName) => ({ characterName, clanName })));
    if (!entries.length) {
      setMessage('참여 명단을 1명 이상 입력해 주세요.');
      return;
    }
    setSavingRoster(true);
    setMessage('');
    try {
      const rows = await request(`/boss-participations/${selectedRecord.recordId}/members?adminMemberId=${member.memberId}`, {
        method: 'PUT',
        body: JSON.stringify({ members: entries }),
      });
      setSelectedMembers(rows);
      setSelectedDraftByClan(rowsToDraftByClan(rows));
      setReviewEdit(null);
      await load();
      setMessage(`${selectedRecord.bossName} 참여명단을 수정했습니다.`);
    } catch (err) {
      setMessage(err.message);
    } finally {
      setSavingRoster(false);
    }
  };

  const copyRouletteNames = async (record) => {
    setMessage('');
    try {
      const rows = await request(`/boss-participations/${record.recordId}/members`);
      const names = rows.map((row) => row.characterName).filter(Boolean);
      if (!names.length) throw new Error('핀볼에 넣을 참여 명단이 없습니다.');
      await copyToClipboard(names.join(','));
      window.open(ROULETTE_URL, '_blank', 'noopener,noreferrer');
      setMessage(`${record.bossName} 참여자 ${names.length}명 핀볼 목록을 복사했습니다. 열린 룰렛 사이트 이름칸에 붙여넣어 주세요.`);
    } catch (err) {
      setMessage(err.message);
    }
  };

  const groupedSelectedMembers = useMemo(() => selectedMembers.reduce((acc, row) => {
    const key = row.clanName || '미분류';
    acc[key] = [...(acc[key] || []), row];
    return acc;
  }, {}), [selectedMembers]);
  const selectedRosterGroups = useMemo(() => Object.entries(groupedSelectedMembers)
    .filter(([clanName]) => !selectedClan || clanName === selectedClan), [groupedSelectedMembers, selectedClan]);
  const currentDraftReview = useMemo(() => namesFromText(currentDraftNames).map((name) => ({
    name,
    matched: isRegisteredDraftName(name, form.clanName),
  })), [currentDraftNames, form.clanName, members]);

  const visibleRecords = records.slice(0, 100);

  return (
    <>
      <datalist id="member-name-suggestions">
        {members.map((candidate) => <option key={candidate.memberId} value={candidate.characterName} />)}
      </datalist>
      <div className="page-title">
        <h1>보스 참여내역 조회</h1>
        <p>각 클랜 스크린샷을 OCR로 읽어 보스 회차별 참석 인원과 명단을 기록합니다.</p>
      </div>

      {member.role === 'ADMIN' && (
        <section className="white-card boss-batch-card">
          <div className="section-heading">
            <div>
              <h2>시간별 출석체크</h2>
              <p className="subtle">보스 시간별로 사진을 여러 장 넣으면 전체 인원을 합산해 보여줍니다. 초록색만 저장되고, 주황색 확인 필요 항목은 저장에서 제외됩니다.</p>
            </div>
            <div className="batch-heading-actions">
              <button type="button" className="outline-button no-margin" onClick={() => setShowCorrectionPanel(true)}>자동치환 관리</button>
              <label className="batch-date-control">출석 날짜
                <input type="date" value={form.bossDate} onChange={(event) => setForm({ ...form, bossDate: event.target.value })} />
              </label>
            </div>
          </div>
          {showCorrectionPanel && (
            <div className="correction-panel">
              <div className="section-heading compact">
                <div>
                  <h3>닉네임 자동치환</h3>
                  <p className="subtle">OCR이 잘못 읽는 이름을 직접 등록하세요. 예: 가오가이가 → 가오가이거</p>
                </div>
                <button type="button" className="mini-button" onClick={() => setShowCorrectionPanel(false)}>닫기</button>
              </div>
              <div className="correction-form">
                <input value={correctionDraft.wrong} onChange={(event) => setCorrectionDraft({ ...correctionDraft, wrong: event.target.value })} placeholder="오인식 닉네임" />
                <span>→</span>
                <input value={correctionDraft.right} onChange={(event) => setCorrectionDraft({ ...correctionDraft, right: event.target.value })} placeholder="올바른 닉네임" />
                <button type="button" className="primary-button no-margin" onClick={saveOcrCorrection}>추가</button>
              </div>
              <div className="correction-list">
                {correctionEntries.map(([wrong, right]) => (
                  <span className="correction-chip" key={wrong}>
                    <b>{wrong}</b><em>→</em><strong>{right}</strong>
                    <button type="button" onClick={() => removeOcrCorrection(wrong)}>삭제</button>
                  </span>
                ))}
                {!correctionEntries.length && <p className="subtle">아직 등록된 자동치환이 없습니다.</p>}
              </div>
              <div className="correction-subsection">
                <div>
                  <h4>표시제외 필터</h4>
                  <p className="subtle">OCR에는 잡히지만 명단에 필요 없는 값은 여기에 등록하세요. 등록된 값은 초록/주황 결과에 표시되지 않습니다.</p>
                </div>
                <div className="filter-form">
                  <input
                    value={filterDraft}
                    onChange={(event) => setFilterDraft(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter') {
                        event.preventDefault();
                        saveOcrFilter();
                      }
                    }}
                    placeholder="예: SIR, AY"
                  />
                  <button type="button" className="outline-button no-margin" onClick={saveOcrFilter}>필터 추가</button>
                </div>
                <div className="correction-list">
                  {filterEntries.map((filter) => (
                    <span className="correction-chip filter-chip" key={filter}>
                      <b>{filter}</b>
                      <button type="button" onClick={() => removeOcrFilter(filter)}>삭제</button>
                    </span>
                  ))}
                  {!filterEntries.length && <p className="subtle">아직 등록된 표시제외 필터가 없습니다.</p>}
                </div>
              </div>
            </div>
          )}
          <div className="boss-batch-list">
            {batchRows.map((row) => {
              const counts = batchClanCounts(row.names);
              const unresolved = row.names.filter((item) => !item.matched).length + row.ambiguous.length;
              return (
                <div className="boss-batch-row" key={row.key}>
                  <div className="boss-batch-title">
                    <b>{row.title}</b>
                    {row.savedRecord && <span className="saved-pill">저장완료</span>}
                  </div>
                  <div className="batch-file-picker">
                    <label className="batch-drop-zone" onDragOver={(event) => event.preventDefault()} onDrop={(event) => dropBatchFiles(row.key, event)}>
                      <input type="file" accept="image/png,image/jpeg,image/webp" multiple onChange={(event) => selectBatchFiles(row.key, event)} />
                      <span>{row.files.length ? '사진 더 추가' : '사진 드래그/선택'}</span>
                      {!!row.files.length && <small>{row.files.length}장 선택됨</small>}
                    </label>
                    {!!row.files.length && (
                      <div className="batch-file-list selected-photo-list">
                        {row.fileReviews.map((fileItem) => (
                          <span className="batch-file-chip selected-photo-chip" key={`${row.key}-${fileItem.fileIndex}-${fileItem.fileName}`}>
                            {fileItem.previewUrl && <img src={fileItem.previewUrl} alt={`선택 사진 ${fileItem.fileIndex}`} />}
                            <b>{fileItem.fileIndex}</b>
                            <em>{fileItem.fileName}</em>
                            <button type="button" disabled={row.scanning} onClick={() => removeBatchFile(row.key, fileItem.fileIndex)} aria-label={`${fileItem.fileName} 삭제`}>×</button>
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                  <input className="batch-time-input" value={row.cutInput} maxLength="5" onChange={(event) => updateBatchRow(row.key, { cutInput: event.target.value })} placeholder="컷 시간" />
                  <label className="batch-check"><input type="checkbox" checked={row.doubleScore} onChange={(event) => updateBatchRow(row.key, { doubleScore: event.target.checked })} /> 새벽/쟁 일정(2배)</label>
                  <label className="batch-check"><input type="checkbox" checked={row.longTerm} onChange={(event) => updateBatchRow(row.key, { longTerm: event.target.checked })} /> 장기전</label>
                  <label className="batch-check precision-check"><input type="checkbox" checked={row.precise} onChange={(event) => updateBatchRow(row.key, { precise: event.target.checked })} /> 정밀인식</label>
                  <div className="batch-actions">
                    <button type="button" className="outline-button no-margin" disabled={!row.files.length || row.scanning} onClick={() => scanBatchRow(row.key)}>{row.scanning ? `인식 ${row.progress}%` : row.precise ? '정밀인식' : '글자인식'}</button>
                    <button type="button" className="primary-button no-margin" disabled={row.scanning} onClick={() => saveBatchRow(row.key)}>인원체크 완료</button>
                    {row.savedRecord && <button type="button" className="roster-button roulette-button" onClick={() => copyRouletteNames(row.savedRecord)}>핀볼복사</button>}
                  </div>
                  {(row.names.length > 0 || row.ambiguous.length > 0 || row.message) && (
                    <div className="batch-result">
                      <div className="batch-counts">
                        <span className="clan-badge total">전체 {row.names.length}명</span>
                        {clanDisplayOrder.map((clan) => counts[clan] ? <span className={`clan-badge ${normalize(clan)}`} key={clan}>{clan} {counts[clan]}명</span> : null)}
                        {!!unresolved && <span className="review-count">확인필요 {unresolved}개</span>}
                      </div>
                      <div className="batch-photo-groups">
                        {groupedBatchReview(row).map((fileGroup) => (
                          <div className="batch-photo-group" key={`${fileGroup.fileIndex}-${fileGroup.fileName}`}>
                            <div className="batch-photo-title">
                              <div>
                                <b>사진 {fileGroup.fileIndex}</b>
                                <span>{fileGroup.fileName}</span>
                              </div>
                              {fileGroup.dominantClan && <em>{fileGroup.dominantClan} 기준 후보</em>}
                            </div>
                            {fileGroup.previewUrl && <img className="batch-photo-preview" src={fileGroup.previewUrl} alt={`사진 ${fileGroup.fileIndex} 미리보기`} />}
                            <div className="batch-position-list">
                              {fileGroup.positions.length ? fileGroup.positions.map((positionGroup) => (
                                <div className="batch-position-row" key={`${fileGroup.fileIndex}-${positionGroup.position}`}>
                                  <b className="batch-position-title">{positionGroup.position}번 자리</b>
                                  <div className="draft-review-list position-review-list">
                                    {positionGroup.names.map((item) => {
                                      const editing = batchEdit?.rowKey === row.key && batchEdit?.oldName === item.name;
                                      return (
                                        <span className={item.matched ? 'draft-chip matched' : 'draft-chip review'} key={`${fileGroup.fileIndex}-${positionGroup.position}-${item.name}`}>
                                          {editing ? (
                                            <>
                                              <input list="member-name-suggestions" value={batchEdit.value} onChange={(event) => setBatchEdit({ ...batchEdit, value: event.target.value })} />
                                              <div className="name-suggestion-list">
                                                {memberNameSuggestions(batchEdit.value).map((candidate) => (
                                                  <button type="button" key={candidate.memberId} onClick={() => setBatchEdit({ ...batchEdit, value: candidate.characterName })}>{candidate.characterName}</button>
                                                ))}
                                              </div>
                                              <button type="button" onClick={() => { updateBatchName(row.key, item.name, batchEdit.value); setBatchEdit(null); }}>적용</button>
                                              <button type="button" onClick={() => setBatchEdit(null)}>취소</button>
                                            </>
                                          ) : (
                                            <>
                                              {item.name}
                                              {!item.matched && <button type="button" onClick={() => setBatchEdit({ rowKey: row.key, oldName: item.name, value: item.name })}>수정</button>}
                                              <button type="button" onClick={() => removeBatchName(row.key, item.name)}>삭제</button>
                                            </>
                                          )}
                                        </span>
                                      );
                                    })}
                                    {positionGroup.ambiguous.map((item) => {
                                      const ambiguousKey = item.id || item.raw;
                                      const editing = batchEdit?.rowKey === row.key && batchEdit?.raw === ambiguousKey;
                                      return (
                                        <div className="ocr-review-item compact inline" key={`${fileGroup.fileIndex}-${positionGroup.position}-${item.raw}`}>
                                          <b>인식값: {item.raw}</b>
                                          <div className="ocr-suggestion-buttons">
                                            {item.suggestions.map(({ member: suggestion, score }) => (
                                              <button type="button" key={suggestion.memberId} onClick={() => resolveBatchAmbiguous(row.key, ambiguousKey, suggestion.characterName)}>
                                                {suggestion.characterName}<small>{Math.round(score * 100)}%</small>
                                              </button>
                                            ))}
                                          </div>
                                          <div className="ocr-manual-row">
                                            {editing ? (
                                              <>
                                                <input list="member-name-suggestions" value={batchEdit.value} onChange={(event) => setBatchEdit({ ...batchEdit, value: event.target.value })} />
                                                <div className="name-suggestion-list">
                                                  {memberNameSuggestions(batchEdit.value).map((candidate) => (
                                                    <button type="button" key={candidate.memberId} onClick={() => setBatchEdit({ ...batchEdit, value: candidate.characterName })}>{candidate.characterName}</button>
                                                  ))}
                                                </div>
                                                <button type="button" onClick={() => { resolveBatchAmbiguous(row.key, ambiguousKey, batchEdit.value); setBatchEdit(null); }}>수정해서 추가</button>
                                                <button type="button" onClick={() => setBatchEdit(null)}>취소</button>
                                              </>
                                            ) : (
                                              <>
                                                <button type="button" onClick={() => setBatchEdit({ rowKey: row.key, raw: ambiguousKey, value: item.raw })}>직접수정</button>
                                                <button type="button" onClick={() => ignoreBatchAmbiguous(row.key, ambiguousKey)}>무시</button>
                                              </>
                                            )}
                                          </div>
                                        </div>
                                      );
                                    })}
                                  </div>
                                </div>
                              )) : <div className="batch-empty-photo">이 사진에서는 인식된 인원이 없습니다. 사진이 흐리면 다시 올려주세요.</div>}
                            </div>
                          </div>
                        ))}
                      </div>
                      {!!row.appliedCorrections?.length && (
                        <div className="applied-correction-list">
                          <b>자동치환 적용</b>
                          <div>
                            {row.appliedCorrections.map((item) => (
                              <span className="applied-correction-chip" key={item.id || `${item.wrong}-${item.right}`}>
                                {item.fileIndex && <small>사진 {item.fileIndex}</small>}
                                <em>{item.wrong}</em>
                                <strong>→ {item.right}</strong>
                                {!!item.positions?.length && <small>{item.positions.join(', ')}번 자리</small>}
                              </span>
                            ))}
                          </div>
                        </div>
                      )}
                      {row.message && <p className="batch-message">{row.message}</p>}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </section>
      )}

      {false && member.role === 'ADMIN' && (
        <section className="white-card boss-register-card">
          <div className="section-heading">
            <div>
              <h2>보스 참여내역 등록</h2>
              <p className="subtle">클랜을 선택하고 스샷을 올리면 이름 후보가 아래 명단에 들어갑니다. 틀린 이름은 직접 수정 후 저장하세요.</p>
            </div>
            <span className="result-count">{totalDraftCount}명 준비됨</span>
          </div>
          <form className="boss-form" onSubmit={saveRecord}>
            <label>날짜<input type="date" value={form.bossDate} onChange={(e) => setForm({ ...form, bossDate: e.target.value })} /></label>
            <label>컷시간<input type="time" value={form.cutTime} onChange={(e) => setForm({ ...form, cutTime: e.target.value })} /></label>
            <label>보스명<select value={form.bossName} onChange={(e) => setForm({ ...form, bossName: e.target.value })}>{bossOptions.map((boss) => <option key={boss}>{boss}</option>)}</select></label>
            <label>클랜<select value={form.clanName} onChange={(e) => setForm({ ...form, clanName: e.target.value })}>{clanOptions.map((clan) => <option key={clan}>{clan}</option>)}</select></label>
            <label>점수<input type="number" min="0" value={form.score} onChange={(e) => setForm({ ...form, score: e.target.value })} /></label>
            <label className="wide">메모<input value={form.memo} onChange={(e) => setForm({ ...form, memo: e.target.value })} placeholder="예: 2성, 정산 제외 등" /></label>
            <label className="upload-mini">
              <input type="file" accept="image/png,image/jpeg,image/webp" onChange={selectFile} />
              {preview ? <img src={preview} alt="보스 참여 스크린샷" /> : <span>스샷 선택</span>}
            </label>
            <button type="button" className="outline-button no-margin" disabled={!file || ocrStatus.includes('읽는 중')} onClick={scanImage}>
              {ocrStatus.includes('읽는 중') ? `인식 중 ${progress}%` : '글자 인식'}
            </button>
            <label className="boss-names">현재 선택 클랜 명단<textarea value={currentDraftNames} onChange={(e) => updateCurrentDraft(e.target.value)} placeholder="한 줄에 한 명씩 입력됩니다." /></label>
            <button className="primary-button">참여내역 저장</button>
          </form>
          {!!currentDraftReview.length && (
            <div className="draft-review-panel">
              <div className="section-heading compact">
                <div>
                  <h3>저장 전 명단 검토</h3>
                  <p className="subtle">등록된 클랜원은 초록색, 확인이 필요한 이름은 주황색입니다. 주황색 이름은 저장 전에 바로 고쳐주세요.</p>
                </div>
                <span className="result-count">{currentDraftReview.length}명</span>
              </div>
              <div className="draft-review-list">
                {currentDraftReview.map((item) => {
                  const editing = draftEdit?.oldName === item.name;
                  return (
                    <span className={item.matched ? 'draft-chip matched' : 'draft-chip review'} key={item.name}>
                      {editing ? (
                        <>
                          <input value={draftEdit.value} onChange={(e) => setDraftEdit({ ...draftEdit, value: e.target.value })} />
                          <button type="button" onClick={() => replaceDraftName(item.name, draftEdit.value)}>적용</button>
                          <button type="button" onClick={() => setDraftEdit(null)}>취소</button>
                        </>
                      ) : (
                        <>
                          {item.name}
                          {!item.matched && <button type="button" onClick={() => setDraftEdit({ oldName: item.name, value: item.name })}>수정</button>}
                          <button type="button" onClick={() => removeDraftName(item.name)}>삭제</button>
                        </>
                      )}
                    </span>
                  );
                })}
              </div>
            </div>
          )}
          <div className="boss-draft-summary">
            {clanOptions.map((clan) => <span key={clan} className={namesFromText(draftByClan[clan]).length ? 'ready' : ''}>{clan} {namesFromText(draftByClan[clan]).length}명</span>)}
          </div>
          {ocrStatus && <div className="scan-status">{ocrStatus}</div>}
          {!!ocrAmbiguous.length && (
            <div className="ocr-review-panel">
              <div className="section-heading">
                <div>
                  <h3>애매한 이름 확인</h3>
                  <p className="subtle">OCR이 헷갈린 이름입니다. 맞는 클랜원을 누르면 현재 선택한 클랜 명단에 추가됩니다.</p>
                </div>
                <span className="result-count">{ocrAmbiguous.length}개</span>
              </div>
              <div className="ocr-review-list">
                {ocrAmbiguous.map((item) => (
                  <div className="ocr-review-item" key={item.raw}>
                    <b>인식값: {item.raw}</b>
                    <div className="ocr-suggestion-buttons">
                      {item.suggestions.map(({ member: suggestion, score }) => (
                        <button type="button" key={suggestion.memberId} onClick={() => addResolvedOcrName(item.raw, suggestion.characterName)}>
                          {suggestion.characterName}
                          <small>{Math.round(score * 100)}%</small>
                        </button>
                      ))}
                    </div>
                    <div className="ocr-manual-row">
                      {ocrEdit?.raw === item.raw ? (
                        <>
                          <input value={ocrEdit.value} onChange={(e) => setOcrEdit({ ...ocrEdit, value: e.target.value })} placeholder="직접 입력" />
                          <button type="button" onClick={() => addResolvedOcrName(item.raw, ocrEdit.value)}>수정해서 추가</button>
                          <button type="button" onClick={() => setOcrEdit(null)}>취소</button>
                        </>
                      ) : (
                        <>
                          <button type="button" onClick={() => setOcrEdit({ raw: item.raw, value: item.raw })}>직접수정</button>
                          <button type="button" onClick={() => ignoreOcrName(item.raw)}>무시</button>
                        </>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
          {message && <p className="vault-message">{message}</p>}
        </section>
      )}

      <section className="white-card boss-history-card">
        <div className="filters">
          <select><option>전체 날짜</option></select>
          <input placeholder="보스명" />
          <button className="dark-button">조회</button>
        </div>
        <p className="subtle">조회 결과 {records.length}건 · 1/{Math.max(1, Math.ceil(records.length / 30))}페이지</p>
        <div className="boss-table-scroll">
          <table className="data-table boss-history-table">
            <thead>
              <tr>
                <th>날짜</th>
                <th>컷시간</th>
                <th>출석입력시간</th>
                <th>보스명</th>
                <th>출석인원</th>
                <th>점수</th>
                <th>참여명단</th>
              </tr>
            </thead>
            <tbody>
              {visibleRecords.map((record) => (
                <tr key={record.recordId}>
                  <td>{record.bossDate}</td>
                  <td><TimeBadge value={record.cutTime} /></td>
                  <td><TimeBadge value={record.submittedAt} dateTime /></td>
                  <td>{record.bossName}</td>
                  <td><ClanCountBadges record={record} onSelectClan={(clan) => openRoster(record, clan)} /></td>
                  <td><b>{record.score}</b></td>
                  <td><div className="boss-action-buttons"><button className="roster-button" onClick={() => openRoster(record)}>명단보기</button><button className="roster-button roulette-button" onClick={() => copyRouletteNames(record)}>핀볼복사</button></div></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {!records.length && <div className="empty-state">아직 등록된 보스 참여내역이 없습니다.</div>}
      </section>

      {selectedRecord && (
        <section className="white-card boss-roster-card">
          <div className="section-heading">
            <div>
              <h2>{selectedRecord.bossDate} · {selectedRecord.bossName} 명단{selectedClan ? ` · ${selectedClan}` : ''}</h2>
              <p className="subtle">총 {selectedMembers.length}명 · 미등록 이름은 확인 필요로 표시됩니다.</p>
            </div>
            <button className="outline-button no-margin" onClick={() => setSelectedRecord(null)}>닫기</button>
          </div>
          {member.role === 'ADMIN' && (
            <div className="boss-roster-editor">
              <div className="section-heading">
                <div>
                  <h3>참여명단 수정</h3>
                  <p className="subtle">잘못 들어간 이름은 지우고, 빠진 이름은 한 줄에 한 명씩 추가하세요.</p>
                </div>
                <button className="primary-button no-margin" onClick={saveSelectedRoster} disabled={savingRoster}>{savingRoster ? '저장중...' : '명단 저장'}</button>
              </div>
              <div className="boss-roster-edit-grid">
                {clanOptions.map((clan) => (
                  <label key={clan}>{clan}
                    <textarea value={selectedDraftByClan[clan] || ''} onChange={(e) => setSelectedDraftByClan((prev) => ({ ...prev, [clan]: e.target.value }))} placeholder={`${clan} 참여자 이름`} />
                  </label>
                ))}
              </div>
            </div>
          )}
          <div className="boss-roster-groups">
            {selectedRosterGroups.map(([clanName, list]) => (
              <div className="boss-roster-group" key={clanName}>
                <h3>{clanName} <span>{list.length}명</span></h3>
                <div>{list.map((row) => {
                  const editing = reviewEdit?.clanName === clanName && reviewEdit?.oldName === row.characterName;
                  return <span className={row.matched ? 'member-chip matched' : 'member-chip review editable'} key={row.participationMemberId}>
                    {editing ? (
                      <>
                        <input value={reviewEdit.value} onChange={(e) => setReviewEdit({ ...reviewEdit, value: e.target.value })} />
                        <button type="button" onClick={() => { replaceSelectedDraftName(clanName, row.characterName, reviewEdit.value); setReviewEdit(null); }}>적용</button>
                        <button type="button" onClick={() => setReviewEdit(null)}>취소</button>
                      </>
                    ) : (
                      <>
                        {row.characterName}
                        {member.role === 'ADMIN' && !row.matched && <>
                          <button type="button" onClick={() => setReviewEdit({ clanName, oldName: row.characterName, value: row.characterName })}>수정</button>
                          <button type="button" onClick={() => removeSelectedDraftName(clanName, row.characterName)}>삭제</button>
                        </>}
                      </>
                    )}
                  </span>;
                })}</div>
              </div>
            ))}
          </div>
        </section>
      )}
    </>
  );
}

function ClanCountBadges({ record, onSelectClan }) {
  const entries = Object.entries(record.clanCounts || {});
  return (
    <div className="clan-counts">
      <span className="clan-badge total">전체 {record.totalCount}명</span>
      {entries.map(([clan, count]) => {
        const label = `${clan} ${count}명`;
        const className = `clan-badge ${normalize(clan)}${onSelectClan ? ' clickable' : ''}`;
        return onSelectClan ? (
          <button type="button" className={className} key={clan} onClick={() => onSelectClan(clan)}>
            {label}
          </button>
        ) : (
          <span className={className} key={clan}>
            {label}
          </span>
        );
      })}
    </div>
  );
}

function TimeBadge({ value, dateTime = false }) {
  const { period, time } = dateTime ? splitDateTimeKoreanTime(value) : splitKoreanTime(value);
  return <>{period && <span className="time-badge">{period}</span>} {time}</>;
}

function PinballPage({ setPage }) {
  const [records, setRecords] = useState([]);
  const [message, setMessage] = useState('');
  const [loadingId, setLoadingId] = useState(null);
  const [pinballDraft, setPinballDraft] = useState(null);
  const [pinballWinner, setPinballWinner] = useState('');
  const [pinballSpinning, setPinballSpinning] = useState(false);

  const load = () => request('/boss-participations')
    .then(setRecords)
    .catch((err) => setMessage(err.message));

  useEffect(() => { load(); }, []);

  const copyRouletteNames = async (record) => {
    setLoadingId(record.recordId);
    setMessage('');
    try {
      const rows = await request(`/boss-participations/${record.recordId}/members`);
      const names = rows.map((row) => row.characterName).filter(Boolean);
      if (!names.length) throw new Error('핀볼에 넣을 참여 명단이 없습니다.');
      const namesText = names.join(',');
      setPinballDraft({ record, namesText, count: names.length });
      setPinballWinner('');
      await copyToClipboard(namesText);
      setMessage(`${record.bossDate} ${record.bossName} 참여자 ${names.length}명을 내장 핀볼에 바로 등록했습니다. 외부 사이트용 이름 목록도 복사했습니다.`);
    } catch (err) {
      setMessage(err.message);
    } finally {
      setLoadingId(null);
    }
  };

  const copyDraft = async () => {
    if (!pinballDraft?.namesText?.trim()) return;
    await copyToClipboard(pinballDraft.namesText);
    window.open(ROULETTE_URL, '_blank', 'noopener,noreferrer');
    setMessage(`${pinballDraft.count}명 핀볼 이름 목록을 다시 복사했습니다.`);
  };

  const spinPinball = () => {
    const names = namesFromText(pinballDraft?.namesText || '');
    if (!names.length || pinballSpinning) return;
    setPinballSpinning(true);
    setMessage('');
    let tick = 0;
    const timer = window.setInterval(() => {
      const picked = names[Math.floor(Math.random() * names.length)];
      setPinballWinner(picked);
      tick += 1;
      if (tick >= 24) {
        window.clearInterval(timer);
        const winner = names[Math.floor(Math.random() * names.length)];
        setPinballWinner(winner);
        setPinballSpinning(false);
        setMessage(`핀볼 결과: ${winner}`);
      }
    }, 70);
  };

  return (
    <>
      <AdminBackButton setPage={setPage} />
      <div className="page-title">
        <h1>핀볼</h1>
        <p>보스별 참여자 명단을 핀볼 룰렛에 바로 넣을 수 있게 복사합니다.</p>
      </div>
      <section className="white-card">
        <div className="section-heading">
          <div>
            <h2>보스 참여자 핀볼 세팅</h2>
            <p className="subtle">원하는 보스 기록의 핀볼복사를 누르면 참여자 이름이 복사되고 룰렛 사이트가 열립니다.</p>
          </div>
          <a className="outline-button no-margin" href={ROULETTE_URL} target="_blank" rel="noreferrer">핀볼 사이트 열기</a>
        </div>
        {message && <p className="vault-message">{message}</p>}
        {pinballDraft && (
          <div className="pinball-draft">
            <div className="section-heading">
              <div>
                <h3>{pinballDraft.record.bossDate} · {pinballDraft.record.bossName} 핀볼 이름</h3>
                <p className="subtle">아래 이름이 핀볼 사이트에 넣을 목록입니다. 잘못 인식된 이름은 직접 수정한 뒤 다시 복사하세요.</p>
              </div>
              <span className="result-count">{namesFromText(pinballDraft.namesText).length}명</span>
            </div>
            <textarea value={pinballDraft.namesText} onChange={(e) => setPinballDraft({ ...pinballDraft, namesText: e.target.value, count: namesFromText(e.target.value).length })} />
            <div className="inline-pinball">
              <div className={`pinball-orb ${pinballSpinning ? 'spinning' : ''}`}>{pinballWinner || '?'}</div>
              <div className="pinball-control-panel">
                <strong>내장 핀볼</strong>
                <p className="subtle">위 명단이 바로 후보로 들어갑니다. 이름을 수정하면 수정된 목록 기준으로 추첨합니다.</p>
                <div className="boss-action-buttons">
                  <button className="primary-button no-margin" onClick={spinPinball} disabled={pinballSpinning || !namesFromText(pinballDraft.namesText).length}>{pinballSpinning ? '돌리는 중...' : '핀볼 돌리기'}</button>
                  <button className="role-button" onClick={() => setPinballWinner('')} disabled={pinballSpinning}>결과 초기화</button>
                </div>
              </div>
            </div>
            <div className="boss-action-buttons pinball-draft-actions">
              <button className="roster-button roulette-button" onClick={copyDraft}>이 목록 복사 + 핀볼 열기</button>
              <a className="outline-button no-margin" href={ROULETTE_URL} target="_blank" rel="noreferrer">핀볼 사이트만 열기</a>
            </div>
          </div>
        )}
        <div className="table-wrap">
          <table className="data-table pinball-table">
            <thead>
              <tr>
                <th>날짜</th>
                <th>컷시간</th>
                <th>보스명</th>
                <th>참여인원</th>
                <th>점수</th>
                <th>핀볼</th>
              </tr>
            </thead>
            <tbody>
              {records.slice(0, 100).map((record) => (
                <tr key={record.recordId}>
                  <td>{record.bossDate}</td>
                  <td><TimeBadge value={record.cutTime} /></td>
                  <td>{record.bossName}</td>
                  <td><ClanCountBadges record={record} /></td>
                  <td><b>{record.score}</b></td>
                  <td><button className="roster-button roulette-button" disabled={loadingId === record.recordId} onClick={() => copyRouletteNames(record)}>{loadingId === record.recordId ? '복사중...' : '핀볼복사'}</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {!records.length && <div className="empty-state">등록된 보스 참여내역이 없습니다.</div>}
      </section>
    </>
  );
}

function LegacyAttendance({ member }) {
  const [rows, setRows] = useState([]);
  const [members, setMembers] = useState([]);
  const [activities, setActivities] = useState([]);
  const [form, setForm] = useState({ attendanceDate: today(), memberId: '', activityTypeId: '', status: 'ATTENDED' });
  const [message, setMessage] = useState('');
  const load = () => Promise.all([request('/attendances'), request('/members'), request('/activities')]).then(([a, m, t]) => { setRows(a); setMembers(m); setActivities(t); }).catch((err) => setMessage(err.message));
  useEffect(() => { load(); }, []);
  const save = async (event) => {
    event.preventDefault();
    setMessage('');
    try {
      await request('/attendances', { method: 'POST', body: JSON.stringify({ ...form, memberId: Number(form.memberId), activityTypeId: Number(form.activityTypeId) }) });
      setForm({ ...form, memberId: '' });
      await load();
      setMessage('출석 기록을 저장했습니다.');
    } catch (err) { setMessage(err.message); }
  };
  return <><div className="page-title"><h1>보스 참여내역 조회</h1><p>활동 참석 여부를 기록하고 조회합니다.</p></div>{member.role === 'ADMIN' && <section className="white-card"><h2>출석 기록 추가</h2><form className="record-form" onSubmit={save}><input type="date" value={form.attendanceDate} onChange={(e) => setForm({ ...form, attendanceDate: e.target.value })} /><select required value={form.activityTypeId} onChange={(e) => setForm({ ...form, activityTypeId: e.target.value })}><option value="">활동 선택</option>{activities.map((a) => <option key={a.activityTypeId} value={a.activityTypeId}>{a.typeName}</option>)}</select><select required value={form.memberId} onChange={(e) => setForm({ ...form, memberId: e.target.value })}><option value="">클랜원 선택</option>{members.map((m) => <option key={m.memberId} value={m.memberId}>{m.characterName}</option>)}</select><select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}><option value="ATTENDED">참석</option><option value="ABSENT">불참</option></select><button className="primary-button">저장</button></form>{message && <p className="vault-message">{message}</p>}</section>}<section className="white-card"><div className="filters"><select><option>전체 날짜</option></select><input placeholder="보스명" /><button className="dark-button">조회</button><button className="orange-button">주간 정산</button></div><div className="table-wrap"><table className="data-table"><thead><tr><th>날짜</th><th>활동</th><th>출석회원</th><th>상태</th></tr></thead><tbody>{rows.map((row) => <tr key={row.attendanceId}><td>{row.attendanceDate}</td><td>{row.activityType?.typeName ?? '-'}</td><td>{row.member?.characterName ?? '-'}</td><td><span className="status-pill">{row.status === 'ATTENDED' ? '참석' : '불참'}</span></td></tr>)}</tbody></table></div>{!rows.length && <div className="empty-state">등록된 출석 기록이 없습니다.</div>}</section></>;
}

function ClanVaultPage({ member, readonly = false }) {
  const [summary, setSummary] = useState(null);
  const [members, setMembers] = useState([]);
  const [mode, setMode] = useState('deposit');
  const [form, setForm] = useState({ amountDiamonds: '', balanceDiamonds: '', targetMemberId: '', memo: '' });
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const typeText = { DEPOSIT: '입금', DISTRIBUTION: '분배', WITHDRAW: '차감', ADJUSTMENT: '잔액수정' };
  const typeTone = { DEPOSIT: 'green', DISTRIBUTION: 'blue', WITHDRAW: 'red', ADJUSTMENT: 'purple' };
  const load = async () => { const [vault, memberRows] = await Promise.all([request('/vault'), request('/members')]); setSummary(vault); setMembers(memberRows); };
  useEffect(() => { load().catch((err) => setMessage(err.message)); }, []);
  const submit = async (event) => {
    event.preventDefault();
    setLoading(true);
    setMessage('');
    const body = { memo: form.memo, createdByMemberId: member.memberId };
    if (mode === 'adjust') body.balanceDiamonds = Number(form.balanceDiamonds || 0);
    else body.amountDiamonds = Number(form.amountDiamonds || 0);
    if (mode === 'distribute') body.targetMemberId = Number(form.targetMemberId || 0);
    const path = mode === 'deposit' ? '/vault/deposit' : mode === 'distribute' ? '/vault/distribute' : mode === 'withdraw' ? '/vault/withdraw' : '/vault/balance';
    const method = mode === 'adjust' ? 'PATCH' : 'POST';
    try {
      await request(path, { method, body: JSON.stringify(body) });
      setForm({ amountDiamonds: '', balanceDiamonds: '', targetMemberId: '', memo: '' });
      await load();
      setMessage('금고 내용을 저장했습니다.');
    } catch (err) { setMessage(err.message); } finally { setLoading(false); }
  };
  const transactions = summary?.recentTransactions ?? [];
  return <><div className="page-title"><h1>{readonly ? '장부 조회' : '통장현황'}</h1><p>클랜 금고의 다이아 잔액과 입금·분배·차감 기록을 관리합니다.</p></div><div className="vault-grid"><section className="white-card vault-balance"><span className="vault-icon">💎</span><p>현재 클랜금고 잔액</p><strong>{money(summary?.balanceDiamonds)}</strong><small>입금 {summary?.depositCount ?? 0}건 · 분배 {summary?.distributionCount ?? 0}건</small></section>{!readonly && <section className="white-card vault-form-card"><div className="section-heading"><h2>금고 기록 추가</h2></div><div className="vault-tabs"><button className={mode === 'deposit' ? 'active' : ''} onClick={() => setMode('deposit')}>입금</button><button className={mode === 'distribute' ? 'active' : ''} onClick={() => setMode('distribute')}>분배</button><button className={mode === 'withdraw' ? 'active' : ''} onClick={() => setMode('withdraw')}>차감</button><button className={mode === 'adjust' ? 'active' : ''} onClick={() => setMode('adjust')}>잔액수정</button></div><form className="vault-form" onSubmit={submit}>{mode === 'distribute' && <label>받는 클랜원<select required value={form.targetMemberId} onChange={(e) => setForm({ ...form, targetMemberId: e.target.value })}><option value="">클랜원 선택</option>{members.map((m) => <option value={m.memberId} key={m.memberId}>{m.characterName}</option>)}</select></label>}{mode === 'adjust' ? <label>새 금고 잔액<input required min="0" type="number" value={form.balanceDiamonds} onChange={(e) => setForm({ ...form, balanceDiamonds: e.target.value })} placeholder="현재 총 다이아 개수" /></label> : <label>다이아 수량<input required min="1" type="number" value={form.amountDiamonds} onChange={(e) => setForm({ ...form, amountDiamonds: e.target.value })} placeholder="기록할 다이아 수량" /></label>}<label>메모<input value={form.memo} onChange={(e) => setForm({ ...form, memo: e.target.value })} placeholder="예: 에노크 분배, 보스 수익 입금" /></label><button className="primary-button" disabled={loading}>{loading ? '저장 중...' : '금고 기록 저장'}</button>{message && <p className="vault-message">{message}</p>}</form></section>}</div><section className="white-card"><div className="section-heading"><h2>거래내역</h2><span className="result-count">{transactions.length}건</span></div><div className="table-wrap"><table className="data-table vault-table"><thead><tr><th>시간</th><th>종류</th><th>대상</th><th>수량</th><th>처리 후 잔액</th><th>메모</th><th>기록자</th></tr></thead><tbody>{transactions.map((row) => <tr key={row.transactionId}><td>{new Date(row.createdAt).toLocaleString('ko-KR')}</td><td><span className={`vault-type ${typeTone[row.type]}`}>{typeText[row.type] ?? row.type}</span></td><td>{row.targetMemberName ?? '-'}</td><td>{money(row.amountDiamonds)}</td><td><b>{money(row.balanceAfter)}</b></td><td>{row.memo || '-'}</td><td>{row.createdByMemberName ?? '-'}</td></tr>)}</tbody></table></div>{!transactions.length && <div className="empty-state">아직 금고 거래내역이 없습니다.</div>}</section></>;
}

function PaymentPage({ member }) {
  const [summary, setSummary] = useState(null);
  useEffect(() => { request('/vault').then(setSummary).catch(() => {}); }, []);
  const rows = (summary?.recentTransactions ?? []).filter((row) => row.type === 'DISTRIBUTION' && row.targetMemberId === member.memberId);
  const total = rows.reduce((sum, row) => sum + Number(row.amountDiamonds || 0), 0);
  return <><div className="page-title"><h1>분배금 조회</h1><p>내 캐릭터에게 지급된 클랜금고 분배 기록입니다.</p></div><div className="metric-grid"><Metric label="내 누적 분배" value={money(total)} caption={`${rows.length}건 지급`} tone="green" /><Metric label="클랜금고 잔액" value={money(summary?.balanceDiamonds)} caption="현재 남은 다이아" tone="blue" /><Metric label="최근 기록" value={`${rows.length}건`} caption="최근 거래 기준" tone="purple" /></div><section className="white-card"><div className="table-wrap"><table className="data-table"><thead><tr><th>일시</th><th>수량</th><th>메모</th><th>기록자</th></tr></thead><tbody>{rows.map((row) => <tr key={row.transactionId}><td>{new Date(row.createdAt).toLocaleString('ko-KR')}</td><td className="green-text">{money(row.amountDiamonds)}</td><td>{row.memo || '-'}</td><td>{row.createdByMemberName || '-'}</td></tr>)}</tbody></table></div>{!rows.length && <div className="empty-state">아직 내게 지급된 분배금 기록이 없습니다.</div>}</section></>;
}

function PaymentClaimPage({ member }) {
  const [summary, setSummary] = useState(null);
  const [rows, setRows] = useState([]);
  const [message, setMessage] = useState('');
  const [claimingId, setClaimingId] = useState(null);
  const load = async () => {
    const [vault, distributions] = await Promise.all([
      request('/vault'),
      request(`/vault/distributions/member/${member.memberId}`),
    ]);
    setSummary(vault);
    setRows(distributions);
  };
  useEffect(() => { load().catch((err) => setMessage(err.message)); }, [member.memberId]);
  const pendingTotal = rows.filter((row) => !row.claimed).reduce((sum, row) => sum + Number(row.amountDiamonds || 0), 0);
  const claimedTotal = rows.filter((row) => row.claimed).reduce((sum, row) => sum + Number(row.amountDiamonds || 0), 0);
  const claim = async (row) => {
    setClaimingId(row.transactionId);
    setMessage('');
    try {
      await request(`/vault/distributions/${row.transactionId}/claim?memberId=${member.memberId}`, { method: 'POST' });
      await load();
      setMessage('분배금 수령완료로 처리했습니다.');
    } catch (err) {
      setMessage(err.message);
    } finally {
      setClaimingId(null);
    }
  };
  return <><div className="page-title"><h1>분배금 조회</h1><p>클랜금고에서 내 캐릭터에게 배정된 분배금과 수령 여부를 확인합니다.</p></div><div className="metric-grid"><Metric label="받을금액" value={money(pendingTotal)} caption="아직 수령 처리하지 않은 금액" tone="green" /><Metric label="받은금액" value={money(claimedTotal)} caption="수령완료 처리된 금액" tone="blue" /><Metric label="클랜금고 잔액" value={money(summary?.balanceDiamonds)} caption="현재 남아있는 다이아" tone="purple" /></div><section className="white-card"><div className="section-heading"><h2>내 분배금 내역</h2><span className="result-count">{rows.length}건</span></div>{message && <p className="vault-message">{message}</p>}<div className="table-wrap"><table className="data-table"><thead><tr><th>일시</th><th>수량</th><th>상태</th><th>수령일</th><th>메모</th><th>기록자</th><th>처리</th></tr></thead><tbody>{rows.map((row) => <tr key={row.transactionId}><td>{new Date(row.createdAt).toLocaleString('ko-KR')}</td><td className="green-text">{money(row.amountDiamonds)}</td><td><span className={`claim-pill ${row.claimed ? 'done' : 'pending'}`}>{row.claimed ? '수령완료' : '수령대기'}</span></td><td>{row.claimedAt ? new Date(row.claimedAt).toLocaleString('ko-KR') : '-'}</td><td>{row.memo || '-'}</td><td>{row.createdByMemberName || '-'}</td><td>{row.claimed ? '-' : <button className="mini-button" disabled={claimingId === row.transactionId} onClick={() => claim(row)}>{claimingId === row.transactionId ? '처리중' : '수령완료 처리'}</button>}</td></tr>)}</tbody></table></div>{!rows.length && <div className="empty-state">아직 내게 배정된 분배금 기록이 없습니다.</div>}</section></>;
}

function InventoryPage({ member }) {
  const [rows, setRows] = useState([]);
  const [form, setForm] = useState({ itemName: '', quantity: 1, location: '클랜창고', memo: '' });
  const canManage = member.role === 'ADMIN';
  const load = () => request('/management/inventory').then(setRows).catch(() => {});
  useEffect(() => { load(); }, []);
  const add = async (event) => { event.preventDefault(); await request('/management/inventory', { method: 'POST', body: JSON.stringify({ ...form, quantity: Number(form.quantity || 0), adminMemberId: member.memberId }) }); setForm({ itemName: '', quantity: 1, location: '클랜창고', memo: '' }); await load(); };
  return <CrudPage title="재고현황" description="클랜 보유 아이템과 수량을 조회합니다." canManage={canManage} form={<form className="record-form" onSubmit={add}><input required placeholder="아이템명" value={form.itemName} onChange={(e) => setForm({ ...form, itemName: e.target.value })} /><input required type="number" min="0" value={form.quantity} onChange={(e) => setForm({ ...form, quantity: e.target.value })} /><input placeholder="보관 위치" value={form.location} onChange={(e) => setForm({ ...form, location: e.target.value })} /><input placeholder="메모" value={form.memo} onChange={(e) => setForm({ ...form, memo: e.target.value })} /><button className="primary-button">추가</button></form>} rows={rows} getId={(row) => row.inventoryItemId} columns={['아이템', '수량', '위치', '메모', '등록일']} render={(row) => [row.itemName, formatNumber(row.quantity), row.location || '-', row.memo || '-', new Date(row.createdAt).toLocaleDateString('ko-KR')]} onDelete={async (id) => { await request(`/management/inventory/${id}?adminMemberId=${member.memberId}`, { method: 'DELETE' }); await load(); }} />;
}

function BiddingPage({ member }) {
  const [rows, setRows] = useState([]);
  const [form, setForm] = useState({ itemName: '', bidder: '', bidDiamonds: '', memo: '' });
  const canManage = member.role === 'ADMIN';
  const load = () => request('/management/bids').then(setRows).catch(() => {});
  useEffect(() => { load(); }, []);
  const add = async (event) => { event.preventDefault(); await request('/management/bids', { method: 'POST', body: JSON.stringify({ ...form, bidDiamonds: Number(form.bidDiamonds || 0), adminMemberId: member.memberId }) }); setForm({ itemName: '', bidder: '', bidDiamonds: '', memo: '' }); await load(); };
  return <CrudPage title="아이템입찰" description="아이템별 입찰자와 입찰 다이아를 조회합니다." canManage={canManage} form={<form className="record-form" onSubmit={add}><input required placeholder="아이템명" value={form.itemName} onChange={(e) => setForm({ ...form, itemName: e.target.value })} /><input required placeholder="입찰자" value={form.bidder} onChange={(e) => setForm({ ...form, bidder: e.target.value })} /><input required type="number" min="0" placeholder="입찰 다이아" value={form.bidDiamonds} onChange={(e) => setForm({ ...form, bidDiamonds: e.target.value })} /><input placeholder="메모" value={form.memo} onChange={(e) => setForm({ ...form, memo: e.target.value })} /><button className="primary-button">입찰 등록</button></form>} rows={rows} getId={(row) => row.itemBidId} columns={['아이템', '입찰자', '입찰가', '메모', '등록일']} render={(row) => [row.itemName, row.bidder, money(row.bidDiamonds), row.memo || '-', new Date(row.createdAt).toLocaleDateString('ko-KR')]} onDelete={async (id) => { await request(`/management/bids/${id}?adminMemberId=${member.memberId}`, { method: 'DELETE' }); await load(); }} />;
}

function CollectionPage({ member }) {
  const [data, setData] = useState({ items: [], members: [], statuses: [], histories: [] });
  const [message, setMessage] = useState('');
  const [itemName, setItemName] = useState('');
  const [editingItem, setEditingItem] = useState(null);
  const [memoByCell, setMemoByCell] = useState({});
  const [savingCell, setSavingCell] = useState('');
  const [showLogs, setShowLogs] = useState(false);
  const load = () => request('/management/collection-dashboard').then(setData).catch((err) => setMessage(err.message));
  useEffect(() => { load(); }, []);
  const statusMap = useMemo(() => {
    const map = new Map();
    data.statuses.forEach((row) => map.set(`${row.memberId}:${row.itemId}`, row));
    return map;
  }, [data.statuses]);
  const addItem = async (event) => {
    event.preventDefault();
    setMessage('');
    try {
      await request('/management/collection-items', {
        method: 'POST',
        body: JSON.stringify({ itemName, actorMemberId: member.memberId }),
      });
      setItemName('');
      await load();
      setMessage('컬렉템 항목을 추가했습니다.');
    } catch (err) {
      setMessage(err.message);
    }
  };
  const renameItem = async () => {
    if (!editingItem?.itemName?.trim()) return;
    setMessage('');
    try {
      await request(`/management/collection-items/${editingItem.itemId}`, {
        method: 'PATCH',
        body: JSON.stringify({ itemName: editingItem.itemName, actorMemberId: member.memberId }),
      });
      setEditingItem(null);
      await load();
      setMessage('컬렉템 항목명을 수정했습니다.');
    } catch (err) {
      setMessage(err.message);
    }
  };
  const deleteItem = async (item) => {
    if (!window.confirm(`${item.itemName} 항목을 숨길까요? 기존 로그는 유지됩니다.`)) return;
    setMessage('');
    try {
      await request(`/management/collection-items/${item.itemId}?actorMemberId=${member.memberId}`, { method: 'DELETE' });
      await load();
      setMessage('컬렉템 항목을 숨겼습니다.');
    } catch (err) {
      setMessage(err.message);
    }
  };
  const updateStatus = async (targetMember, item, state) => {
    const key = `${targetMember.memberId}:${item.itemId}`;
    setSavingCell(key);
    setMessage('');
    try {
      await request('/management/collection-statuses', {
        method: 'PATCH',
        body: JSON.stringify({
          memberId: targetMember.memberId,
          itemId: item.itemId,
          state,
          memo: memoByCell[key] || '',
          actorMemberId: member.memberId,
        }),
      });
      setMemoByCell((prev) => ({ ...prev, [key]: '' }));
      await load();
    } catch (err) {
      setMessage(err.message);
    } finally {
      setSavingCell('');
    }
  };
  const completedCount = data.statuses.filter((row) => row.state === '완료').length;
  const totalCount = data.members.length * data.items.length;
  const itemSummaries = useMemo(() => data.items.map((item) => {
    const rows = data.members.map((targetMember) => {
      const key = `${targetMember.memberId}:${item.itemId}`;
      const status = statusMap.get(key);
      return {
        key,
        member: targetMember,
        status,
        state: status?.state || '미완료',
      };
    });
    const counts = rows.reduce((acc, row) => {
      acc[row.state] = (acc[row.state] || 0) + 1;
      return acc;
    }, { 완료: 0, 미완료: 0, 회수: 0 });
    return { item, rows, counts };
  }), [data.items, data.members, statusMap]);
  return (
    <>
      <div className="page-title">
        <h1>컬렉템 지급현황</h1>
        <p>컬렉션/스킬 지급 여부를 체크하고, 누가 언제 수정했는지 로그로 남깁니다.</p>
      </div>
      <section className="white-card collection-toolbar">
        <div>
          <h2>스킬현황 아이템 관리</h2>
          <p className="subtle">모든 클랜원이 수정할 수 있으며, 변경 내역은 오른쪽 로그에 기록됩니다.</p>
        </div>
        <form className="collection-add-form" onSubmit={addItem}>
          <input value={itemName} onChange={(event) => setItemName(event.target.value)} placeholder="새 컬렉템/스킬명" />
          <button className="primary-button">항목 추가</button>
        </form>
      </section>
      {message && <p className="vault-message">{message}</p>}
      <section className="white-card collection-main-card">
        <div className="section-heading">
          <div>
            <h2>지급현황</h2>
            <p className="subtle">아이템별로 완료/미완료 인원을 나눠서 보여줍니다. 사람 이름 옆 상태를 바꾸면 바로 저장됩니다.</p>
          </div>
          <span className="result-count">완료 {completedCount} / {totalCount}</span>
        </div>
        <div className="collection-summary-grid">
          <div><small>전체 항목</small><b>{data.items.length}개</b></div>
          <div><small>관리 인원</small><b>{data.members.length}명</b></div>
          <div><small>완료율</small><b>{totalCount ? Math.round((completedCount / totalCount) * 100) : 0}%</b></div>
        </div>
        <div className="collection-card-grid">
          {itemSummaries.map(({ item, rows, counts }) => (
            <article className="collection-item-card" key={item.itemId}>
              <header>
                <div>
                  {editingItem?.itemId === item.itemId ? (
                    <span className="collection-item-edit">
                      <input value={editingItem.itemName} onChange={(event) => setEditingItem({ ...editingItem, itemName: event.target.value })} />
                      <button type="button" onClick={renameItem}>저장</button>
                      <button type="button" onClick={() => setEditingItem(null)}>취소</button>
                    </span>
                  ) : (
                    <h3>{item.itemName}</h3>
                  )}
                  <p>완료해야 할 컬렉템/스킬 항목입니다.</p>
                </div>
                {editingItem?.itemId !== item.itemId && (
                  <div className="collection-item-actions">
                    <button type="button" title="항목명 수정" onClick={() => setEditingItem(item)}>이름수정</button>
                    <button type="button" title="항목 숨김" onClick={() => deleteItem(item)}>숨김</button>
                  </div>
                )}
              </header>
              <div className="collection-state-summary">
                <span className="done">완료 <b>{counts.완료 || 0}</b></span>
                <span className="pending">미완료 <b>{counts.미완료 || 0}</b></span>
                <span className="returned">회수 <b>{counts.회수 || 0}</b></span>
              </div>
              <div className="collection-member-list">
                {rows.map(({ key, member: targetMember, status, state }) => (
                  <div className={`collection-member-card ${state}`} key={key}>
                    <div>
                      <b>{targetMember.characterName}</b>
                      <small>{targetMember.guildName || '클랜 미지정'}</small>
                    </div>
                    <select className={`collection-state ${state}`} value={state} disabled={savingCell === key} onChange={(event) => updateStatus(targetMember, item, event.target.value)}>
                      <option>미완료</option>
                      <option>완료</option>
                      <option>회수</option>
                    </select>
                    <input value={memoByCell[key] ?? ''} onChange={(event) => setMemoByCell({ ...memoByCell, [key]: event.target.value })} placeholder={status?.memo || '메모'} />
                    {status?.updatedByName && <small className="collection-updated">{status.updatedByName} · {new Date(status.updatedAt).toLocaleDateString('ko-KR')}</small>}
                  </div>
                ))}
              </div>
            </article>
          ))}
        </div>
        {!data.members.length && <div className="empty-state">등록된 클랜원이 없습니다.</div>}
        {!data.items.length && <div className="empty-state">등록된 컬렉템 항목이 없습니다. 위에서 항목을 먼저 추가하세요.</div>}
      </section>
      <section className="white-card collection-log-card collapsible">
        <button type="button" className="collection-log-toggle" onClick={() => setShowLogs(!showLogs)}>
          <span>변경/지급 로그</span>
          <b>{data.histories.length}건</b>
          <em>{showLogs ? '접기' : '열어보기'}</em>
        </button>
        {showLogs && (
          <div className="collection-log-list">
            {data.histories.map((log) => (
              <div className="collection-log-row" key={log.historyId}>
                <b>{log.action}</b>
                <span>{log.characterName} · {log.itemName}</span>
                <small>{log.previousState || '-'} → {log.nextState || '-'} / {log.editedByName || '-'}</small>
                {log.memo && <em>{log.memo}</em>}
                <time>{new Date(log.createdAt).toLocaleString('ko-KR')}</time>
              </div>
            ))}
            {!data.histories.length && <div className="empty-state">아직 변경 로그가 없습니다.</div>}
          </div>
        )}
      </section>
    </>
  );
}

function ItemRequestPage({ member }) {
  const [rows, setRows] = useState([]);
  const [tab, setTab] = useState('requests');
  const [form, setForm] = useState({ itemName: '', memo: '' });
  const [processMemo, setProcessMemo] = useState({});
  const [message, setMessage] = useState('');
  const isAdmin = member.role === 'ADMIN';
  const load = () => request('/management/item-requests').then(setRows).catch((err) => setMessage(err.message));
  useEffect(() => { load(); }, []);
  const visibleRows = rows.filter((row) => isAdmin || row.requesterMemberId === member.memberId);
  const requestRows = visibleRows.filter((row) => (tab === 'requests' ? row.status !== '지급완료' : row.status === '지급완료'));
  const submit = async (event) => {
    event.preventDefault();
    setMessage('');
    try {
      await request('/management/item-requests', {
        method: 'POST',
        body: JSON.stringify({ requesterMemberId: member.memberId, itemName: form.itemName, memo: form.memo }),
      });
      setForm({ itemName: '', memo: '' });
      await load();
      setMessage('아이템 신청이 접수되었습니다.');
    } catch (err) {
      setMessage(err.message);
    }
  };
  const process = async (row, status) => {
    setMessage('');
    try {
      await request(`/management/item-requests/${row.requestId}`, {
        method: 'PATCH',
        body: JSON.stringify({ actorMemberId: member.memberId, status, processedMemo: processMemo[row.requestId] || '' }),
      });
      setProcessMemo((prev) => ({ ...prev, [row.requestId]: '' }));
      await load();
      setMessage(status === '지급완료' ? '지급완료 처리했습니다.' : '반려 처리했습니다.');
    } catch (err) {
      setMessage(err.message);
    }
  };
  return (
    <>
      <div className="item-request-hero">
        <div>
          <h1>아이템신청 접수내역 & 아이템 등록</h1>
          <p>필요한 아이템을 신청하고, 운영자는 확인 후 지급 처리를 진행합니다.</p>
        </div>
        <span className="result-count">{visibleRows.length}건</span>
      </div>
      <section className="white-card item-request-form-card">
        <form className="item-request-form" onSubmit={submit}>
          <input required value={form.itemName} onChange={(event) => setForm({ ...form, itemName: event.target.value })} placeholder="신청 아이템명" />
          <input value={form.memo} onChange={(event) => setForm({ ...form, memo: event.target.value })} placeholder="메모: 필요한 이유, 옵션, 우선순위 등" />
          <button className="primary-button">+ 신청 등록</button>
        </form>
        {message && <p className="vault-message">{message}</p>}
      </section>
      <section className="white-card">
        <div className="item-request-tabs">
          <button className={tab === 'requests' ? 'active' : ''} onClick={() => setTab('requests')}>신청내역</button>
          <button className={tab === 'paid' ? 'active' : ''} onClick={() => setTab('paid')}>지급내역</button>
        </div>
        <div className="item-request-list">
          {requestRows.map((row) => (
            <article className={`item-request-card ${row.status === '지급완료' ? 'paid' : ''}`} key={row.requestId}>
              <div>
                <small>날짜: {new Date(row.createdAt).toLocaleString('ko-KR')}</small>
                <b>대상자 {row.requesterName}</b>
                <span>신청아이템: {row.itemName}</span>
                {row.memo && <em>{row.memo}</em>}
              </div>
              <div className="item-request-state">
                <span className={`claim-pill ${row.status === '지급완료' ? 'done' : 'pending'}`}>{row.status}</span>
                {row.processedByName && <small>처리자: {row.processedByName}</small>}
                {row.processedMemo && <small>{row.processedMemo}</small>}
              </div>
              {isAdmin && row.status === '접수' && (
                <div className="item-request-actions">
                  <input value={processMemo[row.requestId] || ''} onChange={(event) => setProcessMemo({ ...processMemo, [row.requestId]: event.target.value })} placeholder="처리 메모" />
                  <button className="mini-button" onClick={() => process(row, '지급완료')}>지급완료</button>
                  <button className="role-button danger" onClick={() => process(row, '반려')}>반려</button>
                </div>
              )}
            </article>
          ))}
          {!requestRows.length && <div className="empty-state">표시할 아이템 신청이 없습니다.</div>}
        </div>
      </section>
    </>
  );
}

function SpecHistoryPage({ setPage }) {
  const [rows, setRows] = useState([]);
  const [message, setMessage] = useState('');
  useEffect(() => {
    request('/members/spec-histories').then(setRows).catch((err) => setMessage(err.message));
  }, []);
  const diff = (before, after, empty = '-') => `${before ?? empty} → ${after ?? empty}`;
  return (
    <>
      <AdminBackButton setPage={setPage} />
      <div className="page-title">
        <h1>스펙/장비 수정기록</h1>
        <p>클랜원 정보와 전투력 저장 시 변경 전후 기록을 남깁니다. 최근 기록 위주로 확인합니다.</p>
      </div>
      {message && <p className="vault-message">{message}</p>}
      <section className="white-card spec-history-card">
        <div className="section-heading">
          <h2>최근 수정내역</h2>
          <span className="result-count">{rows.length}건</span>
        </div>
        <div className="spec-history-list">
          {rows.map((row) => (
            <article className="spec-history-row" key={row.historyId}>
              <div>
                <b>{row.characterName}</b>
                <small>{new Date(row.createdAt).toLocaleString('ko-KR')} · 수정자 {row.editedByName || '-'}</small>
              </div>
              <div className="spec-history-grid">
                <span><small>전투력</small>{diff(formatNumber(row.previousCombatPower), formatNumber(row.nextCombatPower))}</span>
                <span><small>레벨</small>{diff(row.previousLevel, row.nextLevel)}</span>
                <span><small>클랜</small>{diff(row.previousGuildName, row.nextGuildName)}</span>
                <span><small>클래스</small>{diff(row.previousCharacterClass, row.nextCharacterClass)}</span>
                <span><small>직급</small>{diff(row.previousRank, row.nextRank)}</span>
                <span><small>상태</small>{diff(row.previousStatus, row.nextStatus)}</span>
              </div>
            </article>
          ))}
          {!rows.length && <div className="empty-state">아직 수정 기록이 없습니다. 클랜원/전투력 관리에서 저장하면 이곳에 기록됩니다.</div>}
        </div>
      </section>
    </>
  );
}

function CrudPage({ title, description, canManage, form, rows, getId, columns, render, onDelete }) {
  return <><div className="page-title"><h1>{title}</h1><p>{description}</p></div>{canManage && <section className="white-card">{form}</section>}<section className="white-card"><div className="table-wrap"><table className="data-table"><thead><tr>{columns.map((column) => <th key={column}>{column}</th>)}{canManage && <th>관리</th>}</tr></thead><tbody>{rows.map((row) => <tr key={getId(row)}>{render(row).map((cell, index) => <td key={index}>{cell}</td>)}{canManage && <td><button className="role-button danger" onClick={() => onDelete(getId(row))}>삭제</button></td>}</tr>)}</tbody></table></div>{!rows.length && <div className="empty-state">아직 등록된 기록이 없습니다.</div>}</section></>;
}

function MyPage({ member, setPage, favoritePages = [] }) {
  const [info, setInfo] = useState(null);
  const [passwordForm, setPasswordForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [passwordMessage, setPasswordMessage] = useState('');
  useEffect(() => { request(`/members/${member.memberId}/my-info`).then(setInfo).catch(() => {}); }, [member.memberId]);
  if (!info) return <LoadingCard />;
  const changePassword = async (event) => {
    event.preventDefault();
    setPasswordMessage('');
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      setPasswordMessage('새 비밀번호 확인이 일치하지 않습니다.');
      return;
    }
    try {
      await request(`/members/${member.memberId}/password`, {
        method: 'PATCH',
        body: JSON.stringify({ currentPassword: passwordForm.currentPassword, newPassword: passwordForm.newPassword }),
      });
      setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' });
      setPasswordMessage('비밀번호를 변경했습니다. 다음 로그인부터 새 비밀번호를 사용하세요.');
    } catch (err) { setPasswordMessage(err.message); }
  };
  return <><div className="page-title"><h1>마이페이지</h1><p>내 계정과 활동 정보를 확인합니다.</p></div><FavoriteLinks favorites={favoritePages} setPage={setPage} /><ProfileCard member={member} info={info} /><section className="white-card"><h2>계정 정보</h2><div className="detail-grid"><div><small>캐릭터명</small><b>{member.characterName}</b></div><div><small>권한</small><b>{member.role === 'ADMIN' ? '운영자' : '클랜원'}</b></div><div><small>회원 번호</small><b>{member.memberId}</b></div></div></section><section className="white-card"><h2>비밀번호 변경</h2><form className="password-form" onSubmit={changePassword}><label>현재 비밀번호<input required type="password" value={passwordForm.currentPassword} onChange={(e) => setPasswordForm({ ...passwordForm, currentPassword: e.target.value })} /></label><label>새 비밀번호<input required type="password" minLength="4" value={passwordForm.newPassword} onChange={(e) => setPasswordForm({ ...passwordForm, newPassword: e.target.value })} /></label><label>새 비밀번호 확인<input required type="password" minLength="4" value={passwordForm.confirmPassword} onChange={(e) => setPasswordForm({ ...passwordForm, confirmPassword: e.target.value })} /></label><button className="primary-button">비밀번호 변경</button></form>{passwordMessage && <p className="vault-message">{passwordMessage}</p>}</section></>;
}

function Admin({ member, setPage, onMemberUpdate, memberOnly = false, favorites = [], toggleFavorite }) {
  const emptyCreateForm = { characterName: '', initialPassword: '112200', guildName: '', characterClass: '', level: '', combatPower: '', rank: '', status: '활동중', active: true };
  const emptyEditForm = { characterName: '', guildName: '', characterClass: '', level: '', combatPower: '', rank: '', status: '', active: true };
  const [members, setMembers] = useState([]);
  const [message, setMessage] = useState('');
  const [loadingId, setLoadingId] = useState(null);
  const [editId, setEditId] = useState(null);
  const [resetTarget, setResetTarget] = useState(null);
  const [resetPassword, setResetPassword] = useState('112200');
  const [editForm, setEditForm] = useState(emptyEditForm);
  const [createForm, setCreateForm] = useState(emptyCreateForm);
  const [bulkEditing, setBulkEditing] = useState(false);
  const [bulkEdits, setBulkEdits] = useState({});
  const load = () => request('/members').then(setMembers).catch((err) => setMessage(err.message));
  useEffect(() => { load(); }, []);

  const memberPayload = (form) => ({
    ...form,
    combatPower: Number(form.combatPower || 0),
    level: Number(form.level || 0),
  });
  const formFromMember = (targetMember) => ({
    characterName: targetMember.characterName ?? '',
    guildName: targetMember.guildName ?? '',
    characterClass: targetMember.characterClass ?? '',
    level: targetMember.level ?? 0,
    combatPower: targetMember.combatPower ?? 0,
    rank: targetMember.rank ?? '',
    status: targetMember.status ?? '',
    active: targetMember.active ?? true,
  });
  const isSameProfile = (left, right) => JSON.stringify(memberPayload(left)) === JSON.stringify(memberPayload(right));

  const createMember = async (event) => {
    event.preventDefault();
    setMessage('');
    try {
      const saved = await request(`/members?adminMemberId=${member.memberId}`, {
        method: 'POST',
        body: JSON.stringify(memberPayload(createForm)),
      });
      setCreateForm(emptyCreateForm);
      await load();
      setMessage(`${saved.characterName} 클랜원을 미리 등록했습니다. 임시 비밀번호는 등록한 값으로 로그인하면 됩니다.`);
    } catch (err) { setMessage(err.message); }
  };

  const startEdit = (targetMember) => {
    setEditId(targetMember.memberId);
    setMessage('');
    setEditForm(formFromMember(targetMember));
  };

  const startBulkEdit = () => {
    setBulkEditing(true);
    setEditId(null);
    setResetTarget(null);
    setMessage('');
    setBulkEdits(Object.fromEntries(members.map((row) => [row.memberId, formFromMember(row)])));
  };

  const cancelBulkEdit = () => {
    setBulkEditing(false);
    setBulkEdits({});
    setMessage('');
  };

  const updateBulkEdit = (memberId, patch) => {
    setBulkEdits((prev) => ({ ...prev, [memberId]: { ...(prev[memberId] || emptyEditForm), ...patch } }));
  };

  const bulkChangedRows = () => members.filter((row) => {
    const edited = bulkEdits[row.memberId];
    return edited && !isSameProfile(formFromMember(row), edited);
  });

  const saveProfile = async (event) => {
    event.preventDefault();
    setLoadingId(editId);
    setMessage('');
    try {
      const saved = await request(`/members/${editId}/profile?adminMemberId=${member.memberId}`, {
        method: 'PATCH',
        body: JSON.stringify(memberPayload(editForm)),
      });
      await load();
      setEditId(null);
      setMessage(`${saved.characterName}의 정보를 수정했습니다.`);
      if (saved.memberId === member.memberId) {
        onMemberUpdate({ ...member, characterName: saved.characterName });
      }
    } catch (err) { setMessage(err.message); } finally { setLoadingId(null); }
  };

  const saveBulkProfiles = async () => {
    const changedRows = bulkChangedRows();
    if (!changedRows.length) {
      setMessage('변경된 클랜원이 없습니다.');
      return;
    }
    setLoadingId('bulk');
    setMessage('');
    try {
      const savedRows = [];
      for (const row of changedRows) {
        const saved = await request(`/members/${row.memberId}/profile?adminMemberId=${member.memberId}`, {
          method: 'PATCH',
          body: JSON.stringify(memberPayload(bulkEdits[row.memberId])),
        });
        savedRows.push(saved);
      }
      await load();
      const current = savedRows.find((row) => row.memberId === member.memberId);
      if (current) {
        onMemberUpdate({ ...member, characterName: current.characterName });
      }
      setBulkEditing(false);
      setBulkEdits({});
      setMessage(`${changedRows.length}명의 정보를 한 번에 저장했습니다.`);
    } catch (err) {
      setMessage(err.message);
    } finally {
      setLoadingId(null);
    }
  };

  const changeRole = async (targetMember, role) => {
    setLoadingId(targetMember.memberId);
    setMessage('');
    try {
      await request(`/members/${targetMember.memberId}/role?role=${role}&adminMemberId=${member.memberId}`, { method: 'PATCH' });
      await load();
      setMessage(`${targetMember.characterName}의 권한을 ${role === 'ADMIN' ? '운영자' : '클랜원'}로 변경했습니다.`);
    } catch (err) { setMessage(err.message); } finally { setLoadingId(null); }
  };

  const startPasswordReset = (targetMember) => {
    setResetTarget(targetMember);
    setResetPassword('112200');
    setMessage('');
  };

  const savePasswordReset = async (event) => {
    event.preventDefault();
    if (!resetTarget) return;
    setLoadingId(resetTarget.memberId);
    setMessage('');
    try {
      await request(`/members/${resetTarget.memberId}/password/reset?adminMemberId=${member.memberId}`, {
        method: 'PATCH',
        body: JSON.stringify({ newPassword: resetPassword || '112200' }),
      });
      setMessage(`${resetTarget.characterName}의 비밀번호를 초기화했습니다. 새 비밀번호: ${resetPassword || '112200'}`);
      setResetTarget(null);
      setResetPassword('112200');
    } catch (err) { setMessage(err.message); } finally { setLoadingId(null); }
  };

  const deleteMember = async (targetMember) => {
    if (!window.confirm(`${targetMember.characterName} 클랜원을 삭제할까요? 과거 기록은 보존되고 목록에서는 숨김 처리됩니다.`)) return;
    setLoadingId(targetMember.memberId);
    setMessage('');
    try {
      await request(`/members/${targetMember.memberId}?adminMemberId=${member.memberId}`, { method: 'DELETE' });
      await load();
      setMessage(`${targetMember.characterName} 클랜원을 삭제했습니다.`);
    } catch (err) { setMessage(err.message); } finally { setLoadingId(null); }
  };

  if (!memberOnly) {
    return (
      <>
        <div className="page-title"><h1>관리자 설정</h1><p>클랜 운영에 필요한 설정 메뉴입니다.</p></div>
        <div className="admin-grid">
          {adminCards.map(([icon, title, color, target]) => (
            <button className={`admin-card ${color}`} key={title} onClick={() => setPage(target)}>
              <span>{icon}</span><b>{title}</b><small>{target === 'attendance' ? '출석 인식·보스 기록 관리' : target === 'member-admin' ? '클랜원 정보·전투력 관리' : '바로 이동'}</small>
            </button>
          ))}
        </div>
      </>
    );
  }

  return (
    <>
      <div className="page-title"><h1>클랜원/전투력 관리</h1><p>클랜원 정보, 전투력, 비밀번호, 권한, 삭제 여부를 관리합니다.</p></div>
      <button className="outline-button no-margin" onClick={() => setPage('admin')}>← 관리자 설정으로</button>

      <section className="white-card role-card">
        <div className="section-heading">
          <div><h2>클랜원 미리 등록</h2><p className="subtle">운영자가 캐릭터 정보를 먼저 넣어두면, 클랜원은 임시 비밀번호로 로그인한 뒤 마이페이지에서 직접 변경할 수 있습니다.</p></div>
        </div>
        <form className="admin-create-form" onSubmit={createMember}>
          <label>닉네임<input required value={createForm.characterName} onChange={(e) => setCreateForm({ ...createForm, characterName: e.target.value })} /></label>
          <label>임시 비밀번호<input required value={createForm.initialPassword} onChange={(e) => setCreateForm({ ...createForm, initialPassword: e.target.value })} /></label>
          <label>클랜<select value={createForm.guildName} onChange={(e) => setCreateForm({ ...createForm, guildName: e.target.value })}><option value="">클랜 선택</option>{clanOptions.map((clan) => <option key={clan} value={clan}>{clan}</option>)}</select></label>
          <label>클래스<input value={createForm.characterClass} onChange={(e) => setCreateForm({ ...createForm, characterClass: e.target.value })} /></label>
          <label>레벨<input type="number" min="0" value={createForm.level} onChange={(e) => setCreateForm({ ...createForm, level: e.target.value })} /></label>
          <label>전투력<input type="number" min="0" value={createForm.combatPower} onChange={(e) => setCreateForm({ ...createForm, combatPower: e.target.value })} /></label>
          <label>직급<input placeholder="예: 장로, 정예, 일반" value={createForm.rank} onChange={(e) => setCreateForm({ ...createForm, rank: e.target.value })} /></label>
          <label>상태<input value={createForm.status} onChange={(e) => setCreateForm({ ...createForm, status: e.target.value })} /></label>
          <button className="primary-button">미리 등록</button>
        </form>
      </section>

      <section className="white-card role-card">
        <div className="section-heading">
          <div><h2>클랜원 관리</h2><p className="subtle">닉네임, 길드, 클래스, 레벨, 전투력, 상태, 권한을 관리합니다.</p></div>
          <div className="bulk-edit-actions">
            <span className="result-count">{members.length}명</span>
            {bulkEditing ? (
              <>
                <span className="result-count changed">{bulkChangedRows().length}명 변경</span>
                <button className="primary-button no-margin" disabled={loadingId === 'bulk'} onClick={saveBulkProfiles}>{loadingId === 'bulk' ? '저장중...' : '전체 저장'}</button>
                <button className="outline-button no-margin" disabled={loadingId === 'bulk'} onClick={cancelBulkEdit}>취소</button>
              </>
            ) : (
              <button className="outline-button no-margin" onClick={startBulkEdit}>전체수정</button>
            )}
          </div>
        </div>
        {message && <p className="vault-message">{message}</p>}
        {resetTarget && (
          <form className="admin-reset-form" onSubmit={savePasswordReset}>
            <div>
              <b>{resetTarget.characterName}</b>
              <small>비밀번호 초기화</small>
            </div>
            <label>새 비밀번호<input required minLength="4" value={resetPassword} onChange={(e) => setResetPassword(e.target.value)} /></label>
            <button className="primary-button" disabled={loadingId === resetTarget.memberId}>초기화</button>
            <button type="button" className="role-button" onClick={() => setResetTarget(null)}>취소</button>
          </form>
        )}
        <div className="table-wrap">
          <table className="data-table role-table">
            <thead><tr><th>닉네임</th><th>길드</th><th>클래스</th><th>레벨</th><th>전투력</th><th>직급</th><th>상태</th><th>권한</th><th>정보수정</th><th>비밀번호</th><th>권한변경</th><th>삭제</th></tr></thead>
            <tbody>{members.map((row) => {
              const bulkForm = bulkEdits[row.memberId] || formFromMember(row);
              return (
              <React.Fragment key={row.memberId}>
                <tr>
                  <td>{bulkEditing ? <input className="bulk-table-input name" required value={bulkForm.characterName} onChange={(e) => updateBulkEdit(row.memberId, { characterName: e.target.value })} /> : row.characterName}</td>
                  <td>{bulkEditing ? <select className="bulk-table-input" value={bulkForm.guildName} onChange={(e) => updateBulkEdit(row.memberId, { guildName: e.target.value })}><option value="">클랜 선택</option>{clanOptions.map((clan) => <option key={clan} value={clan}>{clan}</option>)}</select> : (row.guildName || '-')}</td>
                  <td>{bulkEditing ? <input className="bulk-table-input" value={bulkForm.characterClass} onChange={(e) => updateBulkEdit(row.memberId, { characterClass: e.target.value })} /> : (row.characterClass || '-')}</td>
                  <td>{bulkEditing ? <input className="bulk-table-input small" type="number" min="0" value={bulkForm.level} onChange={(e) => updateBulkEdit(row.memberId, { level: e.target.value })} /> : (row.level ? `Lv.${row.level}` : '-')}</td>
                  <td>{bulkEditing ? <input className="bulk-table-input power" required type="number" min="0" value={bulkForm.combatPower} onChange={(e) => updateBulkEdit(row.memberId, { combatPower: e.target.value })} /> : formatNumber(row.combatPower)}</td>
                  <td>{bulkEditing ? <input className="bulk-table-input" value={bulkForm.rank} onChange={(e) => updateBulkEdit(row.memberId, { rank: e.target.value })} /> : (row.rank || '-')}</td>
                  <td>{bulkEditing ? <><input className="bulk-table-input" value={bulkForm.status} onChange={(e) => updateBulkEdit(row.memberId, { status: e.target.value })} /><select className="bulk-table-input mini" value={bulkForm.active ? 'true' : 'false'} onChange={(e) => updateBulkEdit(row.memberId, { active: e.target.value === 'true' })}><option value="true">활성</option><option value="false">비활성</option></select></> : (row.active ? (row.status || '활성') : '비활성')}</td>
                  <td><span className={row.role === 'ADMIN' ? 'role-pill admin' : 'role-pill member'}>{row.role === 'ADMIN' ? '운영자' : '클랜원'}</span></td>
                  <td>{bulkEditing ? <span className="bulk-editing-label">전체수정중</span> : <button className="role-button" disabled={loadingId === row.memberId} onClick={() => startEdit(row)}>{editId === row.memberId ? '수정중' : '수정'}</button>}</td>
                  <td><button className="role-button key-button" title="비밀번호 초기화" disabled={bulkEditing || loadingId === row.memberId} onClick={() => startPasswordReset(row)}>🔑</button></td>
                  <td>{row.role === 'ADMIN'
                    ? <button className="role-button danger" disabled={bulkEditing || loadingId === row.memberId || row.memberId === member.memberId} onClick={() => changeRole(row, 'MEMBER')}>{row.memberId === member.memberId ? '본인 해제 불가' : '클랜원으로 변경'}</button>
                    : <button className="role-button" disabled={bulkEditing || loadingId === row.memberId} onClick={() => changeRole(row, 'ADMIN')}>운영자로 지정</button>}</td>
                  <td><button className="role-button danger" disabled={bulkEditing || loadingId === row.memberId || row.memberId === member.memberId} onClick={() => deleteMember(row)}>{row.memberId === member.memberId ? '본인 삭제 불가' : '삭제'}</button></td>
                </tr>
                {!bulkEditing && editId === row.memberId && (
                  <tr className="member-edit-row">
                    <td colSpan="12">
                      <form className="admin-edit-form inline-member-edit" onSubmit={saveProfile}>
                        <label>닉네임<input required value={editForm.characterName} onChange={(e) => setEditForm({ ...editForm, characterName: e.target.value })} /></label>
                        <label>클랜<select value={editForm.guildName} onChange={(e) => setEditForm({ ...editForm, guildName: e.target.value })}><option value="">클랜 선택</option>{clanOptions.map((clan) => <option key={clan} value={clan}>{clan}</option>)}</select></label>
                        <label>클래스<input value={editForm.characterClass} onChange={(e) => setEditForm({ ...editForm, characterClass: e.target.value })} /></label>
                        <label>레벨<input type="number" min="0" value={editForm.level} onChange={(e) => setEditForm({ ...editForm, level: e.target.value })} /></label>
                        <label>전투력<input required type="number" min="0" value={editForm.combatPower} onChange={(e) => setEditForm({ ...editForm, combatPower: e.target.value })} /></label>
                        <label>직급<input placeholder="예: 장로, 정예, 일반" value={editForm.rank} onChange={(e) => setEditForm({ ...editForm, rank: e.target.value })} /></label>
                        <label>상태<input placeholder="예: 활동중, 휴면, 탈퇴예정" value={editForm.status} onChange={(e) => setEditForm({ ...editForm, status: e.target.value })} /></label>
                        <label>활성<select value={editForm.active ? 'true' : 'false'} onChange={(e) => setEditForm({ ...editForm, active: e.target.value === 'true' })}><option value="true">활성</option><option value="false">비활성</option></select></label>
                        <button className="primary-button" disabled={loadingId === editId}>저장</button>
                        <button type="button" className="role-button" onClick={() => setEditId(null)}>취소</button>
                      </form>
                    </td>
                  </tr>
                )}
              </React.Fragment>
            );})}</tbody>
          </table>
        </div>
        {!members.length && <div className="empty-state">등록된 클랜원이 없습니다.</div>}
      </section>
    </>
  );
}

function RosterScan() {
  const [members, setMembers] = useState([]);
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState('');
  const [status, setStatus] = useState('');
  const [progress, setProgress] = useState(0);
  const [text, setText] = useState('');
  const [result, setResult] = useState([]);
  useEffect(() => { request('/members').then(setMembers).catch(() => {}); }, []);
  const selectFile = (event) => { const next = event.target.files?.[0]; if (!next) return; setFile(next); setPreview(URL.createObjectURL(next)); setText(''); setResult([]); setStatus(''); };
  const scan = async () => {
    if (!file) return;
    setStatus('이미지에서 이름을 읽는 중입니다...');
    setProgress(0);
    try {
      const ocrText = await recognizeImageTextMultiple(file, setProgress);
      setText(ocrText);
      const memberByNormalized = new Map(members.map((m) => [normalize(m.characterName), m.characterName]));
      const names = extractOcrNames(ocrText, members).slice(0, 40);
      const registeredCount = names.filter((name) => memberByNormalized.has(normalize(name))).length;
      setResult(names.map((name) => {
        const registeredName = memberByNormalized.get(normalize(name));
        return registeredName
          ? { name: registeredName, state: 'registered', detail: '등록된 클랜원과 일치' }
          : { name, state: 'review', detail: 'OCR 인식 결과 · 확인 필요' };
      }));
      setStatus(registeredCount ? `${registeredCount}명의 등록 클랜원을 찾았습니다. 5회 보정 인식 결과를 합쳤습니다.` : '자동 일치된 클랜원이 없습니다. 인식 결과를 확인해 주세요.');
    } catch {
      setStatus('이미지를 읽지 못했습니다. 이름 부분이 선명하게 보이는 사진으로 다시 시도해 주세요.');
    } finally {
      setProgress(100);
    }
  };
  return <><div className="page-title"><h1>출석체크</h1><p>파티 구성 스크린샷에서 캐릭터 이름을 읽어 클랜원 목록과 비교합니다.</p></div><section className="white-card roster-card"><div className="roster-head"><div><h2>파티 구성 불러오기</h2><p>스크린샷은 브라우저 안에서만 분석하며, 등록된 클랜원과 일치하는지 확인합니다.</p></div><span className="local-badge">기기 OCR</span></div><div className="roster-layout"><label className="upload-zone"><input type="file" accept="image/png,image/jpeg,image/webp" onChange={selectFile} />{preview ? <img src={preview} alt="업로드한 파티 구성" /> : <><b>↥</b><strong>파티 스크린샷 업로드</strong><span>PNG, JPG, WEBP</span></>}</label><div className="scan-guide"><h3>인식 안내</h3><ol><li>캐릭터 이름이 선명하게 보이도록 올려주세요.</li><li>등록된 이름은 자동으로 일치 처리합니다.</li><li>오인식 후보는 운영자가 검토합니다.</li></ol><button className="primary-button" disabled={!file || status.includes('읽는 중')} onClick={scan}>{status.includes('읽는 중') ? `글자 인식 중 ${progress}%` : '이름 인식 시작'}</button></div></div>{status && <div className="scan-status">{status}</div>}</section>{(result.length > 0 || text) && <section className="white-card"><div className="section-heading"><div><h2>인식 결과 검토</h2><p className="subtle">현재는 후보 검토 화면이며, 다음 단계에서 선택한 인원을 출석 기록으로 저장하게 연결하면 됩니다.</p></div><span className="result-count">{result.length}개 후보</span></div><div className="scan-results">{result.map((item, index) => <div className="scan-result" key={`${item.name}-${index}`}><span className={item.state === 'registered' ? 'match-icon yes' : 'match-icon wait'}>{item.state === 'registered' ? '✓' : '?'}</span><div><b>{item.name}</b><small>{item.detail}</small></div><span className={item.state === 'registered' ? 'match-state registered' : 'match-state review'}>{item.state === 'registered' ? '등록됨' : '확인 필요'}</span></div>)}</div><details className="raw-ocr"><summary>OCR 원문 보기</summary><pre>{text}</pre></details></section>}</>;
}

function AccessDenied() { return <section className="placeholder-page"><div className="white-card"><span>🔒</span><h1>운영자 전용 화면</h1><p>이 메뉴는 운영자만 사용할 수 있습니다.<br />일반 클랜원은 조회 메뉴를 이용할 수 있습니다.</p></div></section>; }
function LoadingCard() { return <section className="white-card loading-card">정보를 불러오는 중입니다...</section>; }

function RosterScanAdmin({ setPage }) { return <><AdminBackButton setPage={setPage} /><RosterScan /></>; }

function MemberAdminPage({ member, setPage, onMemberUpdate }) {
  return <><AdminBackButton setPage={setPage} /><Admin member={member} setPage={setPage} onMemberUpdate={onMemberUpdate} memberOnly /></>;
}

export default function App() {
  const [member, setMember] = useState(() => JSON.parse(sessionStorage.getItem('clanMember') || 'null'));
  const [page, setPage] = useState('lobby');
  const [favorites, setFavorites] = useState(() => readFavorites(JSON.parse(sessionStorage.getItem('clanMember') || 'null')));
  const login = (data) => { sessionStorage.setItem('clanMember', JSON.stringify(data)); setMember(data); };
  const updateCurrentMember = (data) => { sessionStorage.setItem('clanMember', JSON.stringify(data)); setMember(data); };
  const logout = () => { sessionStorage.removeItem('clanMember'); setMember(null); setPage('lobby'); setFavorites([]); };
  useEffect(() => { if (member) setFavorites(readFavorites(member)); }, [member?.memberId]);
  const visibleFavoritePages = favorites
    .filter((id) => member?.role === 'ADMIN' || !adminOnlyPages.has(id))
    .map(pageMeta);
  const toggleFavorite = (targetPage) => {
    setFavorites((prev) => {
      const next = prev.includes(targetPage) ? prev.filter((id) => id !== targetPage) : [...prev, targetPage];
      writeFavorites(member, next);
      return next;
    });
  };
  if (!member) return <AuthScreen onLogin={login} />;
  if (member.role !== 'ADMIN' && adminOnlyPages.has(page)) return <Shell member={member} page={page} setPage={setPage} onLogout={logout} favorites={favorites} toggleFavorite={toggleFavorite}><AccessDenied /></Shell>;
  const view = page === 'lobby' ? <Lobby member={member} setPage={setPage} favoritePages={visibleFavoritePages} />
    : page === 'my-info' ? <MyInfo member={member} />
    : page === 'participation' ? <Participation member={member} setPage={setPage} />
    : page === 'attendance' ? <Attendance member={member} setPage={setPage} />
    : page === 'payment' ? <PaymentClaimPage member={member} />
    : page === 'ledger' ? <ClanVaultPage member={member} />
    : page === 'book' ? <ClanVaultPage member={member} readonly />
    : page === 'inventory' ? <InventoryPage member={member} />
    : page === 'bidding' ? <BiddingPage member={member} />
    : page === 'collection' ? <CollectionPage member={member} />
    : page === 'item-request' ? <ItemRequestPage member={member} />
    : page === 'spec-history' ? <SpecHistoryPage setPage={setPage} />
    : page === 'roster' ? <RosterScanAdmin setPage={setPage} />
    : page === 'pinball' ? <PinballPage setPage={setPage} />
    : page === 'mypage' ? <MyPage member={member} setPage={setPage} favoritePages={visibleFavoritePages} />
    : page === 'admin' ? <Admin member={member} setPage={setPage} onMemberUpdate={updateCurrentMember} favorites={favorites} toggleFavorite={toggleFavorite} />
    : page === 'member-admin' ? <MemberAdminPage member={member} setPage={setPage} onMemberUpdate={updateCurrentMember} />
    : <Lobby member={member} setPage={setPage} favoritePages={visibleFavoritePages} />;
  return <Shell member={member} page={page} setPage={setPage} onLogout={logout} favorites={favorites} toggleFavorite={toggleFavorite}>{view}</Shell>;
}
