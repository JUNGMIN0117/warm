package com.personalcolor.api.web;

import com.personalcolor.api.web.dto.AnalysisDtos;
import com.personalcolor.domain.season.BrowseSeasonCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 계절 카탈로그 조회.
 *
 * <p>분석 없이도 팔레트를 둘러볼 수 있게 공개한다. "내 계절이 뭔지는
 * 모르지만 가을 웜 팔레트가 궁금하다"가 흔한 요구이고, 이걸 막을 이유가 없다.
 */
@RestController
@RequestMapping("/api/v1/seasons")
public class SeasonController {

    private final BrowseSeasonCatalog catalog;

    public SeasonController(BrowseSeasonCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<AnalysisDtos.SeasonView> all() {
        return catalog.all().stream().map(AnalysisDtos.SeasonView::from).toList();
    }

    /**
     * 계절 하나.
     *
     * @param code spring_warm | summer_cool | autumn_warm | winter_cool.
     *     모르는 코드는 IllegalArgumentException이 되고 전역 핸들러가 400으로 바꾼다
     */
    @GetMapping("/{code}")
    public AnalysisDtos.SeasonView one(@PathVariable String code) {
        return AnalysisDtos.SeasonView.from(catalog.byCode(code));
    }
}
