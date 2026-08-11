import { describe, expect, it } from "vitest";
import { ApiError, toApiError } from "./errors";

/**
 * 오류 계약 테스트.
 *
 * 게이트웨이의 {code, message, detail} 형식이 ApiError로 정확히 옮겨지는지,
 * 그리고 계약 밖의 응답(프록시 오류 HTML 등)에도 죽지 않는지를 고정한다.
 */
describe("toApiError", () => {
  it("게이트웨이 오류 형식을 그대로 옮긴다", async () => {
    const response = new Response(
      JSON.stringify({
        code: "NO_FACE_DETECTED",
        message: "얼굴을 찾지 못했습니다.",
        detail: null,
      }),
      { status: 422 },
    );

    const error = await toApiError(response);

    expect(error.code).toBe("NO_FACE_DETECTED");
    expect(error.message).toBe("얼굴을 찾지 못했습니다.");
    expect(error.status).toBe(422);
  });

  it("detail을 구조 그대로 보존한다", async () => {
    const response = new Response(
      JSON.stringify({
        code: "FILE_TOO_LARGE",
        message: "파일이 너무 큽니다.",
        detail: { max_bytes: 12582912 },
      }),
      { status: 413 },
    );

    const error = await toApiError(response);

    expect(error.detail).toEqual({ max_bytes: 12582912 });
  });

  it("JSON이 아닌 body(프록시 오류 등)에도 죽지 않고 폴백한다", async () => {
    const response = new Response("<html>502 Bad Gateway</html>", { status: 502 });

    const error = await toApiError(response);

    expect(error.code).toBe("INTERNAL_ERROR");
    expect(error.status).toBe(502);
    expect(error.message.length).toBeGreaterThan(0);
  });

  it("code가 없는 JSON에도 폴백 코드를 준다", async () => {
    const response = new Response(JSON.stringify({ unexpected: true }), { status: 500 });

    const error = await toApiError(response);

    expect(error.code).toBe("INTERNAL_ERROR");
  });
});

describe("ApiError.isRetryableWithDifferentPhoto", () => {
  it.each(["NO_FACE_DETECTED", "MULTIPLE_FACES", "INSUFFICIENT_SKIN_PIXELS", "IMAGE_DECODE_FAILED", "FILE_TOO_LARGE"])(
    "%s 는 사진을 바꾸면 되는 실패다",
    (code) => {
      expect(new ApiError(code, "", 422).isRetryableWithDifferentPhoto).toBe(true);
    },
  );

  it.each(["ANALYZER_UNAVAILABLE", "INTERNAL_ERROR", "UNAUTHORIZED"])(
    "%s 는 사진 문제가 아니다",
    (code) => {
      expect(new ApiError(code, "", 503).isRetryableWithDifferentPhoto).toBe(false);
    },
  );
});
