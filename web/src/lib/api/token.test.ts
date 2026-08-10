import { afterEach, describe, expect, it } from "vitest";

import { clearSession, getSession, isExpired, saveSession } from "./token";

/**
 * 세션 보관 규칙의 회귀 테스트.
 * 만료 판단은 서버가 준 expiresAt만 쓴다 — JWT 디코드 금지가 계약이다.
 */

afterEach(() => {
  clearSession();
});

describe("isExpired", () => {
  it("미래 시각은 유효, 과거 시각은 만료", () => {
    const now = new Date("2026-08-10T12:00:00Z");
    expect(isExpired("2026-08-10T13:00:00Z", now)).toBe(false);
    expect(isExpired("2026-08-10T11:00:00Z", now)).toBe(true);
  });

  it("정확히 만료 시각이면 만료로 취급한다", () => {
    const now = new Date("2026-08-10T12:00:00Z");
    expect(isExpired("2026-08-10T12:00:00Z", now)).toBe(true);
  });

  it("파싱 불가능한 값은 만료로 취급한다 — 의심스러운 세션으로 요청하지 않는다", () => {
    expect(isExpired("not-a-date")).toBe(true);
  });
});

describe("getSession", () => {
  it("만료된 세션은 돌려주지 않고 저장소에서도 지운다", () => {
    saveSession({
      accessToken: "token",
      expiresAt: "2000-01-01T00:00:00Z", // 과거
      displayName: "정민",
    });
    expect(getSession()).toBeNull();
    expect(window.localStorage.getItem("pcai.session")).toBeNull();
  });

  it("유효한 세션은 그대로 돌려준다", () => {
    saveSession({
      accessToken: "token",
      expiresAt: "2999-01-01T00:00:00Z",
      displayName: "정민",
    });
    expect(getSession()?.displayName).toBe("정민");
  });

  it("깨진 JSON이 저장돼 있으면 지우고 null을 준다", () => {
    window.localStorage.setItem("pcai.session", "{broken");
    expect(getSession()).toBeNull();
    expect(window.localStorage.getItem("pcai.session")).toBeNull();
  });
});
