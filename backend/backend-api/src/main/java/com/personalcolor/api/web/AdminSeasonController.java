package com.personalcolor.api.web;

import com.personalcolor.api.web.dto.AdminDtos;
import com.personalcolor.api.web.dto.AnalysisDtos;
import com.personalcolor.domain.season.Season;
import com.personalcolor.domain.season.SeasonProfile;
import com.personalcolor.domain.season.UpdateSeasonCuration;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 큐레이션 편집 — ADR-005("큐레이션은 재배포 없이 갱신")의 실현.
 *
 * <p>인가는 SecurityConfig의 {@code /api/v1/admin/** → hasRole(ADMIN)}이
 * 맡는다. 컨트롤러에는 권한 검사 코드가 없다 — 한 곳(시큐리티 규칙)에서
 * 선언되고 테스트가 그것을 고정한다.
 *
 * <p>분석 응답 캐시는 무효화할 필요가 없다. Redis에 캐시되는 것은
 * ml-service의 <b>측정 결과</b>뿐이고 큐레이션은 응답 조립 시점에 DB에서
 * 조인된다(ADR-005의 경계가 여기서 값을 한다) — 편집 즉시 반영된다.
 */
@RestController
@RequestMapping("/api/v1/admin/seasons")
public class AdminSeasonController {

    private final UpdateSeasonCuration updateSeasonCuration;

    public AdminSeasonController(UpdateSeasonCuration updateSeasonCuration) {
        this.updateSeasonCuration = updateSeasonCuration;
    }

    /** 큐레이션 통째 교체. 응답은 공개 조회와 같은 SeasonView — 편집 결과 확인용. */
    @PutMapping("/{code}")
    public AnalysisDtos.SeasonView update(
            @PathVariable String code,
            @Valid @RequestBody AdminDtos.CurationUpdateRequest request) {
        SeasonProfile updated = updateSeasonCuration.execute(
                Season.fromCode(code), request.toCommand());
        return AnalysisDtos.SeasonView.from(updated);
    }
}
