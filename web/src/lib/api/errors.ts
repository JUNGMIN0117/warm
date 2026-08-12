/**
 * 게이트웨이 오류 계약 {code, message, detail}의 클라이언트 표현.
 *
 * code는 계약이고 message는 문구다 (docs/05-api-spec.md §5, §10).
 * 프론트의 모든 분기는 code로만 한다 — 문자열 매칭으로 분기하기 시작하면
 * 백엔드가 안내 문구를 다듬을 때마다 프론트가 조용히 깨진다.
 */

/** 게이트웨이가 정의한 오류 코드. 모르는 코드가 와도 죽지 않도록 열린 유니온으로 둔다. */
export type ApiErrorCode =
  | "VALIDATION_FAILED"
  | "INVALID_REQUEST"
  | "IMAGE_DECODE_FAILED"
  | "UNAUTHORIZED"
  | "INVALID_CREDENTIALS"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "EMAIL_ALREADY_USED"
  | "FILE_TOO_LARGE"
  | "NO_FACE_DETECTED"
  | "MULTIPLE_FACES"
  | "INSUFFICIENT_SKIN_PIXELS"
  | "ANALYZER_UNAVAILABLE"
  | "CATALOG_UNAVAILABLE"
  | "INTERNAL_ERROR"
  | (string & {});

export class ApiError extends Error {
  readonly code: ApiErrorCode;
  readonly status: number;
  readonly detail: unknown;
  /** 게이트웨이가 발급한 상관관계 ID (X-Request-Id). 서버 로그와 이 오류를 잇는 열쇠다. */
  readonly requestId: string | null;

  constructor(
    code: ApiErrorCode,
    message: string,
    status: number,
    detail: unknown = null,
    requestId: string | null = null,
  ) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
    this.detail = detail;
    this.requestId = requestId;
  }

  /**
   * 사용자가 사진을 바꾸면 해결되는 오류인가.
   * 422 계열은 "장애"가 아니라 "이 사진으로는 측정 불가"라는 정상 응답이다 —
   * 서킷 브레이커가 이것을 실패로 세지 않는 것과 같은 구분을 UI도 지킨다.
   */
  get isRetryableWithDifferentPhoto(): boolean {
    return (
      this.code === "NO_FACE_DETECTED" ||
      this.code === "MULTIPLE_FACES" ||
      this.code === "INSUFFICIENT_SKIN_PIXELS" ||
      this.code === "IMAGE_DECODE_FAILED" ||
      this.code === "FILE_TOO_LARGE"
    );
  }
}

/** 네트워크 단절 등 응답 자체가 없는 실패. code가 없으므로 ApiError와 구분한다. */
export class NetworkError extends Error {
  constructor(cause: unknown) {
    super("서버에 연결할 수 없습니다. 네트워크 상태를 확인해 주세요.");
    this.name = "NetworkError";
    this.cause = cause;
  }
}

interface ErrorBody {
  code?: unknown;
  message?: unknown;
  detail?: unknown;
}

/**
 * 실패 응답을 ApiError로 변환한다.
 * 게이트웨이 형식이 아닌 body(프록시 오류, HTML 등)가 와도 죽지 않고
 * 상태 코드 기반의 일반 오류로 감싼다.
 */
export async function toApiError(response: Response): Promise<ApiError> {
  let body: ErrorBody = {};
  try {
    body = (await response.json()) as ErrorBody;
  } catch {
    // JSON이 아니면 아래 폴백을 쓴다.
  }
  const code = typeof body.code === "string" ? body.code : "INTERNAL_ERROR";
  const message =
    typeof body.message === "string"
      ? body.message
      : "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
  return new ApiError(
    code,
    message,
    response.status,
    body.detail ?? null,
    response.headers.get("X-Request-Id"),
  );
}
