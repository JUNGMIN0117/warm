import { afterEach, describe, expect, it } from "vitest";
import { clearAuth, loadAuth, saveAuth } from "./token-storage";
import type { StoredAuth } from "./token-storage";

/**
 * 토큰 보관 테스트.
 *
 * 핵심 계약: 만료된 토큰과 깨진 데이터는 "없는 것"으로 취급하고 스스로
 * 정리한다. 게이트웨이가 잘못된 토큰을 익명으로 취급하듯, 클라이언트도
 * 유효하지 않은 저장분으로 인증 상태를 주장하지 않는다.
 */

function validAuth(overrides: Partial<StoredAuth> = {}): StoredAuth {
  return {
    accessToken: "token",
    expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    userId: "user-1",
    displayName: "테스트",
    role: "USER",
    ...overrides,
  };
}

afterEach(() => {
  window.localStorage.clear();
});

describe("token-storage", () => {
  it("저장한 것을 그대로 돌려준다", () => {
    const auth = validAuth();
    saveAuth(auth);
    expect(loadAuth()).toEqual(auth);
  });

  it("만료된 토큰은 null을 주고 저장소에서 지운다", () => {
    saveAuth(validAuth({ expiresAt: new Date(Date.now() - 1000).toISOString() }));

    expect(loadAuth()).toBeNull();
    expect(window.localStorage.getItem("pcai.auth")).toBeNull();
  });

  it("깨진 JSON은 null을 주고 정리한다", () => {
    window.localStorage.setItem("pcai.auth", "{not json");

    expect(loadAuth()).toBeNull();
    expect(window.localStorage.getItem("pcai.auth")).toBeNull();
  });

  it("필드가 빠진 객체는 인증으로 치지 않는다", () => {
    window.localStorage.setItem("pcai.auth", JSON.stringify({ accessToken: "x" }));

    expect(loadAuth()).toBeNull();
  });

  it("clearAuth 후에는 아무것도 없다", () => {
    saveAuth(validAuth());
    clearAuth();
    expect(loadAuth()).toBeNull();
  });
});
