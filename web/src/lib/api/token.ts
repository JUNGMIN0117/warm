/**
 * 인증 세션 보관.
 *
 * localStorage에 토큰을 두는 것은 의도된 트레이드오프다 — httpOnly 쿠키가
 * XSS에는 더 안전하지만 게이트웨이의 CORS·CSRF 설계 변경이 따라오고,
 * 이 서비스의 토큰이 여는 것은 "내 분석 이력 조회"뿐이다.
 * 근거와 재검토 조건은 docs/06-frontend.md §4에 있다.
 *
 * 만료 판단은 서버가 준 expiresAt으로만 한다 — JWT payload를 디코드하기
 * 시작하면 토큰 구조가 사실상 공개 계약이 된다 (05-api-spec §10).
 */

const STORAGE_KEY = "pcai.session";

export interface Session {
  accessToken: string;
  /** ISO-8601. 서버가 준 값 그대로 보관한다. */
  expiresAt: string;
  displayName: string;
}

/** 유효한(만료 전) 세션을 돌려준다. 만료됐으면 지우고 null. */
export function getSession(): Session | null {
  if (typeof window === "undefined") return null;
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;

  let session: Session;
  try {
    session = JSON.parse(raw) as Session;
  } catch {
    window.localStorage.removeItem(STORAGE_KEY);
    return null;
  }

  if (isExpired(session.expiresAt)) {
    window.localStorage.removeItem(STORAGE_KEY);
    return null;
  }
  return session;
}

export function saveSession(session: Session): void {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  notify();
}

export function clearSession(): void {
  window.localStorage.removeItem(STORAGE_KEY);
  notify();
}

export function isExpired(expiresAt: string, now: Date = new Date()): boolean {
  const expiry = new Date(expiresAt);
  // 파싱 불가능한 값은 만료로 취급한다 — 의심스러운 세션으로 요청을 보내
  // "로그인했는데 익명 취급"이라는 더 혼란스러운 상태를 만들지 않기 위해.
  if (Number.isNaN(expiry.getTime())) return true;
  return expiry.getTime() <= now.getTime();
}

/**
 * 세션 변경 구독 — Header 같은 컴포넌트가 로그인/로그아웃을 즉시 반영한다.
 * 다른 탭의 변경은 storage 이벤트로, 같은 탭은 커스텀 이벤트로 전달된다.
 */
const SESSION_EVENT = "pcai:session-changed";

function notify(): void {
  window.dispatchEvent(new Event(SESSION_EVENT));
}

export function subscribeSession(listener: () => void): () => void {
  window.addEventListener(SESSION_EVENT, listener);
  window.addEventListener("storage", listener);
  return () => {
    window.removeEventListener(SESSION_EVENT, listener);
    window.removeEventListener("storage", listener);
  };
}
