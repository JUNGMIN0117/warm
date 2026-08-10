/**
 * Spring 게이트웨이(/api/v1) 응답 계약.
 *
 * 원본은 docs/05-api-spec.md §10 — 이 파일은 그 명세의 TypeScript 사본이다.
 * 게이트웨이가 측정값(Python)과 큐레이션(DB)을 합쳐 주므로,
 * 프론트는 ML 서비스의 존재를 모른다.
 */

/** 4계절 코드. Python·Java·DB 세 곳과 일치해야 하는 계약값. */
export type SeasonCode = "spring_warm" | "summer_cool" | "autumn_warm" | "winter_cool";

export type Undertone = "warm" | "cool";

/** 계절별 확률 분포. top-1만 받지 않는 것이 도메인 불변식 4다. */
export type Probabilities = Record<SeasonCode, number>;

export interface PaletteColor {
  name: string;
  hex: string;
}

/** DB가 소유하는 큐레이션 — 라벨·팔레트·스타일링 팁 (ADR-005). */
export interface SeasonCuration {
  code: SeasonCode;
  labelKo: string;
  labelEn: string;
  emoji: string;
  keywords: string[];
  description: string;
  bestColors: PaletteColor[];
  worstColors: PaletteColor[];
  stylingTips: string[];
}

/** 판정 근거 3축. normalized는 0..1, 좌우 라벨은 서버가 준다. */
export interface AnalysisAxis {
  name: "undertone" | "depth" | "clarity";
  rawValue: number;
  normalized: number;
  lowLabel: string;
  highLabel: string;
  interpretation: string;
}

/** 측정된 색채 통계 — "h°가 68.4라서 웜"까지 보여주기 위한 재료. */
export interface AnalysisFeatures {
  lightness: number;
  aStar: number;
  bStar: number;
  chroma: number;
  hueAngle: number;
  ita: number;
  itaCategory: string;
  lightnessSpread: number;
  pixelCount: number;
  medianRgbHex: string;
}

/** 전처리가 사진을 얼마나 건드렸는지 — 보정하고 침묵하지 않는다. */
export interface PreprocessingReport {
  whiteBalanceMethod: string;
  gains: [number, number, number];
  castStrength: number;
  maskCoverageRatio: number;
}

/** 전처리 단계 이미지 5장. 각 값은 base64 WebP data URI. */
export interface PipelineStages {
  original: string;
  whiteBalanced: string;
  faceCrop: string;
  skinMask: string;
  measuredPixels: string;
}

export interface AnalysisResponse {
  id: string | null;
  analyzedAt: string;
  /** 익명 분석은 저장되지 않는다 — "이력에 담겼습니다"를 잘못 안내하지 않기 위한 사실 통보. */
  saved: boolean;
  season: SeasonCuration;
  confidence: number;
  probabilities: Probabilities;
  undertone: Undertone;
  undertoneConfidence: number;
  /** 게이트웨이가 계산한 1위-2위 확률 차. "두 계절 사이"를 판단할 재료. */
  topTwoMargin: number;
  axes: AnalysisAxis[];
  features: AnalysisFeatures;
  preprocessing: PreprocessingReport;
  qualityFactor: number;
  warnings: string[];
  stages: PipelineStages | null;
}

/** GET /api/v1/analyses 목록 항목 — 원본 이미지는 없다(저장하지 않으므로). */
export interface HistoryItem {
  id: string;
  analyzedAt: string;
  seasonCode: SeasonCode;
  seasonLabelKo: string;
  emoji: string;
  confidence: number;
  medianRgbHex: string;
}

export interface AuthResponse {
  accessToken: string;
  /** JWT를 디코드하지 않고도 재로그인 시점을 알기 위한 값. */
  expiresAt: string;
  userId: string;
  displayName: string;
}

export interface RegisterRequest {
  email: string;
  displayName: string;
  password: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

/**
 * 오류 계약. message는 언제든 바뀔 수 있는 한국어 안내문이고,
 * 분기는 반드시 code로 한다 — 문자열 매칭 금지.
 */
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
  | "INTERNAL_ERROR";

export interface ApiErrorBody {
  code: ApiErrorCode;
  message: string;
  detail: unknown;
}
