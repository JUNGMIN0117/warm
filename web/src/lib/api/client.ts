import { NetworkError, toApiError } from "./errors";
import { loadAuth } from "@/lib/auth/token-storage";

/**
 * 게이트웨이 호출의 공통 관문.
 *
 * 경로는 항상 상대경로(/api/v1/...)다 — Next 서버의 rewrite가 게이트웨이로
 * 중계하므로 브라우저는 오리진을 하나만 본다 (next.config.ts).
 *
 * 토큰이 있으면 항상 붙인다. 분석은 익명도 되지만 로그인 상태에서는
 * 토큰을 실어야 이력에 저장된다(익명 200 / 로그인 201). "이 요청은 인증이
 * 필요한가"를 클라이언트가 판단하지 않는 것이 게이트웨이의 설계와 맞다 —
 * 잘못된 토큰도 401이 아니라 익명으로 취급된다.
 */

interface RequestOptions {
  method?: string;
  body?: BodyInit;
  headers?: Record<string, string>;
  signal?: AbortSignal;
}

async function request(path: string, options: RequestOptions = {}): Promise<Response> {
  const headers: Record<string, string> = { ...options.headers };
  const auth = loadAuth();
  if (auth !== null) {
    headers.Authorization = `Bearer ${auth.accessToken}`;
  }

  let response: Response;
  try {
    response = await fetch(path, {
      method: options.method ?? "GET",
      headers,
      body: options.body,
      signal: options.signal,
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === "AbortError") throw cause;
    throw new NetworkError(cause);
  }

  if (!response.ok) {
    throw await toApiError(response);
  }
  return response;
}

/** JSON body를 보내고 JSON을 받는다. */
export async function requestJson<T>(
  path: string,
  options: { method?: string; body?: unknown; signal?: AbortSignal } = {},
): Promise<T> {
  const response = await request(path, {
    method: options.method,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    headers: options.body === undefined ? {} : { "Content-Type": "application/json" },
    signal: options.signal,
  });
  return (await response.json()) as T;
}

/** multipart/form-data 전송. Content-Type은 브라우저가 boundary와 함께 채운다. */
export async function requestMultipart<T>(
  path: string,
  form: FormData,
  options: { signal?: AbortSignal } = {},
): Promise<T> {
  const response = await request(path, { method: "POST", body: form, signal: options.signal });
  return (await response.json()) as T;
}
