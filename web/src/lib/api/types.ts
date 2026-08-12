/**
 * Spring 게이트웨이 /api/v1 계약의 TypeScript 표현.
 *
 * 원본은 backend의 AnalysisDtos·AuthDtos 레코드다 (docs/05-api-spec.md §10).
 * 게이트웨이 응답이 곧 프론트의 데이터 모델이므로 여기서 별도 변환 계층을
 * 두지 않는다 — 필드명이 어긋나면 이 파일과 백엔드 DTO 중 한쪽이 틀린 것이다.
 */

/** 4계절 코드. ml-service의 Season enum과 팔레트 시드의 code가 이 값으로 일치한다. */
export type SeasonCode = "spring_warm" | "summer_cool" | "autumn_warm" | "winter_cool";

export type Undertone = "warm" | "cool";

export interface ColorView {
  name: string;
  hex: string;
}

/** 계절 큐레이션 — DB가 소유하는 부분 (ADR-005). */
export interface SeasonView {
  code: SeasonCode;
  labelKo: string;
  labelEn: string;
  emoji: string;
  keywords: string[];
  description: string;
  bestColors: ColorView[];
  worstColors: ColorView[];
  stylingTips: string[];
}

/** 3축 판정 근거. normalized는 0(low)~1(high). */
export interface AxisView {
  name: "undertone" | "depth" | "clarity" | (string & {});
  rawValue: number;
  normalized: number;
  lowLabel: string;
  highLabel: string;
  interpretation: string;
}

export interface FeaturesView {
  lightness: number;
  aStar: number;
  bStar: number;
  chroma: number;
  hueAngle: number;
  ita: number;
  itaCategory: string;
  pixelCount: number;
  medianRgbHex: string;
}

/** 전처리가 사진을 얼마나 건드렸는지 — 보정하고 침묵하지 않는다. */
export interface PreprocessingView {
  whiteBalanceMethod: string;
  gains: number[];
  castStrength: number;
  maskCoverageRatio: number;
}

/** 전처리 단계 이미지. 전부 base64 data URI (WebP, 마스크만 무손실). */
export interface StagesView {
  original: string;
  whiteBalanced: string;
  faceCrop: string;
  skinMask: string;
  measuredPixels: string;
}

export interface AnalysisResponse {
  id: string;
  analyzedAt: string;
  /** 익명 분석은 저장되지 않는다. 프론트가 "이력에 담김"을 잘못 안내하지 않기 위한 사실 보고. */
  saved: boolean;
  season: SeasonView;
  confidence: number;
  probabilities: Record<SeasonCode, number>;
  undertone: Undertone;
  undertoneConfidence: number;
  /** 1위-2위 확률 차. 절대 확률만으로는 "55% vs 44%" 같은 사실상 동점을 못 가린다. */
  topTwoMargin: number;
  axes: AxisView[];
  features: FeaturesView;
  preprocessing: PreprocessingView;
  qualityFactor: number;
  warnings: string[];
  stages?: StagesView | null;
}

/** 이력 한 줄. 원본 이미지를 저장하지 않으므로 대표 색과 수치로 구성된다. */
export interface HistoryItem {
  id: string;
  analyzedAt: string;
  seasonCode: SeasonCode;
  seasonLabelKo: string;
  emoji: string;
  confidence: number;
  medianRgbHex: string;
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

export interface AuthResponse {
  accessToken: string;
  /** ISO-8601. 클라이언트가 JWT를 디코드하지 않고 만료를 알 수 있게 서버가 준다. */
  expiresAt: string;
  userId: string;
  displayName: string;
  /** 서버가 알려주는 역할 — JWT를 디코드하지 않는 원칙의 연장. UI 노출 판단에만 쓴다. */
  role: "USER" | "ADMIN";
}

/** 큐레이션 편집 요청 (관리자). 게이트웨이 AdminDtos와 대응. */
export interface CurationUpdateRequest {
  keywords: string[];
  description: string;
  bestColors: ColorView[];
  worstColors: ColorView[];
  stylingTips: string[];
}
