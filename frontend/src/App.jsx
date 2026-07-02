import { useEffect, useState } from 'react';
import { createWorker } from 'tesseract.js';
import './roster.css';
import './vault.css';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';

const menu = [
  ['lobby', '로', '로비'], ['my-info', '내', '내 정보 확인'], ['participation', '참', '참여율 조회'],
  ['attendance', '보', '보스 참여내역 조회'], ['payment', '분', '분배금 조회'], ['ledger', '통', '통장현황'],
  ['book', '장', '장부 조회'], ['inventory', '재', '재고현황'], ['bidding', '아', '아이템입찰'],
  ['collection', '컬', '컬렉템 지급현황'], ['roster', '인', '클랜원 관리'], ['mypage', '마', '마이페이지'], ['admin', '관', '관리자 설정'],
];
const adminOnlyPages = new Set(['ledger', 'book', 'inventory', 'bidding', 'collection', 'roster', 'admin']);
const adminCards = [['✓', '출석체크', 'mint'], ['♕', '출석보스 설정', 'rose'], ['♙', '클랜원 관리', 'blue'], ['◴', '출석기록 관리', 'purple'], ['⚙', '가중치 설정', 'orange'], ['ϟ', '전투력 관리', 'gold'], ['✿', '기타 설정', 'indigo'], ['▣', '스펙/장비 수정기록', 'amber'], ['▥', '참여율 선택조회', 'cyan'], ['⚠', '계현나감지', 'red']];

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, { headers: { 'Content-Type': 'application/json', ...options.headers }, ...options });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.message ?? '요청 처리 중 오류가 발생했습니다.');
  return body;
}

function AuthScreen({ onLogin }) {
  const [isRegister, setIsRegister] = useState(false);
  const [form, setForm] = useState({ characterName: '', password: '', combatPower: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const submit = async (event) => {
    event.preventDefault(); setLoading(true); setError('');
    try {
      if (isRegister) await request('/auth/register', { method: 'POST', body: JSON.stringify({ ...form, combatPower: Number(form.combatPower || 0) }) });
      onLogin(await request('/auth/login', { method: 'POST', body: JSON.stringify({ characterName: form.characterName, password: form.password }) }));
    } catch (err) { setError(err.message); } finally { setLoading(false); }
  };
  return <main className="auth-page"><section className="auth-card light-auth"><div className="auth-mark">C</div><p className="auth-kicker">CLAN MANAGER</p><h1>{isRegister ? '클랜에 합류하기' : '클랜 매니저'}</h1><p>캐릭터 정보와 클랜 활동을 한곳에서 관리하세요.</p><form onSubmit={submit}><label>캐릭터 이름<input required value={form.characterName} onChange={(e) => setForm({ ...form, characterName: e.target.value })} /></label><label>비밀번호<input required type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></label>{isRegister && <label>전투력<input required type="number" value={form.combatPower} onChange={(e) => setForm({ ...form, combatPower: e.target.value })} /></label>}{error && <p className="form-error">{error}</p>}<button className="primary-button" disabled={loading}>{loading ? '처리 중...' : isRegister ? '회원가입' : '로그인'}</button></form><button className="link-button" onClick={() => setIsRegister(!isRegister)}>{isRegister ? '이미 계정이 있어요' : '처음 오셨나요? 회원가입'}</button></section></main>;
}

function Shell({ member, page, setPage, onLogout, children }) {
  const [collapsed, setCollapsed] = useState(false);
  const visibleMenu = menu.filter(([id]) => member.role === 'ADMIN' || !adminOnlyPages.has(id));
  return <div className={`shell ${collapsed ? 'collapsed' : ''}`}><header className="topbar"><button className="hamburger" onClick={() => setCollapsed(!collapsed)}>☰</button><div className="brand-mark">C</div><div className="topbar-spacer" /><button className="circle-button">☾</button><button className="profile-menu"><b>{member.characterName.slice(0, 1)}</b><span>{member.characterName}</span><small>{member.role === 'ADMIN' ? '운영자' : '클랜원'}</small><i>⌄</i></button><button className="logout-icon" title="로그아웃" onClick={onLogout}>⇥</button></header><aside className="sidebar"><nav>{visibleMenu.map(([id, icon, label]) => <button key={id} className={page === id ? 'menu-item active' : 'menu-item'} onClick={() => setPage(id)}><span className="menu-icon">{icon}</span><span>{label}</span></button>)}</nav><div className="side-note"><b>클랜 매니저</b><small>{member.role === 'ADMIN' ? '운영자 모드입니다' : '클랜 활동을 확인하세요'}</small></div></aside><main className="content">{children}</main></div>;
}

function NoticePanel({ notices, canManage }) { return <section className="white-card notices"><div className="section-heading"><h2>공지</h2>{canManage && <button className="small-primary">+ 추가</button>}</div>{notices.length ? notices.map((n) => <article key={n.noticeId} className="notice-row"><b>{n.title}</b><p>{n.content}</p><small>{new Date(n.createdAt).toLocaleDateString('ko-KR')}</small></article>) : <div className="empty-state">아직 등록된 공지사항이 없습니다.</div>}</section>; }

function Lobby({ member }) {
  const [notices, setNotices] = useState([]); const [members, setMembers] = useState([]);
  useEffect(() => { Promise.all([request('/notices'), request('/members')]).then(([a, b]) => { setNotices(a); setMembers(b); }).catch(() => {}); }, []);
  return <><NoticePanel notices={notices} canManage={member.role === 'ADMIN'} /><div className="page-title center"><h1>클랜 종합정보</h1><p>클랜원들의 참여율과 전투력을 기반으로 한 순위 정보</p></div><div className="dashboard-grid"><Ranking title="🏅 참여율 순위" rows={members} field="참여율" /><Ranking title="⚔ 전투력 순위" rows={[...members].sort((a,b) => b.combatPower-a.combatPower)} field="전투력" power /></div><section className="white-card quick-actions"><h2>빠른 메뉴</h2><div><button onClick={() => {}}>오늘의 출석 확인</button><button>클랜 활동 일정</button>{member.role === 'ADMIN' && <button>공지사항 관리</button>}</div></section></>;
}
function Ranking({ title, rows, field, power }) { return <section className="white-card ranking"><h2>{title}</h2><table><thead><tr><th>순위</th><th>닉네임</th><th>{field}</th></tr></thead><tbody>{rows.slice(0, 10).map((m, i) => <tr key={m.memberId}><td>{i < 3 ? ['🥇', '🥈', '🥉'][i] : i + 1}</td><td>{m.characterName}</td><td>{power ? Number(m.combatPower || 0).toLocaleString() : '-'}</td></tr>)}</tbody></table>{!rows.length && <div className="empty-state">등록된 클랜원이 없습니다.</div>}</section>; }

function MyInfo({ member }) {
  const [info, setInfo] = useState(null); useEffect(() => { request(`/members/${member.memberId}/my-info`).then(setInfo).catch(() => {}); }, [member.memberId]);
  if (!info) return <LoadingCard />;
  return <><div className="page-title"><h1>내 정보 확인</h1></div><section className="white-card profile-overview"><div className="profile-name"><span className="avatar">{info.characterName.slice(0,1)}</span><div><h2>{info.characterName}</h2><div className="pills"><span>클랜원</span><span>전투력 {Number(info.combatPower).toLocaleString()}</span></div></div><button className="outline-button">정보수정</button></div><div className="metric-grid"><Metric label="현재 참여율" value={`${info.participationRate}%`} caption={`참여 ${info.myAttendanceCount}회 / 1등 ${info.topAttendanceCount}회`} tone="blue" /><Metric label="기여율" value={`${info.participationRate}%`} caption="최고 참여 횟수를 100%로 계산" tone="green" /><Metric label="참석 횟수" value={`${info.myAttendanceCount}회`} caption="전체 활동 기준" tone="purple" /></div><div className="formula-row"><div><b>참여율 계산 기준</b><p>내 참석 횟수 / 가장 많이 참석한 캐릭터의 참석 횟수 × 100</p></div><div><b>현재 100% 기준</b><p>{info.topAttendanceCount}회</p></div></div></section></>;
}
function Metric({ label, value, caption, tone }) { return <div className={`metric ${tone}`}><p>{label}</p><strong>{value}</strong><small>{caption}</small></div>; }

function Participation() { const [members, setMembers] = useState([]); useEffect(() => { request('/members').then(setMembers).catch(() => {}); }, []); return <><div className="page-title"><h1>참여율·기여율 조회</h1><p>클랜원의 최근 활동 참여 현황입니다.</p></div><section className="white-card"><div className="info-banner"><b>참여율 계산 기준</b><span>가장 많이 참석한 캐릭터의 횟수를 100%로 계산합니다.</span></div><div className="table-wrap"><table className="data-table"><thead><tr><th>순위</th><th>닉네임</th><th>전투력</th><th>참여율</th><th>기여율</th></tr></thead><tbody>{members.map((m, i) => <tr key={m.memberId}><td>{i + 1}</td><td>{m.characterName}</td><td>{Number(m.combatPower || 0).toLocaleString()}</td><td className="blue-text">-</td><td className="green-text">-</td></tr>)}</tbody></table></div>{!members.length && <div className="empty-state">클랜원이 등록되면 이곳에 순위가 표시됩니다.</div>}</section></>; }
function Attendance() { const [rows, setRows] = useState([]); useEffect(() => { request('/attendances').then(setRows).catch(() => {}); }, []); return <><div className="page-title"><h1>보스 참여내역 조회</h1></div><section className="white-card"><div className="filters"><select><option>전체 날짜</option></select><input placeholder="보스명" /><button className="dark-button">조회</button><button className="orange-button">주간 정산</button></div><div className="table-wrap"><table className="data-table"><thead><tr><th>날짜</th><th>활동</th><th>출석회원</th><th>상태</th></tr></thead><tbody>{rows.map((row) => <tr key={row.attendanceId}><td>{row.attendanceDate}</td><td>{row.activityType?.typeName ?? '-'}</td><td>{row.member?.characterName ?? '-'}</td><td><span className="status-pill">{row.status}</span></td></tr>)}</tbody></table></div>{!rows.length && <div className="empty-state">등록된 출석 기록이 없습니다.</div>}</section></>; }
function ClanVaultPage({ member }) {
  const [summary, setSummary] = useState(null);
  const [members, setMembers] = useState([]);
  const [mode, setMode] = useState('deposit');
  const [form, setForm] = useState({ amountDiamonds: '', balanceDiamonds: '', targetMemberId: '', memo: '' });
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const money = (value) => `${Number(value || 0).toLocaleString()} 다이아`;
  const typeText = { DEPOSIT: '입금', DISTRIBUTION: '분배', WITHDRAW: '차감', ADJUSTMENT: '잔액수정' };
  const typeTone = { DEPOSIT: 'green', DISTRIBUTION: 'blue', WITHDRAW: 'red', ADJUSTMENT: 'purple' };
  const load = async () => {
    const [vault, memberRows] = await Promise.all([request('/vault'), request('/members')]);
    setSummary(vault); setMembers(memberRows);
  };
  useEffect(() => { load().catch((err) => setMessage(err.message)); }, []);
  const submit = async (event) => {
    event.preventDefault(); setLoading(true); setMessage('');
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
      setMessage('금고 장부에 저장했습니다.');
    } catch (err) { setMessage(err.message); } finally { setLoading(false); }
  };
  const transactions = summary?.recentTransactions ?? [];
  return <><div className="page-title"><h1>클랜금고</h1><p>클랜 다이아 잔액과 인원별 분배 기록을 은행 장부처럼 관리합니다.</p></div><div className="vault-grid"><section className="white-card vault-balance"><span className="vault-icon">💎</span><p>현재 클랜금고 잔액</p><strong>{money(summary?.balanceDiamonds)}</strong><small>입금 {summary?.depositCount ?? 0}건 · 분배 {summary?.distributionCount ?? 0}건</small></section><section className="white-card vault-form-card"><div className="section-heading"><h2>금고 기록 추가</h2><span className="status-pill">{typeText[mode === 'adjust' ? 'ADJUSTMENT' : mode === 'deposit' ? 'DEPOSIT' : mode === 'distribute' ? 'DISTRIBUTION' : 'WITHDRAW']}</span></div><div className="vault-tabs"><button className={mode === 'deposit' ? 'active' : ''} onClick={() => setMode('deposit')}>입금</button><button className={mode === 'distribute' ? 'active' : ''} onClick={() => setMode('distribute')}>분배</button><button className={mode === 'withdraw' ? 'active' : ''} onClick={() => setMode('withdraw')}>차감</button><button className={mode === 'adjust' ? 'active' : ''} onClick={() => setMode('adjust')}>잔액수정</button></div><form className="vault-form" onSubmit={submit}>{mode === 'distribute' && <label>받는 클랜원<select required value={form.targetMemberId} onChange={(e) => setForm({ ...form, targetMemberId: e.target.value })}><option value="">클랜원 선택</option>{members.map((m) => <option value={m.memberId} key={m.memberId}>{m.characterName}</option>)}</select></label>}{mode === 'adjust' ? <label>새 금고 잔액<input required min="0" type="number" value={form.balanceDiamonds} onChange={(e) => setForm({ ...form, balanceDiamonds: e.target.value })} placeholder="현재 총 다이아 갯수" /></label> : <label>다이아 수량<input required min="1" type="number" value={form.amountDiamonds} onChange={(e) => setForm({ ...form, amountDiamonds: e.target.value })} placeholder="기록할 다이아 수량" /></label>}<label>메모<input value={form.memo} onChange={(e) => setForm({ ...form, memo: e.target.value })} placeholder="예: 에노크 분배, 보스 수익 입금" /></label><button className="primary-button" disabled={loading}>{loading ? '저장 중...' : '금고 기록 저장'}</button>{message && <p className="vault-message">{message}</p>}</form></section></div><section className="white-card"><div className="section-heading"><h2>최근 거래내역</h2><span className="result-count">{transactions.length}건</span></div><div className="table-wrap"><table className="data-table vault-table"><thead><tr><th>시간</th><th>종류</th><th>대상</th><th>수량</th><th>처리 후 잔액</th><th>메모</th><th>기록자</th></tr></thead><tbody>{transactions.map((row) => <tr key={row.transactionId}><td>{new Date(row.createdAt).toLocaleString('ko-KR')}</td><td><span className={`vault-type ${typeTone[row.type]}`}>{typeText[row.type] ?? row.type}</span></td><td>{row.targetMemberName ?? '-'}</td><td>{money(row.amountDiamonds)}</td><td><b>{money(row.balanceAfter)}</b></td><td>{row.memo || '-'}</td><td>{row.createdByMemberName ?? '-'}</td></tr>)}</tbody></table></div>{!transactions.length && <div className="empty-state">아직 금고 거래내역이 없습니다. 첫 잔액을 입력해 보세요.</div>}</section></>;
}
function Admin({ member }) {
  const [members, setMembers] = useState([]);
  const [message, setMessage] = useState('');
  const [loadingId, setLoadingId] = useState(null);
  const load = () => request('/members').then(setMembers).catch((err) => setMessage(err.message));
  useEffect(() => { load(); }, []);
  const changeRole = async (targetMember, role) => {
    setLoadingId(targetMember.memberId); setMessage('');
    try {
      await request(`/members/${targetMember.memberId}/role?role=${role}&adminMemberId=${member.memberId}`, { method: 'PATCH' });
      await load();
      setMessage(`${targetMember.characterName}님의 권한을 ${role === 'ADMIN' ? '운영자' : '일반 클랜원'}로 변경했습니다.`);
    } catch (err) { setMessage(err.message); } finally { setLoadingId(null); }
  };
  return <><div className="page-title"><h1>관리자 설정</h1><p>클랜 운영에 필요한 설정 메뉴입니다.</p></div><div className="admin-grid">{adminCards.map(([icon, title, color]) => <button className={`admin-card ${color}`} key={title}><span>{icon}</span><b>{title}</b><small>{title === '클랜원 관리' ? '운영자 권한 관리 가능' : '화면 준비 중'}</small></button>)}</div><section className="white-card role-card"><div className="section-heading"><div><h2>운영자 권한 관리</h2><p className="subtle">특정 클랜원을 운영자로 지정하거나 일반 클랜원으로 되돌릴 수 있습니다.</p></div><span className="result-count">운영자 {members.filter((row) => row.role === 'ADMIN').length}명</span></div>{message && <p className="vault-message">{message}</p>}<div className="table-wrap"><table className="data-table role-table"><thead><tr><th>닉네임</th><th>전투력</th><th>현재 권한</th><th>상태</th><th>변경</th></tr></thead><tbody>{members.map((row) => <tr key={row.memberId}><td>{row.characterName}</td><td>{Number(row.combatPower || 0).toLocaleString()}</td><td><span className={row.role === 'ADMIN' ? 'role-pill admin' : 'role-pill member'}>{row.role === 'ADMIN' ? '운영자' : '클랜원'}</span></td><td>{row.active ? '활성' : '비활성'}</td><td>{row.role === 'ADMIN' ? <button className="role-button danger" disabled={loadingId === row.memberId || row.memberId === member.memberId} onClick={() => changeRole(row, 'MEMBER')}>{row.memberId === member.memberId ? '본인 해제 불가' : '클랜원으로 변경'}</button> : <button className="role-button" disabled={loadingId === row.memberId} onClick={() => changeRole(row, 'ADMIN')}>운영자로 지정</button>}</td></tr>)}</tbody></table></div>{!members.length && <div className="empty-state">등록된 클랜원이 없습니다.</div>}</section></>;
}
function RosterScan() {
  const [members, setMembers] = useState([]), [file, setFile] = useState(null), [preview, setPreview] = useState(''), [status, setStatus] = useState(''), [progress, setProgress] = useState(0), [text, setText] = useState(''), [result, setResult] = useState([]);
  useEffect(() => { request('/members').then(setMembers).catch(() => {}); }, []);
  const normalize = (value) => value.toLowerCase().replace(/[^0-9a-z가-힣]/g, '');
  const selectFile = (event) => { const next = event.target.files?.[0]; if (!next) return; setFile(next); setPreview(URL.createObjectURL(next)); setText(''); setResult([]); setStatus(''); };
  const scan = async () => {
    if (!file) return; setStatus('이미지에서 이름을 읽는 중입니다...'); setProgress(0);
    try {
      const worker = await createWorker('kor+eng', 1, { logger: (message) => { if (message.status === 'recognizing text') setProgress(Math.round(message.progress * 100)); } });
      const { data } = await worker.recognize(file); await worker.terminate(); setText(data.text);
      const cleanText = normalize(data.text); const matched = members.filter((member) => normalize(member.characterName).length > 1 && cleanText.includes(normalize(member.characterName)));
      const lines = data.text.split(/\r?\n/).map((line) => line.replace(/\bLv\.?\s*\d+.*/i, '').trim()).filter((line) => line.length > 1 && !/^lv\.?\s*\d+/i.test(line));
      const uniqueLines = [...new Set(lines)].slice(0, 30);
      setResult([...matched.map((member) => ({ name: member.characterName, state: 'registered', detail: '등록된 클랜원과 일치' })), ...uniqueLines.filter((line) => !matched.some((member) => normalize(member.characterName) === normalize(line))).map((line) => ({ name: line, state: 'review', detail: 'OCR 인식 결과 · 확인 필요' }))]);
      setStatus(matched.length ? `${matched.length}명의 등록 클랜원을 찾았습니다.` : '자동 일치한 클랜원이 없습니다. 인식 결과를 확인해 주세요.');
    } catch { setStatus('이미지를 읽지 못했습니다. 이름 부분이 선명하게 보이는 사진으로 다시 시도해 주세요.'); } finally { setProgress(100); }
  };
  return <><div className="page-title"><h1>클랜원 관리</h1><p>현재 파티 구성 스크린샷에서 캐릭터 이름을 읽어 클랜원 목록과 비교합니다.</p></div><section className="white-card roster-card"><div className="roster-head"><div><h2>파티 구성 불러오기</h2><p>스크린샷은 브라우저 안에서만 분석되며, 자동으로 클랜원을 추가하지 않습니다.</p></div><span className="local-badge">내 기기 OCR</span></div><div className="roster-layout"><label className="upload-zone"><input type="file" accept="image/png,image/jpeg,image/webp" onChange={selectFile} />{preview ? <img src={preview} alt="업로드한 파티 구성" /> : <><b>＋</b><strong>파티 스크린샷 업로드</strong><span>PNG, JPG, WEBP</span></>}</label><div className="scan-guide"><h3>인식 안내</h3><ol><li>파티원의 캐릭터 이름이 선명하게 보이도록 올려주세요.</li><li>등록된 이름은 자동으로 일치 처리합니다.</li><li>새 이름이나 오인식 후보는 관리자가 검토합니다.</li></ol><button className="primary-button" disabled={!file || status.includes('읽는 중')} onClick={scan}>{status.includes('읽는 중') ? `글자 인식 중 ${progress}%` : '이름 인식 시작'}</button></div></div>{status && <div className="scan-status">{status}</div>}</section>{(result.length > 0 || text) && <section className="white-card"><div className="section-heading"><div><h2>인식 결과 검토</h2><p className="subtle">자동으로 출석이나 회원 등록을 처리하지 않습니다.</p></div><span className="result-count">{result.length}개 후보</span></div><div className="scan-results">{result.map((item, index) => <div className="scan-result" key={`${item.name}-${index}`}><span className={item.state === 'registered' ? 'match-icon yes' : 'match-icon wait'}>{item.state === 'registered' ? '✓' : '?'}</span><div><b>{item.name}</b><small>{item.detail}</small></div><span className={item.state === 'registered' ? 'match-state registered' : 'match-state review'}>{item.state === 'registered' ? '등록됨' : '확인 필요'}</span></div>)}</div><details className="raw-ocr"><summary>OCR 원문 보기</summary><pre>{text}</pre></details></section>}</>;
}
function Placeholder({ page }) { const label = menu.find(([id]) => id === page)?.[2] ?? '준비 중'; return <section className="placeholder-page"><div className="white-card"><span>✦</span><h1>{label}</h1><p>이 메뉴의 화면을 먼저 준비해 두었습니다.<br />세부 기능은 필요한 시점에 이어서 연결할 수 있어요.</p><button className="primary-button">기능 준비 중</button></div></section>; }
function AccessDenied() { return <section className="placeholder-page"><div className="white-card"><span>🔒</span><h1>운영자 전용 화면</h1><p>이 메뉴는 운영자만 사용할 수 있어요.<br />일반 클랜원은 내 정보와 조회 메뉴를 이용할 수 있습니다.</p></div></section>; }
function LoadingCard() { return <section className="white-card loading-card">정보를 불러오는 중입니다...</section>; }

export default function App() {
  const [member, setMember] = useState(() => JSON.parse(sessionStorage.getItem('clanMember') || 'null'));
  const [page, setPage] = useState('lobby');
  const login = (data) => { sessionStorage.setItem('clanMember', JSON.stringify(data)); setMember(data); };
  const logout = () => { sessionStorage.removeItem('clanMember'); setMember(null); };
  if (!member) return <AuthScreen onLogin={login} />;
  if (member.role !== 'ADMIN' && adminOnlyPages.has(page)) return <Shell member={member} page={page} setPage={setPage} onLogout={logout}><AccessDenied /></Shell>;
  let view = page === 'lobby' ? <Lobby member={member} /> : page === 'my-info' ? <MyInfo member={member} /> : page === 'participation' ? <Participation /> : page === 'attendance' ? <Attendance /> : page === 'ledger' ? <ClanVaultPage member={member} /> : page === 'roster' ? <RosterScan /> : page === 'admin' ? <Admin member={member} /> : <Placeholder page={page} />;
  return <Shell member={member} page={page} setPage={setPage} onLogout={logout}>{view}</Shell>;
}
