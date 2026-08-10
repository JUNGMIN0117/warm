/**
 * 오류 → 화면 안내 매핑.
 *
 * 규칙 (05-api-spec §5·§10):
 * - 분기는 code로만 한다. message 문자열 매칭 금지.
 * - 422 계열의 message는 측정기(ML 서비스)가 쓴 문구이므로 **그대로** 보여준다.
 *   실패 원인을 가장 잘 아는 쪽의 안내가 가장 구체적이다.
 * - "사용자가 사진을 바꿔 고칠 수 있는가"가 재시도 안내의 기준이다 —
 *   서킷 브레이커가 실패를 세는 기준과 같은 축이다.
 */

import { ApiError, NetworkError } from "./api/client";

export interface ErrorGuidance {
  /** 화면에 보여줄 제목 */
  title: string;
  /** 본문 — 422 계열은 서버 message 그대로 */
  body: string;
  /** 사진을 바꾸면 해결되는 문제인가 (재촬영/재선택 버튼 노출 기준) */
  fixableByUser: boolean;
}

/** 사진을 바꾸면 해결되는 코드 — 서비스 장애가 아니라 입력의 문제. */
const FIXABLE_CODES = new Set([
  "IMAGE_DECODE_FAILED",
  "FILE_TOO_LARGE",
  "NO_FACE_DETECTED",
  "MULTIPLE_FACES",
  "INSUFFICIENT_SKIN_PIXELS",
]);

export function toGuidance(error: unknown): ErrorGuidance {
  if (error instanceof NetworkError) {
    return {
      title: "연결할 수 없습니다",
      body: error.message,
      fixableByUser: false,
    };
  }

  if (error instanceof ApiError) {
    if (FIXABLE_CODES.has(error.code)) {
      return {
        title: "다른 사진으로 시도해 주세요",
        body: error.message,
        fixableByUser: true,
      };
    }
    if (error.code === "ANALYZER_UNAVAILABLE") {
      return {
        title: "분석 엔진이 잠시 응답하지 않습니다",
        body: "서버가 복구를 시도하고 있습니다. 잠시 후 다시 시도해 주세요. 최근에 분석한 사진이라면 캐시된 결과가 나올 수 있습니다.",
        fixableByUser: false,
      };
    }
    if (error.code === "UNAUTHORIZED") {
      return {
        title: "로그인이 필요합니다",
        body: "세션이 만료되었거나 로그인하지 않았습니다.",
        fixableByUser: false,
      };
    }
    return {
      title: "요청을 처리하지 못했습니다",
      body: error.message,
      fixableByUser: false,
    };
  }

  return {
    title: "알 수 없는 문제가 발생했습니다",
    body: "잠시 후 다시 시도해 주세요.",
    fixableByUser: false,
  };
}
