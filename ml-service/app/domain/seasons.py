"""4계절 퍼스널 컬러 타입 정의와 추천 팔레트.

퍼스널 컬러 이론은 피부 언더톤을 두 축으로 나눈다.

              Warm (황기)          Cool (청기)
    Light   │  🌸 봄 웜          │  ☀️ 여름 쿨
    ────────┼───────────────────┼──────────────────
    Deep    │  🍂 가을 웜        │  ❄️ 겨울 쿨

가로축(언더톤)은 CIELab 색상각 h°가, 세로축(명도·선명도)은 ITA°와 C*가
결정한다. 같은 웜톤 안에서도 봄은 밝고 맑게(clear), 가을은 깊고 묵직하게
(deep) 갈린다는 점이 4분류의 핵심이다.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum


class Undertone(str, Enum):
    """언더톤 — 4계절 분류의 1차 축."""

    WARM = "warm"
    COOL = "cool"


class Season(str, Enum):
    """4계절 퍼스널 컬러 타입."""

    SPRING_WARM = "spring_warm"
    SUMMER_COOL = "summer_cool"
    AUTUMN_WARM = "autumn_warm"
    WINTER_COOL = "winter_cool"

    @property
    def undertone(self) -> Undertone:
        return (
            Undertone.WARM
            if self in (Season.SPRING_WARM, Season.AUTUMN_WARM)
            else Undertone.COOL
        )


@dataclass(frozen=True, slots=True)
class SeasonProfile:
    """한 계절 타입의 전체 프로필. UI가 필요로 하는 모든 표시 정보를 담는다."""

    season: Season
    label_ko: str
    label_en: str
    emoji: str
    keywords: tuple[str, ...]
    description: str
    best_colors: tuple[tuple[str, str], ...] = field(default=())
    """추천 색상 (이름, HEX) 쌍."""
    worst_colors: tuple[tuple[str, str], ...] = field(default=())
    """피해야 할 색상 (이름, HEX) 쌍."""
    styling_tips: tuple[str, ...] = field(default=())


SEASON_PROFILES: dict[Season, SeasonProfile] = {
    Season.SPRING_WARM: SeasonProfile(
        season=Season.SPRING_WARM,
        label_ko="봄 웜",
        label_en="Spring Warm",
        emoji="🌸",
        keywords=("밝은", "따뜻한", "생기있는", "선명한"),
        description=(
            "노란기가 도는 밝고 맑은 피부톤입니다. 채도가 높고 투명한 색이 "
            "얼굴에 생기를 더하며, 탁하거나 무거운 색은 혈색을 눌러버립니다."
        ),
        best_colors=(
            ("코랄", "#FF7F50"),
            ("피치", "#FFCBA4"),
            ("아이보리", "#FFF8E7"),
            ("라이트 옐로우그린", "#C5E384"),
            ("살몬 핑크", "#FF91A4"),
            ("카멜", "#C19A6B"),
            ("터콰이즈", "#40E0D0"),
            ("골든 옐로우", "#FFD34E"),
        ),
        worst_colors=(
            ("차콜 그레이", "#36454F"),
            ("퓨어 블랙", "#000000"),
            ("딥 버건디", "#5C1F33"),
            ("더스티 모브", "#8B7B8B"),
        ),
        styling_tips=(
            "골드 계열 액세서리가 피부 광택과 어울립니다.",
            "블랙 대신 다크 브라운이나 네이비로 무게를 잡으세요.",
            "메이크업은 코랄·피치 계열 블러셔가 안전합니다.",
        ),
    ),
    Season.SUMMER_COOL: SeasonProfile(
        season=Season.SUMMER_COOL,
        label_ko="여름 쿨",
        label_en="Summer Cool",
        emoji="☀️",
        keywords=("밝은", "차가운", "부드러운", "은은한"),
        description=(
            "푸른기가 도는 밝고 부드러운 피부톤입니다. 채도를 한 톤 낮춘 "
            "뮤트한 색이 가장 잘 맞고, 쨍한 원색은 얼굴보다 옷이 먼저 보입니다."
        ),
        best_colors=(
            ("라벤더", "#B57EDC"),
            ("파우더 블루", "#B0C4DE"),
            ("로즈 핑크", "#E8A5B8"),
            ("소프트 그레이", "#B8B8C0"),
            ("민트", "#A8D8C8"),
            ("스카이 블루", "#87CEEB"),
            ("더스티 로즈", "#C4A0A8"),
            ("페일 퍼플", "#D8BFD8"),
        ),
        worst_colors=(
            ("오렌지", "#FF7518"),
            ("머스타드", "#E1AD01"),
            ("카멜", "#C19A6B"),
            ("골드", "#D4AF37"),
        ),
        styling_tips=(
            "실버·화이트골드 액세서리를 권합니다.",
            "톤온톤 배색이 가장 자연스럽습니다.",
            "메이크업은 로즈·핑크 계열로 통일하세요.",
        ),
    ),
    Season.AUTUMN_WARM: SeasonProfile(
        season=Season.AUTUMN_WARM,
        label_ko="가을 웜",
        label_en="Autumn Warm",
        emoji="🍂",
        keywords=("깊은", "따뜻한", "차분한", "고급스러운"),
        description=(
            "노란기가 도는 깊고 차분한 피부톤입니다. 채도를 낮춘 어스 톤이 "
            "피부의 깊이를 살리며, 밝은 파스텔은 얼굴을 창백하게 만듭니다."
        ),
        best_colors=(
            ("머스타드", "#D4A017"),
            ("카키", "#78866B"),
            ("테라코타", "#C96A50"),
            ("브릭 레드", "#9C3527"),
            ("올리브", "#6B7B3A"),
            ("초콜릿 브라운", "#5D4037"),
            ("딥 오렌지", "#CC5500"),
            ("캐멀 베이지", "#A9825C"),
        ),
        worst_colors=(
            ("아이시 핑크", "#FFD1DC"),
            ("퓨어 화이트", "#FFFFFF"),
            ("실버", "#C0C0C0"),
            ("페일 라벤더", "#E6E6FA"),
        ),
        styling_tips=(
            "앤티크 골드·브론즈 액세서리가 잘 어울립니다.",
            "화이트가 필요하면 오프화이트나 크림으로 대체하세요.",
            "메이크업은 벽돌빛 브릭·브라운 계열을 권합니다.",
        ),
    ),
    Season.WINTER_COOL: SeasonProfile(
        season=Season.WINTER_COOL,
        label_ko="겨울 쿨",
        label_en="Winter Cool",
        emoji="❄️",
        keywords=("선명한", "차가운", "대비가 강한", "도회적인"),
        description=(
            "푸른기가 도는 깊고 선명한 피부톤입니다. 명암 대비가 강한 배색이 "
            "이목구비를 또렷하게 만들고, 탁한 중간톤은 얼굴을 흐리게 합니다."
        ),
        best_colors=(
            ("퓨어 화이트", "#FFFFFF"),
            ("트루 블랙", "#000000"),
            ("로열 블루", "#1F4FD8"),
            ("마젠타", "#D6006E"),
            ("에메랄드", "#00926C"),
            ("버건디", "#7B1E3A"),
            ("실버", "#C0C0C0"),
            ("아이시 핑크", "#FFD1DC"),
        ),
        worst_colors=(
            ("베이지", "#E8DCC4"),
            ("카멜", "#C19A6B"),
            ("머스타드", "#E1AD01"),
            ("올리브 브라운", "#6B5B3A"),
        ),
        styling_tips=(
            "실버·플래티넘 액세서리가 가장 잘 맞습니다.",
            "블랙&화이트 대비 배색을 적극 활용하세요.",
            "메이크업은 레드·플럼 계열로 또렷하게 마무리합니다.",
        ),
    ),
}


def get_profile(season: Season) -> SeasonProfile:
    """계절 타입에 해당하는 프로필을 반환한다."""
    return SEASON_PROFILES[season]
