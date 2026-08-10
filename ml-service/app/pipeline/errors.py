"""파이프라인 명시적 예외 계층.

"얼굴이 안 잡히면 어떻게 되는가"는 구현 디테일이 아니라 **제품 정책**이다.
정책을 예외 타입으로 강제해 두면 상위 계층(FastAPI → Spring → 프론트)이
각 상황을 구분해 사용자에게 정확한 안내를 줄 수 있다. 제네릭 Exception
하나로 뭉개면 "알 수 없는 오류가 발생했습니다"밖에 말할 수 없게 된다.
"""

from __future__ import annotations


class PipelineError(Exception):
    """전처리 파이프라인에서 발생하는 모든 예외의 공통 조상.

    FastAPI 계층이 이 타입 하나만 잡아도 파이프라인발 오류를 전부
    구조화된 4xx 응답으로 변환할 수 있다.
    """


class ImageDecodeError(PipelineError):
    """이미지 바이트를 디코딩할 수 없음 — 손상된 파일이거나 이미지가 아님."""


class NoFaceDetectedError(PipelineError):
    """얼굴을 하나도 찾지 못함.

    정책: 억지로 진행하지 않고 즉시 실패한다. 얼굴 없이 "피부 비슷한"
    영역을 찾으면 벽지나 옷을 분석하게 되고, 그 결과는 그럴듯한 숫자로
    포장된 쓰레기다. 정직한 실패("정면 얼굴이 나온 사진을 사용해 주세요")가
    조용히 틀린 답보다 낫다는 것이 이 프로젝트의 일관된 태도다.
    """


class MultipleFacesError(PipelineError):
    """얼굴이 두 개 이상 잡힘.

    정책: 자동으로 고르지 않고 즉시 실패한다.

    기각한 대안 — "가장 큰 얼굴 자동 선택". 단체 사진에서 누구를 분석할지는
    사용자의 의도인데, 프레임 점유율은 그 의도의 신뢰할 수 없는 대리 지표다.
    자동 선택이 조용히 다른 사람을 분석하면 사용자는 그 사실조차 모른 채
    엉뚱한 결과를 받는다. 검출 개수를 담아 두었으므로, 이후 프론트엔드에서
    "얼굴 선택 UI"로 확장할 때 이 예외가 그대로 신호가 된다.
    """

    def __init__(self, face_count: int) -> None:
        self.face_count = face_count
        super().__init__(
            f"얼굴이 {face_count}개 검출되었습니다. 한 명만 나온 사진을 사용해 주세요."
        )


class InsufficientSkinPixelsError(PipelineError):
    """마스킹은 성공했지만 남은 피부 픽셀이 통계적 최소치 미만.

    도메인의 min_reliable_pixels(2,000)는 신뢰도를 '감쇠'시키는 소프트
    기준이고, 이것은 중앙값 통계 자체가 무의미해지는 하드 플로어다.
    극단적 저해상도, 대부분이 가려진 얼굴, 마스킹 실패가 여기 걸린다.
    """

    def __init__(self, pixel_count: int, minimum: int) -> None:
        self.pixel_count = pixel_count
        self.minimum = minimum
        super().__init__(
            f"피부 픽셀이 {pixel_count}개뿐입니다(최소 {minimum}개). "
            "얼굴이 더 크고 선명하게 나온 사진을 사용해 주세요."
        )


class ModelNotFoundError(PipelineError):
    """모델 가중치 파일이 없음 — 배포/환경 구성 문제.

    사용자 입력 문제(위의 예외들)와 서버 구성 문제를 구분하기 위해
    별도 타입으로 둔다. 이 예외는 4xx가 아니라 5xx로 매핑되어야 한다.
    """
