/**
 * 게이트웨이 HTTP 클라이언트.
 *
 * fetch를 얇게 감싼다 — 오류를 {code, message, detail} 계약으로 정규화하고,
 * 토큰이 있으면 Authorization 헤더를 붙이는 것이 전부다.
 * 재시도·캐싱은 TanStack Query의 몫이므로 여기 두지 않는다.
 */

import { getSession } from "./token";
import type { ApiErrorBody, ApiErrorCode } from "./types";

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

/**
 * 게이트웨이가 돌려준 오류. code가 계약이고 message는 표시용이다.
 *
 * 422 계열(NO_FACE_DETECTED 등)의 message는 측정기(ML 서비스)가 쓴
 * 문구가 그대로 전달되므로 가공 없이 사용자에게 보여준다 —
 * 실패 원인을 가장 잘 아는 쪽의 안내가 가장 구체적이다.
 */
export class ApiError extends Error {
  readonly code: ApiErrorCode;
  readonly status: number;
  readonly detail: unknown;

  constructor(status: number, body: ApiErrorBody) {
    super(body.message);
    this.name = "ApiError";
    this.status = status;
    this.code = body.code;
    this.detail = body.detail;
  }
}

/** 네트워크 단절 등 응답 자체가 없는 실패. ApiError와 구분해 안내한다. */
export class NetworkError extends Error {
  constructor(cause: unknown) {
    super("서버에 연결할 수 없습니다. 네트워크 상태를 확인해 주세요.");
    this.name = "NetworkError";
    this.cause = cause;
  }
}

interface RequestOptions {
  method?: string;
  body?: BodyInit;
  /** JSON body를 보낼 때만 Content-Type을 지정한다 — FormData는 브라우저가 boundary를 붙인다. */
  json?: unknown;
  signal?: AbortSignal;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers();
  const session = getSession();
  if (session) {
    // 잘못된 토큰은 게이트웨이가 401이 아니라 익명으로 취급하므로,
    // 여기서 만료를 엄격히 거를 필요는 없다. 만료 처리는 token.ts가 한다.
    headers.set("Authorization", `Bearer ${session.accessToken}`);
  }

  let body = options.body;
  if (options.json !== undefined) {
    headers.set("Content-Type", "application/json");
    body = JSON.stringify(options.json);
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method: options.method ?? "GET",
      headers,
      body,
      signal: options.signal,
    });
  } catch (cause) {
    throw new NetworkError(cause);
  }

  if (!response.ok) {
    throw new ApiError(response.status, await parseErrorBody(response));
  }
  return (await response.json()) as T;
}

/**
 * 오류 본문 파싱. 게이트웨이는 항상 {code, message, detail}을 주지만,
 * 프록시·LB가 대신 응답하는 경우(HTML 502 등)에 대비해 방어적으로 읽는다.
 */
async function parseErrorBody(response: Response): Promise<ApiErrorBody> {
  try {
    const body: unknown = await response.json();
    if (
      typeof body === "object" &&
      body !== null &&
      "code" in body &&
      "message" in body
    ) {
      return body as ApiErrorBody;
    }
  } catch {
    // JSON이 아니면 아래 폴백으로.
  }
  return {
    code: "INTERNAL_ERROR",
    message: "서버가 예상하지 못한 응답을 보냈습니다. 잠시 후 다시 시도해 주세요.",
    detail: null,
  };
}
