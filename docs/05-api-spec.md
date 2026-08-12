# 05. API 명세

두 개의 HTTP 계약이 있습니다.

| | 소비자 | 문서 |
|---|---|---|
| **Spring 게이트웨이** `/api/v1/**` | 프론트엔드 | [§10 아래](#10-spring-게이트웨이-apiv1) |
| **ML 서비스** `/v1/**` | Spring 게이트웨이만 | §1~§9 (이 문서 본문) |

아래 본문은 ML 서비스 계약이고, 게이트웨이 계약은 §10에 있습니다. 둘의 관계는 [01-architecture.md](01-architecture.md)를 보세요.

---

## ML 서비스

이 절은 `ml-service`가 노출하는 HTTP 계약을 설명합니다. 소비자는 Spring 게이트웨이 하나이며, 프론트엔드는 이 서비스를 직접 호출하지 않습니다.

기계가 읽는 명세는 서버가 스스로 제공합니다 — 기동 후 `/openapi.json`, 사람이 볼 문서는 `/docs`. 이 문서는 그 스키마가 **왜 그런 모양인지**를 설명합니다.

---

## 1. 이 서비스의 성격

**완전 무상태입니다.** DB도 인증도 세션도 없습니다 ([ADR-001](07-decisions/ADR-001-tech-stack.md)). 입력은 이미지 바이트, 출력은 JSON, 그 사이에 아무것도 남기지 않습니다.

이 제약이 실질적으로 사주는 것이 셋 있습니다. 어느 인스턴스로 요청이 가든 같은 답이 나오므로 **수평 확장이 자유롭고**, 잃을 상태가 없으므로 **재시작이 무해하며**, 같은 입력이 같은 출력을 내므로 **Spring이 이미지 해시를 키로 캐시할 수 있습니다.**

마지막 항목 때문에 응답은 **결정론적**이어야 합니다. 난수도, 처리 시각도, 요청 카운터도 응답에 넣지 않습니다. 응답에 타임스탬프를 하나 넣는 순간 캐시 히트율이 0이 됩니다.

### 경로에 버전이 있는 이유

`/v1/analyze`입니다. Spring이 이 스키마에 강하게 결합되므로, 호환 불가 변경이 필요해질 때 `/v2`를 병행 운영할 여지를 미리 둡니다. `/health`는 버전을 붙이지 않습니다 — 오케스트레이터가 보는 엔드포인트라 스키마 진화의 대상이 아닙니다.

---

## 2. `GET /health`

```json
{ "status": "ok", "model_loaded": true, "detector_pool_size": 2, "version": "0.1.0" }
```

`model_loaded`를 따로 보고하는 이유가 있습니다. **모델 파일이 없어도 서버는 기동합니다.** 기동 자체를 실패시키면 컨테이너가 크래시 루프에 빠지고, 그러면 `/health`로 원인을 알릴 방법조차 사라집니다. 뜨긴 뜨되 `status: "degraded"`로 정직하게 보고하는 편이 운영에서 진단 가능합니다.

이 구분은 오케스트레이터 설정과 맞물립니다 — liveness는 200이면 통과, readiness는 `status == "ok"`를 봐야 합니다.

---

## 3. `POST /v1/analyze`

`multipart/form-data`로 `image` 필드에 사진을 넣습니다.

| 파라미터 | 위치 | 기본값 | 설명 |
|---|---|---|---|
| `image` | form | 필수 | JPEG/PNG 얼굴 사진 |
| `include_stages` | query | `false` | 전처리 단계 이미지를 base64로 함께 반환 |

### 성공 응답 (합성 얼굴 `#C68642` 입력)

```json
{
  "season": "autumn_warm",
  "confidence": 0.822,
  "probabilities": {
    "spring_warm": 0.132,
    "summer_cool": 0.004,
    "autumn_warm": 0.822,
    "winter_cool": 0.042
  },
  "undertone": "warm",
  "undertone_confidence": 0.954,
  "axes": [
    {
      "name": "undertone", "raw_value": 68.42, "normalized": 0.783,
      "low_label": "쿨(푸른기)", "high_label": "웜(노란기)",
      "interpretation": "웜 성향이 뚜렷합니다"
    },
    {
      "name": "depth", "raw_value": 13.55, "normalized": 0.054,
      "low_label": "딥(깊은)", "high_label": "라이트(밝은)",
      "interpretation": "딥 성향이 뚜렷합니다"
    },
    {
      "name": "clarity", "raw_value": 49.08, "normalized": 0.980,
      "low_label": "뮤트(부드러운)", "high_label": "클리어(선명한)",
      "interpretation": "클리어 성향이 뚜렷합니다"
    }
  ],
  "features": {
    "lightness": 61.00, "a_star": 18.05, "b_star": 45.64,
    "chroma": 49.08, "hue_angle": 68.42,
    "ita": 13.55, "ita_category": "tan",
    "lightness_spread": 0.85, "pixel_count": 12453,
    "median_rgb": [198, 134, 66]
  },
  "white_balance": {
    "method": "gray_world",
    "gains": [1.000, 1.000, 1.000],
    "cast_strength": 0.0002
  },
  "mask_quality": { "coverage_ratio": 0.764, "otsu_threshold": 50.0 },
  "quality_factor": 1.0,
  "warnings": [],
  "stages": null
}
```

### 확률 분포를 통째로 주는 이유

`season` 하나만 반환하면 소비자가 **"62% 봄 / 35% 여름"인 경계 케이스와 "97% 겨울"인 확실한 케이스를 구분할 수 없습니다.** 도메인 불변식 4가 이것을 금지하고 있고, API 계층도 같은 규칙을 따릅니다. `test_returns_full_probability_distribution`이 계약으로 고정합니다.

언더톤을 따로 보고하는 것도 같은 맥락입니다. 웜/쿨 2분류는 4분류를 병합한 것이라 **항상 더 견고합니다.** 4계절 판정이 애매해도 "웜톤인 것은 확실하고 봄과 가을 사이"라고 말할 수 있는 경우가 많은데, top-1만 내보내면 그 유용한 정보가 통째로 버려집니다.

### 판정 근거를 함께 주는 이유

`features`와 `axes`는 UI를 위한 장식이 아닙니다. 원본 프로젝트의 결과는 CNN이 뱉은 숫자 하나였고 사용자가 검증할 방법이 없었습니다. 블랙박스 탈출이 재구축의 목표 중 하나이므로, **"보정 노란기가 34.2라서 웜"**까지 응답에 실립니다. (언더톤 축의 `raw_value`는 판정에 실제로 쓰인 값 — 초기의 h°에서 실측 재보정을 거쳐 명도 보정 노란기 `b* + 0.30·(L*−50)`로 바뀌었습니다, ADR-010. h° 자체는 여전히 `features.hue_angle`로 보고됩니다.)

`white_balance`와 `mask_quality`는 한발 더 나갑니다 — **우리가 사진을 얼마나 건드렸는지**를 공개합니다. 보정하고 침묵하는 것보다 보정량을 밝히는 편이 이 프로젝트의 태도에 맞습니다.

### 응답에 없는 것 (ADR-005)

**팔레트·한국어 라벨·이모지·스타일링 팁은 이 서비스가 반환하지 않습니다.** 큐레이션 결과물이지 측정 결과가 아니므로 Spring이 DB에서 소유합니다. 여기서 나가는 것은 `"autumn_warm"`이라는 enum 값까지이고, 그것을 "🍂 가을 웜"으로 만들고 팔레트를 붙이는 일은 게이트웨이의 몫입니다.

이유는 배포 주기입니다. "봄 웜에 코랄 대신 살구를 넣자"는 큐레이션 판단인데, 그것이 Python 소스에 있으면 색 하나 바꾸는 데 모델 로딩이 무거운 추론 서버를 재배포해야 합니다.

경계가 조용히 무너지지 않도록 `test_does_not_leak_presentation_concerns`가 응답에 `best_colors`·`label_ko` 같은 키가 없음을 검사합니다. 반대 방향으로는 `test_season_codes_match_palette_export`가 API의 계절 코드와 Spring이 시드할 팔레트의 `code`가 일치하는지 확인합니다 — 어긋나면 런타임에 "팔레트를 찾을 수 없음"이 되기 때문입니다.

---

## 4. 전처리 단계 이미지

`?include_stages=true`를 붙이면 `stages`가 채워집니다. 다섯 장 모두 base64 WebP data URI입니다.

| 키 | 내용 |
|---|---|
| `original` | 디코딩 직후 (EXIF 회전 보정 완료) |
| `white_balanced` | 화이트밸런스 적용 후 |
| `face_crop` | 얼굴 영역 크롭 |
| `skin_mask` | 최종 피부 마스크 (흑백, **무손실**) |
| `measured_pixels` | 실제로 측정에 쓰인 픽셀만 남긴 이미지 |

기본을 `false`로 둔 이유는 소비 패턴이 둘로 갈리기 때문입니다. Spring이 이력 저장 목적으로 호출할 때는 수치만 필요하고, 프론트가 파이프라인을 시각화할 때만 이미지가 필요합니다.

크기 통제는 세 겹입니다 — 최대 변 512px로 축소, WebP 손실 압축(품질 80), 그리고 마스크만 예외로 무손실. 마스크를 손실 압축하면 경계에 링잉이 생겨 "어디까지가 측정 범위인가"가 흐려지는데, 그건 이 이미지의 존재 이유와 정면으로 충돌합니다.

실측 크기 (240×320 합성 얼굴 기준, 전체 응답 12.8KB):

| 단계 | base64 길이 |
|---|---|
| `original` | 2,135자 |
| `white_balanced` | 2,135자 |
| `face_crop` | 2,231자 |
| `skin_mask` | 931자 |
| `measured_pixels` | 3,847자 |

실제 사진(1600px 입력)에서는 이보다 커지지만, 단계 이미지가 512px로 고정되므로 상한이 있습니다. `test_stage_payload_stays_bounded`가 400KB를 계약으로 못 박습니다 — 인라인 base64를 택한 대가가 무제한 응답이 되면 그 결정이 틀린 것이 되기 때문입니다.

왜 URL이 아니라 인라인인지는 [ADR-005](07-decisions/ADR-005-service-boundary.md)에 있습니다. 요약하면 이미지를 서버에 보관하는 순간 무상태가 깨지고, 무상태가 사주던 캐시 가능성과 확장성을 잃습니다.

---

## 5. 오류 응답

FastAPI 기본 `{"detail": "..."}` 대신 통일된 형태를 씁니다.

```json
{
  "code": "IMAGE_DECODE_FAILED",
  "message": "이미지를 해석할 수 없습니다. JPEG/PNG 형식의 손상되지 않은 파일인지 확인해 주세요.",
  "detail": null
}
```

`code`가 있는 이유는 **Spring과 프론트가 문자열 매칭 없이 분기해야** 하기 때문입니다. `message`는 한국어이고 문구가 언제든 바뀔 수 있지만 `code`는 계약입니다. `detail`은 코드별 부가 정보를 구조화해 담습니다 — 소비자가 메시지를 파싱할 일이 없도록.

| 코드 | 상태 | 상황 | `detail` |
|---|---|---|---|
| `IMAGE_DECODE_FAILED` | 400 | 손상된 파일, 이미지가 아님, 빈 파일 | — |
| `FILE_TOO_LARGE` | 413 | 업로드 한도(기본 12MB) 초과 | `max_bytes` |
| `NO_FACE_DETECTED` | 422 | 얼굴을 찾지 못함 | — |
| `MULTIPLE_FACES` | 422 | 얼굴이 2명 이상 | `face_count` |
| `INSUFFICIENT_SKIN_PIXELS` | 422 | 마스킹 후 픽셀이 하드 플로어(100) 미만 | `pixel_count`, `minimum` |
| `MODEL_NOT_AVAILABLE` | 503 | 모델 미로드 (서버 구성 문제) | — |

### 4xx와 5xx를 가르는 기준

**사용자가 다시 시도해서 고칠 수 있는가**입니다. 얼굴이 없거나 여러 명이거나 사진이 깨진 것은 사진을 바꾸면 되므로 4xx이고, 모델 파일이 없는 것은 사용자가 할 수 있는 게 없으므로 5xx입니다.

이 구분이 중요한 이유는 Spring의 Resilience4j 때문입니다. 서킷 브레이커는 5xx를 장애 신호로 세는데, **"얼굴 없는 사진"을 5xx로 내보내면 사용자가 잘못된 사진을 몇 장 올린 것만으로 회로가 열려 정상 요청까지 막힙니다.** `test_missing_model_yields_503_not_422`가 이 경계를 지킵니다.

### 왜 파이프라인은 HTTP를 모르는가

예외에 상태 코드를 붙이지 않고 `app/api/error_mapping.py`가 번역을 맡습니다. 도메인이 이미지 I/O를 모르고, 파이프라인이 HTTP를 모른다는 계층 규칙의 연장입니다. 덕분에 파이프라인을 배치 작업이나 CLI로 재사용해도 HTTP 어휘가 따라붙지 않습니다.

---

## 6. 동시성과 자원

### 왜 동기 엔드포인트인가

추론은 CPU 바운드입니다. `async def`로 만들면 이벤트 루프를 점유해 다른 요청의 I/O까지 막습니다. 동기 `def`로 두면 FastAPI가 스레드풀에서 실행하므로 이벤트 루프가 살아 있습니다.

대신 문제가 하나 생깁니다 — **MediaPipe FaceLandmarker는 스레드 안전이 보장되지 않습니다.** 여러 요청이 같은 인스턴스를 동시에 건드리면 결과가 섞이거나 죽을 수 있습니다.

### 검출기 풀

고정 크기 풀(`DetectorPool`)로 감쌌습니다. 검토한 대안들:

**전역 락** — 가장 단순하지만 처리량이 인스턴스 하나로 고정됩니다. 두 번째 요청이 첫 번째가 끝날 때까지 통째로 기다립니다.

**요청마다 새 인스턴스** — 모델 로딩이 수백 ms라 요청당 그 비용은 말이 안 됩니다.

**스레드 로컬** — 스레드풀이 스레드를 재사용하므로 그럴듯하지만, 스레드 수를 우리가 통제하지 못해 인스턴스가 몇 개나 생길지 알 수 없습니다. 메모리 사용량이 예측 불가능해집니다.

**고정 크기 풀 ✅** — 인스턴스 수를 설정으로 못 박아 메모리 상한이 명확하고, 풀이 비면 대기합니다(거절이 아니라 지연). 풀이 `SupportsFaceDetection` 프로토콜을 직접 구현하므로 파이프라인은 자기가 풀을 쓰는지 단일 검출기를 쓰는지 알 필요가 없습니다.

빌릴 때 타임아웃을 두지 않았습니다. 부하 상황의 포기 판단은 서비스 경계(Spring의 Resilience4j 타임아웃)에서 하는 편이 낫고, 이 계층은 순서만 지킵니다.

### 입력 크기 제한

업로드는 기본 12MB까지 받고, 그보다 크면 **디코딩하기 전에** 413으로 거절합니다. 디코딩 후에 거절하면 거절할 요청에 압축 해제 비용을 이미 치른 뒤입니다.

디코딩된 이미지는 최대 변 1600px로 축소한 뒤 파이프라인을 태웁니다. 속도만의 문제가 아닙니다 — 4000px 사진은 피부 픽셀이 수백만 개인데 중앙값 통계는 수만 개면 이미 수렴하므로 나머지는 순수한 비용입니다. 면적 평균(`INTER_AREA`)으로 줄이므로 색 통계는 보존됩니다(`test_color_statistics_survive_downscaling`).

---

## 7. 설정

환경변수로 덮어씁니다. 접두사는 `PCAI_`입니다.

| 변수 | 기본값 | 용도 |
|---|---|---|
| `PCAI_MODEL_PATH` | `models/face_landmarker.task` | 모델 가중치 경로 |
| `PCAI_DETECTOR_POOL_SIZE` | `2` | 검출기 인스턴스 수 |
| `PCAI_MAX_UPLOAD_BYTES` | `12582912` | 업로드 한도 |
| `PCAI_MAX_INPUT_EDGE` | `1600` | 입력 이미지 최대 변 |
| `PCAI_STAGE_IMAGE_EDGE` | `512` | 단계 이미지 최대 변 |
| `PCAI_STAGE_IMAGE_QUALITY` | `80` | 단계 이미지 WebP 품질 |

알고리즘 상수(`CalibrationConfig`, `MaskConfig`)는 **여기 없습니다.** 코드와 함께 버전 관리되어야 하는 값이기 때문입니다. 환경변수로 노출되는 것은 배포 환경마다 달라야 하는 운영 파라미터뿐입니다.

---

## 8. 로컬 실행

```bash
cd ml-service && uv run uvicorn app.api.main:app --reload --port 8000
```

기동 후 **`http://127.0.0.1:8000/docs`** 에서 스키마를 확인하고 직접 호출해 볼 수 있습니다.

> ⚠️ **`localhost`가 아니라 `127.0.0.1`을 쓰세요 (Windows).**
> uvicorn의 기본 바인딩은 IPv4 `127.0.0.1` 하나인데, Windows에서 `localhost`는 `::1`(IPv6)로 먼저 해석됩니다. 브라우저는 `::1`을 시도하고 거절당한 뒤 IPv4로 폴백하지 않는 경우가 많아 *"localhost에서 연결을 거부했습니다"* 가 뜹니다. 서버는 멀쩡한데 주소만 어긋난 상황입니다.
>
> 굳이 `localhost`로 접속하고 싶다면 `--host ::1`로 IPv6에 바인딩하면 되지만, 그러면 반대로 `127.0.0.1`이 막힙니다. Windows는 IPv6 소켓의 `IPV6_V6ONLY`가 기본 활성이라 `--host ::` 하나로 양쪽을 덮을 수 없습니다(Linux는 덮입니다). 개발 중에는 `127.0.0.1`로 통일하는 편이 단순합니다.
>
> 컨테이너에서는 이 문제가 없습니다 — `--host 0.0.0.0`으로 띄우고 Compose 네트워크의 서비스 이름으로 접근합니다.

---

## 9. 알려진 한계

**인증이 없습니다.** 의도된 것입니다 — 이 서비스는 내부 네트워크에서 Spring만 호출한다는 전제이고, 외부 노출은 게이트웨이가 막습니다. Step 6에서 Docker Compose 네트워크로 격리합니다.

~~**요청 단위 로깅·추적이 없습니다.**~~ → **해결됨 (Step 6).** 게이트웨이가 발급한 상관관계 ID를 `X-Request-Id` 헤더로 받아 요청 스코프에 바인딩하고, 모든 로그에 `request_id` 필드로 싣고, 응답 헤더로 되돌려줍니다. 이 서비스는 ID를 **발급하지 않습니다** — 발급 주체는 게이트웨이 하나입니다 (ADR-008). 컨테이너에서는 `PCAI_LOG_FORMAT=json`으로 구조화 로그가 켜집니다.

**단계 이미지 크기 상한이 합성 이미지 기준입니다.** 400KB라는 숫자는 240×320 합성 얼굴에서 잰 값에 여유를 둔 것입니다. 1600px 실사진에서의 실측은 아직 하지 않았습니다.

**`include_stages`가 캐시 키의 일부입니다.** Spring이 이미지 해시로만 캐시하면 `include_stages=false`로 캐시된 응답이 `true` 요청에 반환될 수 있습니다. ~~Step 3에서 캐시 키에 이 플래그를 포함해야 합니다.~~ → **해결됨.** 캐시 키가 `SHA-256(이미지) + include_stages`이고 `stageFlagIsPartOfCacheKey` 테스트가 이를 고정합니다.

---

## 10. Spring 게이트웨이 (`/api/v1`)

프론트엔드가 소비하는 계약입니다. 여기서 측정값(Python)과 큐레이션(DB)이 합쳐집니다.

### 인증

**분석은 로그인 없이 됩니다.** 계정은 이력을 보고 싶은 사람만 만듭니다 ([01-architecture.md §6](01-architecture.md)).

| 엔드포인트 | 인증 | 성공 |
|---|---|---|
| `POST /api/v1/auth/register` | 불필요 | 201 |
| `POST /api/v1/auth/login` | 불필요 | 200 |
| `POST /api/v1/analyses` | **선택** | 익명 200 · 로그인 201 |
| `GET /api/v1/analyses` | 필요 | 200 |
| `GET /api/v1/analyses/{id}` | 필요 | 200 |
| `GET /api/v1/seasons` | 불필요 | 200 |
| `GET /api/v1/seasons/{code}` | 불필요 | 200 |
| `GET /actuator/health` | 불필요 | 200 |

토큰은 `Authorization: Bearer <token>` 헤더로 보냅니다. **잘못된 토큰은 401이 아니라 익명으로 취급**됩니다 — 익명 분석을 막지 않기 위해서입니다.

### `POST /api/v1/auth/register`

```json
{ "email": "me@example.com", "displayName": "정민", "password": "10자 이상" }
```

응답에 토큰이 함께 옵니다. 가입하고 다시 로그인하게 만드는 것은 불필요한 마찰입니다.

```json
{
  "accessToken": "eyJhbGciOi...",
  "expiresAt": "2026-08-10T18:00:00Z",
  "userId": "0b6f...",
  "displayName": "정민"
}
```

`expiresAt`을 주는 이유는 클라이언트가 JWT를 디코드하지 않고도 재로그인 시점을 알게 하기 위해서입니다. 프론트가 payload를 파싱하게 만들면 토큰 구조가 사실상 공개 계약이 됩니다.

### `POST /api/v1/analyses`

`multipart/form-data`, 필드명 `image`. 쿼리 `?includeStages=true`로 단계 이미지 요청.

응답은 ML 응답에 계절 큐레이션을 붙이고 재구성한 것입니다.

```json
{
  "id": "…", "analyzedAt": "2026-08-10T09:00:00Z",
  "saved": false,
  "season": {
    "code": "autumn_warm", "labelKo": "가을 웜", "labelEn": "Autumn Warm", "emoji": "🍂",
    "keywords": ["깊은", "따뜻한", "차분한"],
    "description": "…",
    "bestColors": [{ "name": "머스타드", "hex": "#D4A017" }, …],
    "worstColors": […],
    "stylingTips": ["…"]
  },
  "confidence": 0.822,
  "probabilities": { "spring_warm": 0.132, "summer_cool": 0.004,
                     "autumn_warm": 0.822, "winter_cool": 0.042 },
  "undertone": "warm", "undertoneConfidence": 0.954,
  "topTwoMargin": 0.690,
  "axes": [ { "name": "undertone", "rawValue": 68.42, "normalized": 0.783,
              "lowLabel": "쿨(푸른기)", "highLabel": "웜(노란기)",
              "interpretation": "웜 성향이 뚜렷합니다" }, … ],
  "features": { "lightness": 61.0, "hueAngle": 68.42, "ita": 13.55,
                "itaCategory": "tan", "pixelCount": 12453, "medianRgbHex": "#C68642", … },
  "preprocessing": { "whiteBalanceMethod": "gray_world", "gains": [1.0, 1.0, 1.0],
                     "castStrength": 0.0002, "maskCoverageRatio": 0.764 },
  "qualityFactor": 1.0, "warnings": [],
  "stages": null
}
```

**`saved`가 중요합니다.** 익명 분석은 저장되지 않으므로 프론트가 "이력에 담겼습니다"를 잘못 안내하지 않도록 사실을 그대로 알려줍니다. 상태 코드도 갈립니다 — 익명 200, 로그인 201.

**`topTwoMargin`은 게이트웨이가 계산해 추가한 값**입니다. 절대 확률만 보면 "55%"가 확실해 보이지만 2위가 44%면 사실상 동점입니다. 프론트가 "두 계절 사이입니다"를 판단할 재료입니다.

### `GET /api/v1/analyses`

내 이력, 최신순. `?limit=20` (최대 50).

```json
[ { "id": "…", "analyzedAt": "…", "seasonCode": "autumn_warm",
    "seasonLabelKo": "가을 웜", "emoji": "🍂",
    "confidence": 0.822, "medianRgbHex": "#C68642" } ]
```

**원본 이미지가 없습니다.** 저장하지 않기 때문이고, 그래서 이력 화면은 대표 색 칩과 수치로 구성됩니다 ([01-architecture.md §5](01-architecture.md)).

### `GET /api/v1/analyses/{id}`

단건. **남의 분석을 요청하면 403이 아니라 404**입니다 — 403은 "그 id는 존재한다"를 알려주는 셈입니다.

### 오류 코드

ML 서비스와 같은 `{code, message, detail}` 형태입니다.

| 코드 | 상태 | 상황 |
|---|---|---|
| `VALIDATION_FAILED` | 400 | 입력 형식 오류. `detail`에 필드별 메시지 |
| `INVALID_REQUEST` | 400 | 도메인 검증 실패 (예: 모르는 계절 코드) |
| `IMAGE_DECODE_FAILED` | 400 | 빈 파일, 읽기 실패 |
| `UNAUTHORIZED` | 401 | 인증 필요 |
| `INVALID_CREDENTIALS` | 401 | 로그인 실패 |
| `FORBIDDEN` | 403 | 권한 없음 |
| `NOT_FOUND` | 404 | 없거나 내 것이 아님 |
| `EMAIL_ALREADY_USED` | 409 | 중복 가입 |
| `FILE_TOO_LARGE` | 413 | 12MB 초과 |
| `NO_FACE_DETECTED` · `MULTIPLE_FACES` · `INSUFFICIENT_SKIN_PIXELS` | 422 | ML에서 전파 |
| `ANALYZER_UNAVAILABLE` | 503 | ML 장애·타임아웃·서킷 오픈 |
| `CATALOG_UNAVAILABLE` · `INTERNAL_ERROR` | 500 | 서버 문제 |

422 계열의 `message`는 **ML 서비스가 쓴 문구를 그대로 전달**합니다. 실패 원인을 가장 잘 아는 쪽이 측정기이므로 안내도 그쪽이 구체적입니다.

### 상관관계 ID (`X-Request-Id`)

모든 응답에 `X-Request-Id` 헤더가 실립니다. 게이트웨이가 발급하며(유효한 형식 `[A-Za-z0-9._-]{8,64}`으로 들어온 값은 수용), ml-service 호출까지 전파되어 **세 서비스의 로그를 요청 하나로 꿸 수 있습니다.** 프론트는 실패 화면에 이 값을 "문의 코드"로 노출합니다 — 이 코드 하나로 해당 요청이 남긴 모든 로그를 찾을 수 있습니다 (ADR-008).

### 게이트웨이 설정

| 변수 | 기본값 | 용도 |
|---|---|---|
| `PCAI_JWT_SECRET` | **없음 (필수)** | JWT 서명 키. 없으면 기동 실패 |
| `ML_SERVICE_URL` | `http://127.0.0.1:8000` | ML 서비스 주소 |
| `DB_URL` · `DB_USERNAME` · `DB_PASSWORD` | localhost 기본값 | PostgreSQL |
| `REDIS_HOST` · `REDIS_PORT` | `localhost:6379` | Redis |
| `SERVER_PORT` | `8080` | |

서명 키에 기본값이 없는 것은 의도입니다 — 기본 키는 그대로 배포됩니다.

```bash
openssl rand -base64 48
```

### 로컬 실행

PostgreSQL과 Redis가 필요합니다. ML 서비스도 함께 띄워야 분석이 동작합니다.

```bash
cd backend && PCAI_JWT_SECRET=$(openssl rand -base64 48) ./mvnw spring-boot:run -pl backend-api
```
