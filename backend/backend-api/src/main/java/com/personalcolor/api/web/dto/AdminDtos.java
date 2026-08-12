package com.personalcolor.api.web.dto;

import com.personalcolor.domain.analysis.RgbColor;
import com.personalcolor.domain.season.SeasonProfile;
import com.personalcolor.domain.season.UpdateSeasonCuration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 관리자 API 요청.
 *
 * <p>형식 검증(hex 패턴, 빈 값)은 여기서 400으로 빠르게 거절하고,
 * 큐레이션 규칙(추천 6개+, 기피 3개+)은 도메인이 지킨다 — AuthDtos와
 * 같은 이중 검증 구조다.
 */
public final class AdminDtos {

    private AdminDtos() {}

    /** 팔레트 한 칸 입력. */
    public record ColorInput(
            @NotBlank(message = "색 이름을 입력해 주세요.")
            @Size(max = 40, message = "색 이름은 40자 이하여야 합니다.")
            String name,

            @NotBlank(message = "hex 값을 입력해 주세요.")
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "hex는 #RRGGBB 형식이어야 합니다.")
            String hex) {

        SeasonProfile.PaletteColor toDomain() {
            return new SeasonProfile.PaletteColor(name, RgbColor.fromHex(hex));
        }
    }

    /** 큐레이션 교체 요청 — 편집 가능한 필드 전부를 통째로 보낸다. */
    public record CurationUpdateRequest(
            @NotEmpty(message = "키워드를 1개 이상 입력해 주세요.")
            List<@NotBlank(message = "빈 키워드는 넣을 수 없습니다.") String> keywords,

            @NotBlank(message = "설명을 입력해 주세요.")
            String description,

            @NotEmpty(message = "추천 색을 입력해 주세요.")
            List<@Valid ColorInput> bestColors,

            @NotEmpty(message = "기피 색을 입력해 주세요.")
            List<@Valid ColorInput> worstColors,

            @NotEmpty(message = "스타일링 팁을 1개 이상 입력해 주세요.")
            List<@NotBlank(message = "빈 팁은 넣을 수 없습니다.") String> stylingTips) {

        public UpdateSeasonCuration.Command toCommand() {
            return new UpdateSeasonCuration.Command(
                    keywords,
                    description,
                    bestColors.stream().map(ColorInput::toDomain).toList(),
                    worstColors.stream().map(ColorInput::toDomain).toList(),
                    stylingTips);
        }
    }
}
