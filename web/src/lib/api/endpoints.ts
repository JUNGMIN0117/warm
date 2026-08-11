import { requestJson, requestMultipart } from "./client";
import type {
  AnalysisResponse,
  AuthResponse,
  HistoryItem,
  LoginRequest,
  RegisterRequest,
} from "./types";

/**
 * 게이트웨이 엔드포인트 함수들 (docs/05-api-spec.md §10).
 * 훅(TanStack Query)과 분리해 두는 이유: 이 계층은 fetch만 알고 React를
 * 모른다. 테스트에서 React 렌더링 없이 계약을 검증할 수 있다.
 */

/** 사진 분석. 하이브리드 시각화가 단계 이미지를 항상 쓰므로 includeStages를 기본 켠다. */
export function analyzeImage(
  image: Blob,
  options: { includeStages?: boolean; signal?: AbortSignal } = {},
): Promise<AnalysisResponse> {
  const includeStages = options.includeStages ?? true;
  const form = new FormData();
  form.append("image", image, image instanceof File ? image.name : "capture.jpg");
  return requestMultipart<AnalysisResponse>(
    `/api/v1/analyses?includeStages=${includeStages}`,
    form,
    { signal: options.signal },
  );
}

export function fetchHistory(limit = 20): Promise<HistoryItem[]> {
  return requestJson<HistoryItem[]>(`/api/v1/analyses?limit=${limit}`);
}

export function fetchAnalysis(id: string): Promise<AnalysisResponse> {
  return requestJson<AnalysisResponse>(`/api/v1/analyses/${encodeURIComponent(id)}`);
}

export function register(body: RegisterRequest): Promise<AuthResponse> {
  return requestJson<AuthResponse>("/api/v1/auth/register", { method: "POST", body });
}

export function login(body: LoginRequest): Promise<AuthResponse> {
  return requestJson<AuthResponse>("/api/v1/auth/login", { method: "POST", body });
}
