/**
 * JWT 보관.
 *
 * localStorage를 쓴다. httpOnly 쿠키 + BFF가 XSS에는 더 안전하지만,
 * 이 API의 토큰으로 할 수 있는 일은 "내 분석 이력 읽기"가 전부이고
 * (결제·개인정보·쓰기 권한 없음), 게이트웨이는 잘못된 토큰을 401이 아니라
 * 익명으로 취급하므로 토큰 오염이 서비스를 막지도 못한다. 위험 대비
 * BFF 세션 계층의 복잡도가 크다고 판단했다 (docs/06-frontend.md).
 *
 * expiresAt은 서버가 준 값을 그대로 저장한다 — JWT를 디코드하지 않는 것이
 * 계약이다 (05-api-spec.md §10).
 */

const STORAGE_KEY = "pcai.auth";

export interface StoredAuth {
  accessToken: string;
  expiresAt: string;
  userId: string;
  displayName: string;
  /** UI 노출 판단용. 실제 인가는 항상 서버(hasRole)가 한다 — 이 값을 조작해도 API는 403. */
  role: "USER" | "ADMIN";
}

function isStoredAuth(value: unknown): value is StoredAuth {
  if (typeof value !== "object" || value === null) return false;
  const v = value as Record<string, unknown>;
  return (
    typeof v.accessToken === "string" &&
    typeof v.expiresAt === "string" &&
    typeof v.userId === "string" &&
    typeof v.displayName === "string" &&
    (v.role === "USER" || v.role === "ADMIN")
  );
}

/** 저장된 인증 정보. 만료됐거나 형식이 깨졌으면 null을 주고 정리한다. */
export function loadAuth(): StoredAuth | null {
  if (typeof window === "undefined") return null;
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (raw === null) return null;
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!isStoredAuth(parsed)) {
      clearAuth();
      return null;
    }
    if (new Date(parsed.expiresAt).getTime() <= Date.now()) {
      clearAuth();
      return null;
    }
    return parsed;
  } catch {
    clearAuth();
    return null;
  }
}

export function saveAuth(auth: StoredAuth): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(auth));
}

export function clearAuth(): void {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(STORAGE_KEY);
}
