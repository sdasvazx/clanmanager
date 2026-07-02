import { useEffect, useMemo, useState } from 'react';
import { createWorker } from 'tesseract.js';
import './roster.css';
import './vault.css';
import './manager.css';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';

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

const adminOnlyPages = new Set(['ledger', 'roster', 'admin', 'member-admin']);

const adminCards = [
  ['✓', '출석체크', 'mint', 'roster'],
  ['♕', '출석보스 설정', 'rose', 'attendance'],
  ['♙', '클랜원 정보수정', 'blue', 'member-admin'],
  ['◴', '출석기록 관리', 'purple', 'attendance'],
  ['⚙', '가중치 설정', 'orange', 'participation'],
  ['ϟ', '전투력 관리', 'gold', 'member-admin'],
  ['✿', '기타 설정', 'indigo', 'admin'],
  ['▣', '스펙/장비 수정기록', 'amber', 'collection'],
  ['▥', '참여율 선택조회', 'cyan', 'participation'],
  ['⚠', '게헨나감지', 'red', 'roster'],
];

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
const toMemberId = (value) => {
  const id = Number(value?.member?.memberId ?? value?.memberId ?? value);
  return Number.isFinite(id) ? id : null;
};
const normalize = (value) => String(value ?? '').toLowerCase().replace(/[^0-9a-z가-힣]/g, '');
const clanOptions = ['귀신', '운좋은사람들', '귀신Z', '로망', '게헨나', '미분류'];
const bossOptions = ['13시 보스', '17시 보스', '21시 보스', '정예던전보스', '에노크', '마슈미드', '클랜임무', '수호', '쟁탈전'];
const clanDisplayOrder = ['귀신', '운좋은사람들', '귀신Z', '로망', '게헨나', '미분류'];

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
  const memberByNormalized = new Map(registeredMembers.map((m) => [normalize(m.characterName), m.characterName]));
  const wholeText = String(text ?? '');
  const matched = registeredMembers
    .filter((m) => normalize(m.characterName).length > 1 && normalize(wholeText).includes(normalize(m.characterName)))
    .map((m) => m.characterName);
  const guessed = wholeText
    .split(/\r?\n/)
    .flatMap((line) => line.split(/\s{2,}|\t|,/))
    .map((line) => line.replace(/Lv\.?\s*\d+/gi, '').replace(/레벨\s*\d+/g, '').replace(/[|()[\]{}]/g, ' ').trim())
    .filter((line) => /^[0-9A-Za-z가-힣_-]{2,12}$/.test(line))
    .map((line) => memberByNormalized.get(normalize(line)) ?? line);
  return [...new Set([...matched, ...guessed])];
}

function namesFromText(value) {
  return [...new Set(String(value ?? '').split(/\r?\n|,/).map((name) => name.trim()).filter(Boolean))];
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

function Shell({ member, page, setPage, onLogout, children }) {
  const [collapsed, setCollapsed] = useState(false);
  const visibleMenu = menu.filter(([id]) => member.role === 'ADMIN' || !adminOnlyPages.has(id));
  return (
    <div className={`shell ${collapsed ? 'collapsed' : ''}`}>
      <header className="topbar">
        <button className="hamburger" onClick={() => setCollapsed(!collapsed)}>☰</button>
        <div className="brand-mark">C</div>
        <div className="topbar-spacer" />
        <button className="circle-button">☾</button>
        <button className="profile-menu"><b>{member.characterName.slice(0, 1)}</b><span>{member.characterName}</span><small>{member.role === 'ADMIN' ? '운영자' : '클랜원'}</small><i>⌄</i></button>
        <button className="logout-icon" title="로그아웃" onClick={onLogout}>⇥</button>
      </header>
      <aside className="sidebar">
        <nav>{visibleMenu.map(([id, icon, label]) => <button key={id} className={page === id ? 'menu-item active' : 'menu-item'} onClick={() => setPage(id)}><span className="menu-icon">{icon}</span><span>{label}</span></button>)}</nav>
        <div className="side-note"><b>클랜 매니저</b><small>{member.role === 'ADMIN' ? '운영자 모드입니다' : '클랜 활동을 확인하세요'}</small></div>
      </aside>
      <main className="content">{children}</main>
    </div>
  );
}

function NoticePanel({ member, notices, onReload }) {
  const [open, setOpen] = useState(false);
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

  return (
    <section className="white-card notices">
      <div className="section-heading">
        <h2>공지</h2>
        {canManage && <button className="small-primary" onClick={() => setOpen(!open)}>+ 추가</button>}
      </div>
      {open && <form className="inline-form" onSubmit={save}><input required placeholder="공지 제목" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /><textarea required placeholder="공지 내용" value={form.content} onChange={(e) => setForm({ ...form, content: e.target.value })} /><button className="primary-button">공지 등록</button>{message && <p className="form-error">{message}</p>}</form>}
      {notices.length ? notices.map((n) => <article key={n.noticeId} className="notice-row"><b>{n.title}</b><p>{n.content}</p><small>{new Date(n.createdAt).toLocaleString('ko-KR')}</small></article>) : <div className="empty-state">아직 등록된 공지사항이 없습니다.</div>}
    </section>
  );
}

function Lobby({ member, setPage }) {
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
      <ParticipationRanking rows={participationRows} totalCount={members.length} />
      <section className="white-card quick-actions"><h2>빠른 메뉴</h2><div><button onClick={() => setPage('attendance')}>오늘의 출석 확인</button><button onClick={() => setPage('participation')}>참여율 조회</button>{member.role === 'ADMIN' && <button onClick={() => setPage('ledger')}>클랜금고 관리</button>}</div></section>
    </>
  );
}

function ParticipationRanking({ rows, totalCount }) {
  const groups = groupByClan(rows);
  return <section className="white-card lobby-participation-card"><h2>🏆 클랜별 참여율 순위</h2>{groups.map(([clan, list]) => <div className="clan-ranking-block" key={clan}><div className="section-heading"><h3>{clan}</h3><span className="result-count">{list.length}명</span></div><table className="lobby-ranking-table"><thead><tr><th>순위</th><th>닉네임</th><th>참여점수</th><th>참여율(%)</th></tr></thead><tbody>{list.slice(0, 10).map((m, i) => <tr key={m.memberId}><td>{i < 3 ? ['🥇', '🥈', '🥉'][i] : i + 1}</td><td>{m.characterName}</td><td>{m.attendanceCount}</td><td>{m.participationRate}%</td></tr>)}</tbody></table><p className="ranking-footnote">상위 10명 표시 중 (클랜 총 {list.length}명)</p></div>)}{rows.length ? <p className="ranking-footnote">전체 등록 클랜원 {totalCount}명 기준</p> : <div className="empty-state">등록된 클랜원이 없습니다.</div>}</section>;
}

function Ranking({ title, rows, field, power, participation }) {
  return <section className="white-card ranking"><h2>{title}</h2><table><thead><tr><th>순위</th><th>닉네임</th>{participation ? <><th>참여횟수</th><th>참여율</th></> : <th>{field}</th>}</tr></thead><tbody>{rows.slice(0, 10).map((m, i) => <tr key={m.memberId}><td>{i < 3 ? ['🥇', '🥈', '🥉'][i] : i + 1}</td><td>{m.characterName}</td>{participation ? <><td>{m.attendanceCount}회</td><td className="blue-text">{m.participationRate}%</td></> : <td>{power ? formatNumber(m.combatPower) : '-'}</td>}</tr>)}</tbody></table>{!rows.length && <div className="empty-state">등록된 클랜원이 없습니다.</div>}</section>;
}

function MyInfo({ member }) {
  const [info, setInfo] = useState(null);
  useEffect(() => { request(`/members/${member.memberId}/my-info`).then(setInfo).catch(() => {}); }, [member.memberId]);
  if (!info) return <LoadingCard />;
  return <><div className="page-title"><h1>내 정보 확인</h1></div><ProfileCard member={member} info={info} /></>;
}

function ProfileCard({ member, info }) {
  return (
    <section className="white-card profile-overview">
      <div className="profile-name"><span className="avatar">{info.characterName.slice(0, 1)}</span><div><h2>{info.characterName}</h2><div className="pills"><span>{member.role === 'ADMIN' ? '운영자' : '클랜원'}</span><span>전투력 {formatNumber(info.combatPower)}</span></div></div></div>
      <div className="metric-grid"><Metric label="현재 참여율" value={`${info.participationRate}%`} caption={`참여 ${info.myAttendanceCount}회 / 1등 ${info.topAttendanceCount}회`} tone="blue" /><Metric label="기여율" value={`${info.participationRate}%`} caption="최고 참여 횟수를 100%로 계산" tone="green" /><Metric label="참석 횟수" value={`${info.myAttendanceCount}회`} caption="전체 활동 기준" tone="purple" /></div>
      <div className="formula-row"><div><b>참여율 계산 기준</b><p>내 참석 횟수 / 가장 많이 참석한 캐릭터의 참석 횟수 × 100</p></div><div><b>현재 100% 기준</b><p>{info.topAttendanceCount}회</p></div></div>
    </section>
  );
}

function Metric({ label, value, caption, tone }) { return <div className={`metric ${tone}`}><p>{label}</p><strong>{value}</strong><small>{caption}</small></div>; }

function Participation() {
  const [members, setMembers] = useState([]);
  const [attendances, setAttendances] = useState([]);
  useEffect(() => {
    Promise.allSettled([request('/members'), request('/attendances')]).then(([memberResult, attendanceResult]) => {
      if (memberResult.status === 'fulfilled') setMembers(Array.isArray(memberResult.value) ? memberResult.value : []);
      if (attendanceResult.status === 'fulfilled') setAttendances(Array.isArray(attendanceResult.value) ? attendanceResult.value : []);
    });
  }, []);
  const rows = useMemo(() => {
    const counts = new Map();
    attendances.filter((row) => row.status === 'ATTENDED').forEach((row) => {
      const memberId = toMemberId(row);
      if (memberId !== null) counts.set(memberId, (counts.get(memberId) || 0) + 1);
    });
    const top = Math.max(0, ...members.map((m) => counts.get(toMemberId(m)) || 0));
    return members.map((m) => {
      const count = counts.get(toMemberId(m)) || 0;
      return { ...m, count, rate: top ? Math.round((count / top) * 1000) / 10 : 0 };
    }).sort((a, b) => b.count - a.count || (b.combatPower || 0) - (a.combatPower || 0));
  }, [members, attendances]);
  const groups = groupByClan(rows);
  return <><div className="page-title"><h1>참여율·기여율 조회</h1><p>가장 많이 참석한 캐릭터를 100%로 잡고, 클랜별로 나눠 계산 결과를 표시합니다.</p></div><section className="white-card"><div className="info-banner"><b>참여율 계산 기준</b><span>내 참석 횟수 / 1등 참석 횟수 × 100</span></div>{groups.map(([clan, list]) => <div className="clan-ranking-block" key={clan}><div className="section-heading"><h2>{clan}</h2><span className="result-count">{list.length}명</span></div><div className="table-wrap"><table className="data-table"><thead><tr><th>순위</th><th>닉네임</th><th>참석</th><th>참여율</th><th>기여율</th></tr></thead><tbody>{list.map((m, i) => <tr key={m.memberId}><td>{i + 1}</td><td>{m.characterName}</td><td>{m.count}회</td><td className="blue-text">{m.rate}%</td><td className="green-text">{m.rate}%</td></tr>)}</tbody></table></div></div>)}{!rows.length && <div className="empty-state">클랜원이 등록되면 이곳에 순위가 표시됩니다.</div>}</section></>;
}

function Attendance({ member }) {
  const [records, setRecords] = useState([]);
  const [members, setMembers] = useState([]);
  const [selectedRecord, setSelectedRecord] = useState(null);
  const [selectedMembers, setSelectedMembers] = useState([]);
  const [form, setForm] = useState({ bossDate: today(), cutTime: '21:00', bossName: '21시 보스', score: 1, clanName: '로망', memo: '' });
  const [draftByClan, setDraftByClan] = useState({});
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState('');
  const [ocrStatus, setOcrStatus] = useState('');
  const [progress, setProgress] = useState(0);
  const [message, setMessage] = useState('');

  const currentDraftNames = draftByClan[form.clanName] ?? '';
  const totalDraftCount = Object.values(draftByClan).reduce((sum, text) => sum + namesFromText(text).length, 0);
  const updateCurrentDraft = (value) => setDraftByClan((prev) => ({ ...prev, [form.clanName]: value }));

  const load = () => Promise.all([request('/boss-participations'), request('/members')])
    .then(([recordRows, memberRows]) => { setRecords(recordRows); setMembers(memberRows); })
    .catch((err) => setMessage(err.message));

  useEffect(() => { load(); }, []);

  const selectFile = (event) => {
    const nextFile = event.target.files?.[0];
    setFile(nextFile || null);
    setPreview(nextFile ? URL.createObjectURL(nextFile) : '');
    setOcrStatus('');
    setProgress(0);
  };

  const scanImage = async () => {
    if (!file) return;
    setOcrStatus('스샷 글자를 읽는 중입니다.');
    setProgress(0);
    try {
      const worker = await createWorker('kor+eng', 1, {
        logger: (log) => {
          if (log.status === 'recognizing text') setProgress(Math.round(log.progress * 100));
        },
      });
      const { data } = await worker.recognize(file);
      await worker.terminate();
      const names = extractOcrNames(data.text, members);
      const merged = [...new Set([...namesFromText(currentDraftNames), ...names])];
      updateCurrentDraft(merged.join('\n'));
      setOcrStatus(`${names.length}명 후보를 찾았습니다. 저장 전 명단을 확인해 주세요.`);
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

  const openRoster = async (record) => {
    setSelectedRecord(record);
    setSelectedMembers([]);
    try {
      setSelectedMembers(await request(`/boss-participations/${record.recordId}/members`));
    } catch (err) {
      setMessage(err.message);
    }
  };

  const groupedSelectedMembers = useMemo(() => selectedMembers.reduce((acc, row) => {
    const key = row.clanName || '미분류';
    acc[key] = [...(acc[key] || []), row];
    return acc;
  }, {}), [selectedMembers]);

  const visibleRecords = records.slice(0, 100);

  return (
    <>
      <div className="page-title">
        <h1>보스 참여내역 조회</h1>
        <p>각 클랜 스크린샷을 OCR로 읽어 보스 회차별 참석 인원과 명단을 기록합니다.</p>
      </div>

      {member.role === 'ADMIN' && (
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
          <div className="boss-draft-summary">
            {clanOptions.map((clan) => <span key={clan} className={namesFromText(draftByClan[clan]).length ? 'ready' : ''}>{clan} {namesFromText(draftByClan[clan]).length}명</span>)}
          </div>
          {ocrStatus && <div className="scan-status">{ocrStatus}</div>}
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
                  <td><ClanCountBadges record={record} /></td>
                  <td><b>{record.score}</b></td>
                  <td><button className="roster-button" onClick={() => openRoster(record)}>명단보기</button></td>
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
              <h2>{selectedRecord.bossDate} · {selectedRecord.bossName} 명단</h2>
              <p className="subtle">총 {selectedMembers.length}명 · 미등록 이름은 확인 필요로 표시됩니다.</p>
            </div>
            <button className="outline-button no-margin" onClick={() => setSelectedRecord(null)}>닫기</button>
          </div>
          <div className="boss-roster-groups">
            {Object.entries(groupedSelectedMembers).map(([clanName, list]) => (
              <div className="boss-roster-group" key={clanName}>
                <h3>{clanName} <span>{list.length}명</span></h3>
                <div>{list.map((row) => <span className={row.matched ? 'member-chip matched' : 'member-chip review'} key={row.participationMemberId}>{row.characterName}</span>)}</div>
              </div>
            ))}
          </div>
        </section>
      )}
    </>
  );
}

function ClanCountBadges({ record }) {
  const entries = Object.entries(record.clanCounts || {});
  return (
    <div className="clan-counts">
      <span className="clan-badge total">전체 {record.totalCount}명</span>
      {entries.map(([clan, count]) => <span className={`clan-badge ${normalize(clan)}`} key={clan}>{clan} {count}명</span>)}
    </div>
  );
}

function TimeBadge({ value, dateTime = false }) {
  const { period, time } = dateTime ? splitDateTimeKoreanTime(value) : splitKoreanTime(value);
  return <>{period && <span className="time-badge">{period}</span>} {time}</>;
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
  const [rows, setRows] = useState([]);
  const [form, setForm] = useState({ characterName: '', itemName: '', state: '완료', memo: '' });
  const canManage = member.role === 'ADMIN';
  const load = () => request('/management/collections').then(setRows).catch(() => {});
  useEffect(() => { load(); }, []);
  const add = async (event) => { event.preventDefault(); await request('/management/collections', { method: 'POST', body: JSON.stringify({ ...form, adminMemberId: member.memberId }) }); setForm({ characterName: '', itemName: '', state: '완료', memo: '' }); await load(); };
  return <CrudPage title="컬렉템 지급현황" description="컬렉션/장비 지급과 수정 기록을 조회합니다." canManage={canManage} form={<form className="record-form" onSubmit={add}><input required placeholder="닉네임" value={form.characterName} onChange={(e) => setForm({ ...form, characterName: e.target.value })} /><input required placeholder="아이템/장비명" value={form.itemName} onChange={(e) => setForm({ ...form, itemName: e.target.value })} /><select value={form.state} onChange={(e) => setForm({ ...form, state: e.target.value })}><option>완료</option><option>미완료</option><option>회수</option></select><input placeholder="메모" value={form.memo} onChange={(e) => setForm({ ...form, memo: e.target.value })} /><button className="primary-button">기록 추가</button></form>} rows={rows} getId={(row) => row.collectionRecordId} columns={['닉네임', '아이템/장비', '상태', '메모', '등록일']} render={(row) => [row.characterName, row.itemName, row.state, row.memo || '-', new Date(row.createdAt).toLocaleDateString('ko-KR')]} onDelete={async (id) => { await request(`/management/collections/${id}?adminMemberId=${member.memberId}`, { method: 'DELETE' }); await load(); }} />;
}

function CrudPage({ title, description, canManage, form, rows, getId, columns, render, onDelete }) {
  return <><div className="page-title"><h1>{title}</h1><p>{description}</p></div>{canManage && <section className="white-card">{form}</section>}<section className="white-card"><div className="table-wrap"><table className="data-table"><thead><tr>{columns.map((column) => <th key={column}>{column}</th>)}{canManage && <th>관리</th>}</tr></thead><tbody>{rows.map((row) => <tr key={getId(row)}>{render(row).map((cell, index) => <td key={index}>{cell}</td>)}{canManage && <td><button className="role-button danger" onClick={() => onDelete(getId(row))}>삭제</button></td>}</tr>)}</tbody></table></div>{!rows.length && <div className="empty-state">아직 등록된 기록이 없습니다.</div>}</section></>;
}

function MyPage({ member }) {
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
  return <><div className="page-title"><h1>마이페이지</h1><p>내 계정과 활동 정보를 확인합니다.</p></div><ProfileCard member={member} info={info} /><section className="white-card"><h2>계정 정보</h2><div className="detail-grid"><div><small>캐릭터명</small><b>{member.characterName}</b></div><div><small>권한</small><b>{member.role === 'ADMIN' ? '운영자' : '클랜원'}</b></div><div><small>회원 번호</small><b>{member.memberId}</b></div></div></section><section className="white-card"><h2>비밀번호 변경</h2><form className="password-form" onSubmit={changePassword}><label>현재 비밀번호<input required type="password" value={passwordForm.currentPassword} onChange={(e) => setPasswordForm({ ...passwordForm, currentPassword: e.target.value })} /></label><label>새 비밀번호<input required type="password" minLength="4" value={passwordForm.newPassword} onChange={(e) => setPasswordForm({ ...passwordForm, newPassword: e.target.value })} /></label><label>새 비밀번호 확인<input required type="password" minLength="4" value={passwordForm.confirmPassword} onChange={(e) => setPasswordForm({ ...passwordForm, confirmPassword: e.target.value })} /></label><button className="primary-button">비밀번호 변경</button></form>{passwordMessage && <p className="vault-message">{passwordMessage}</p>}</section></>;
}

function Admin({ member, setPage, onMemberUpdate, memberOnly = false }) {
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
  const load = () => request('/members').then(setMembers).catch((err) => setMessage(err.message));
  useEffect(() => { load(); }, []);

  const memberPayload = (form) => ({
    ...form,
    combatPower: Number(form.combatPower || 0),
    level: Number(form.level || 0),
  });

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
    setEditForm({
      characterName: targetMember.characterName ?? '',
      guildName: targetMember.guildName ?? '',
      characterClass: targetMember.characterClass ?? '',
      level: targetMember.level ?? 0,
      combatPower: targetMember.combatPower ?? 0,
      rank: targetMember.rank ?? '',
      status: targetMember.status ?? '',
      active: targetMember.active ?? true,
    });
  };

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
              <span>{icon}</span><b>{title}</b><small>{title === '출석체크' ? '사진 인식으로 출석 확인' : title === '클랜원 정보수정' || title === '전투력 관리' ? '클랜원 관리 화면' : '바로 이동'}</small>
            </button>
          ))}
        </div>
      </>
    );
  }

  return (
    <>
      <div className="page-title"><h1>클랜원 정보수정</h1><p>클랜원 정보, 비밀번호, 권한, 삭제 여부를 관리합니다.</p></div>
      <button className="outline-button no-margin" onClick={() => setPage('admin')}>← 관리자 설정으로</button>

      <section className="white-card role-card">
        <div className="section-heading">
          <div><h2>클랜원 미리 등록</h2><p className="subtle">운영자가 캐릭터 정보를 먼저 넣어두면, 클랜원은 임시 비밀번호로 로그인한 뒤 마이페이지에서 직접 변경할 수 있습니다.</p></div>
        </div>
        <form className="admin-create-form" onSubmit={createMember}>
          <label>닉네임<input required value={createForm.characterName} onChange={(e) => setCreateForm({ ...createForm, characterName: e.target.value })} /></label>
          <label>임시 비밀번호<input required value={createForm.initialPassword} onChange={(e) => setCreateForm({ ...createForm, initialPassword: e.target.value })} /></label>
          <label>길드<input value={createForm.guildName} onChange={(e) => setCreateForm({ ...createForm, guildName: e.target.value })} /></label>
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
          <span className="result-count">{members.length}명</span>
        </div>
        {message && <p className="vault-message">{message}</p>}
        {editId && (
          <form className="admin-edit-form" onSubmit={saveProfile}>
            <label>닉네임<input required value={editForm.characterName} onChange={(e) => setEditForm({ ...editForm, characterName: e.target.value })} /></label>
            <label>길드<input value={editForm.guildName} onChange={(e) => setEditForm({ ...editForm, guildName: e.target.value })} /></label>
            <label>클래스<input value={editForm.characterClass} onChange={(e) => setEditForm({ ...editForm, characterClass: e.target.value })} /></label>
            <label>레벨<input type="number" min="0" value={editForm.level} onChange={(e) => setEditForm({ ...editForm, level: e.target.value })} /></label>
            <label>전투력<input required type="number" min="0" value={editForm.combatPower} onChange={(e) => setEditForm({ ...editForm, combatPower: e.target.value })} /></label>
            <label>직급<input placeholder="예: 장로, 정예, 일반" value={editForm.rank} onChange={(e) => setEditForm({ ...editForm, rank: e.target.value })} /></label>
            <label>상태<input placeholder="예: 활동중, 휴면, 탈퇴예정" value={editForm.status} onChange={(e) => setEditForm({ ...editForm, status: e.target.value })} /></label>
            <label>활성<select value={editForm.active ? 'true' : 'false'} onChange={(e) => setEditForm({ ...editForm, active: e.target.value === 'true' })}><option value="true">활성</option><option value="false">비활성</option></select></label>
            <button className="primary-button" disabled={loadingId === editId}>저장</button>
            <button type="button" className="role-button" onClick={() => setEditId(null)}>취소</button>
          </form>
        )}
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
            <tbody>{members.map((row) => (
              <tr key={row.memberId}>
                <td>{row.characterName}</td>
                <td>{row.guildName || '-'}</td>
                <td>{row.characterClass || '-'}</td>
                <td>{row.level ? `Lv.${row.level}` : '-'}</td>
                <td>{formatNumber(row.combatPower)}</td>
                <td>{row.rank || '-'}</td>
                <td>{row.active ? (row.status || '활성') : '비활성'}</td>
                <td><span className={row.role === 'ADMIN' ? 'role-pill admin' : 'role-pill member'}>{row.role === 'ADMIN' ? '운영자' : '클랜원'}</span></td>
                <td><button className="role-button" disabled={loadingId === row.memberId} onClick={() => startEdit(row)}>수정</button></td>
                <td><button className="role-button key-button" title="비밀번호 초기화" disabled={loadingId === row.memberId} onClick={() => startPasswordReset(row)}>🔑</button></td>
                <td>{row.role === 'ADMIN'
                  ? <button className="role-button danger" disabled={loadingId === row.memberId || row.memberId === member.memberId} onClick={() => changeRole(row, 'MEMBER')}>{row.memberId === member.memberId ? '본인 해제 불가' : '클랜원으로 변경'}</button>
                  : <button className="role-button" disabled={loadingId === row.memberId} onClick={() => changeRole(row, 'ADMIN')}>운영자로 지정</button>}</td>
                <td><button className="role-button danger" disabled={loadingId === row.memberId || row.memberId === member.memberId} onClick={() => deleteMember(row)}>{row.memberId === member.memberId ? '본인 삭제 불가' : '삭제'}</button></td>
              </tr>
            ))}</tbody>
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
      const worker = await createWorker('kor+eng', 1, { logger: (message) => { if (message.status === 'recognizing text') setProgress(Math.round(message.progress * 100)); } });
      const { data } = await worker.recognize(file);
      await worker.terminate();
      setText(data.text);
      const cleanText = normalize(data.text);
      const matched = members.filter((m) => normalize(m.characterName).length > 1 && cleanText.includes(normalize(m.characterName)));
      const lines = data.text.split(/\r?\n/).map((line) => line.replace(/\bLv\.?\s*\d+.*/i, '').trim()).filter((line) => line.length > 1 && !/^lv\.?\s*\d+/i.test(line));
      const uniqueLines = [...new Set(lines)].slice(0, 30);
      setResult([...matched.map((m) => ({ name: m.characterName, state: 'registered', detail: '등록된 클랜원과 일치' })), ...uniqueLines.filter((line) => !matched.some((m) => normalize(m.characterName) === normalize(line))).map((line) => ({ name: line, state: 'review', detail: 'OCR 인식 결과 · 확인 필요' }))]);
      setStatus(matched.length ? `${matched.length}명의 등록 클랜원을 찾았습니다.` : '자동 일치된 클랜원이 없습니다. 인식 결과를 확인해 주세요.');
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

export default function App() {
  const [member, setMember] = useState(() => JSON.parse(sessionStorage.getItem('clanMember') || 'null'));
  const [page, setPage] = useState('lobby');
  const login = (data) => { sessionStorage.setItem('clanMember', JSON.stringify(data)); setMember(data); };
  const updateCurrentMember = (data) => { sessionStorage.setItem('clanMember', JSON.stringify(data)); setMember(data); };
  const logout = () => { sessionStorage.removeItem('clanMember'); setMember(null); setPage('lobby'); };
  if (!member) return <AuthScreen onLogin={login} />;
  if (member.role !== 'ADMIN' && adminOnlyPages.has(page)) return <Shell member={member} page={page} setPage={setPage} onLogout={logout}><AccessDenied /></Shell>;
  const view = page === 'lobby' ? <Lobby member={member} setPage={setPage} /> : page === 'my-info' ? <MyInfo member={member} /> : page === 'participation' ? <Participation /> : page === 'attendance' ? <Attendance member={member} /> : page === 'payment' ? <PaymentPage member={member} /> : page === 'ledger' ? <ClanVaultPage member={member} /> : page === 'book' ? <ClanVaultPage member={member} readonly /> : page === 'inventory' ? <InventoryPage member={member} /> : page === 'bidding' ? <BiddingPage member={member} /> : page === 'collection' ? <CollectionPage member={member} /> : page === 'roster' ? <RosterScan /> : page === 'mypage' ? <MyPage member={member} /> : page === 'admin' ? <Admin member={member} setPage={setPage} onMemberUpdate={updateCurrentMember} /> : page === 'member-admin' ? <Admin member={member} setPage={setPage} onMemberUpdate={updateCurrentMember} memberOnly /> : <Lobby member={member} setPage={setPage} />;
  return <Shell member={member} page={page} setPage={setPage} onLogout={logout}>{view}</Shell>;
}
