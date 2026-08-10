import { describe, expect, it } from "vitest";

import { ApiError, NetworkError } from "./api/client";
import { toGuidance } from "./errors";

/**
 * 오류 → 안내 매핑의 계약 테스트.
 *
 * 핵심 규칙 둘:
 * 1. 422 계열의 서버 message는 가공 없이 그대로 노출된다 —
 *    실패 원인을 가장 잘 아는 측정기의 문구를 프론트가 덮어쓰지 않는다.
 * 2. fixableByUser는 "사진을 바꾸면 해결되는가"로만 정해진다 —
 *    서킷 브레이커가 실패를 세는 기준과 같은 축이다.
 */

function apiError(status: number, code: string, message: string): ApiError {
  return new ApiError(status, { code: code as never, message, detail: null });
}

describe("toGuidance", () => {
  it("NO_FACE_DETECTED — ML 서비스의 message를 그대로 보여주고 재선택을 유도한다", () => {
    const serverMessage = "사진에서 얼굴을 찾지 못했습니다. 정면을 향한 사진을 사용해 주세요.";
    const guidance = toGuidance(apiError(422, "NO_FACE_DETECTED", serverMessage));
    expect(guidance.body).toBe(serverMessage); // 가공 금지
    expect(guidance.fixableByUser).toBe(true);
  });

  it("MULTIPLE_FACES · INSUFFICIENT_SKIN_PIXELS · FILE_TOO_LARGE도 사용자 해결 가능으로 분류한다", () => {
    for (const code of ["MULTIPLE_FACES", "INSUFFICIENT_SKIN_PIXELS", "FILE_TOO_LARGE"]) {
      expect(toGuidance(apiError(422, code, "서버 안내문")).fixableByUser).toBe(true);
    }
  });

  it("ANALYZER_UNAVAILABLE — 장애는 사진 문제가 아니므로 재선택을 유도하지 않는다", () => {
    const guidance = toGuidance(apiError(503, "ANALYZER_UNAVAILABLE", "잠시 후 시도"));
    expect(guidance.fixableByUser).toBe(false);
    expect(guidance.title).toContain("분석 엔진");
  });

  it("네트워크 단절은 서버 오류와 다른 안내를 낸다", () => {
    const guidance = toGuidance(new NetworkError(new TypeError("fetch failed")));
    expect(guidance.title).toContain("연결");
    expect(guidance.fixableByUser).toBe(false);
  });

  it("모르는 오류에도 안내는 항상 나온다 (빈 화면 금지)", () => {
    const guidance = toGuidance(new Error("???"));
    expect(guidance.title.length).toBeGreaterThan(0);
    expect(guidance.body.length).toBeGreaterThan(0);
  });
});
