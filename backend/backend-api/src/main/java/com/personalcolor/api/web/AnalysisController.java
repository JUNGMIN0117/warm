package com.personalcolor.api.web;

import com.personalcolor.api.web.dto.AnalysisDtos;
import com.personalcolor.domain.analysis.AnalysisView;
import com.personalcolor.domain.analysis.AnalyzeImage;
import com.personalcolor.domain.analysis.ImageRejectedException;
import com.personalcolor.domain.analysis.ViewAnalysisHistory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 분석 실행과 이력 조회.
 *
 * <p>유스케이스에만 의존하고 리포지토리를 직접 잡지 않는다. ArchUnit이
 * 이를 검사하며, 실제로 초안에서 {@code SeasonProfileRepository}를 직접
 * 주입했다가 규칙에 걸려 고쳤다.
 */
@RestController
@RequestMapping("/api/v1/analyses")
public class AnalysisController {

    private final AnalyzeImage analyzeImage;
    private final ViewAnalysisHistory history;

    public AnalysisController(AnalyzeImage analyzeImage, ViewAnalysisHistory history) {
        this.analyzeImage = analyzeImage;
        this.history = history;
    }

    /**
     * 사진을 분석한다. <b>로그인 없이 호출할 수 있다.</b>
     *
     * <p>{@code @AuthenticationPrincipal}이 null이면 익명이고, 그 경우 결과는
     * 반환만 되고 저장되지 않는다. 응답의 {@code saved} 필드가 그 사실을
     * 알려주므로 프론트가 "이력에 담겼습니다"를 잘못 안내하지 않는다.
     */
    @PostMapping
    public ResponseEntity<AnalysisDtos.AnalysisResponse> analyze(
            @RequestParam("image") MultipartFile image,
            @RequestParam(name = "includeStages", defaultValue = "false") boolean includeStages,
            @AuthenticationPrincipal UUID userId) {

        AnalysisView view = analyzeImage.execute(
                readBytes(image), Optional.ofNullable(userId), includeStages);

        HttpStatus status = view.record().isAnonymous() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(AnalysisDtos.AnalysisResponse.from(view));
    }

    /** 내 이력 목록. 인증 필요. */
    @GetMapping
    public List<AnalysisDtos.HistoryItem> myHistory(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {

        return history.execute(userId, limit).stream()
                .map(view -> AnalysisDtos.HistoryItem.from(view.record(), view.profile()))
                .toList();
    }

    /**
     * 이력 단건. 인증 필요.
     *
     * <p>남의 분석을 요청하면 403이 아니라 404를 준다. 403은 "그 id는
     * 존재한다"를 알려주는 셈이라, 존재 여부 자체를 숨긴다.
     */
    @GetMapping("/{id}")
    public AnalysisDtos.AnalysisResponse one(
            @PathVariable UUID id, @AuthenticationPrincipal UUID userId) {

        return history.findOwned(id, userId)
                .map(AnalysisDtos.AnalysisResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "분석 결과를 찾을 수 없습니다."));
    }

    /**
     * 업로드 바이트를 읽는다.
     *
     * <p>{@link IOException}을 도메인 예외로 바꾸는 이유: 스트림 읽기 실패는
     * 사용자 입장에서 "업로드가 잘못됐다"이고, 우리가 손쓸 수 있는 서버
     * 장애가 아니다. 500으로 올리면 서킷 브레이커 통계까지 오염된다.
     */
    private static byte[] readBytes(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ImageRejectedException(
                    ImageRejectedException.Reason.IMAGE_DECODE_FAILED,
                    "이미지 파일을 첨부해 주세요.");
        }
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new ImageRejectedException(
                    ImageRejectedException.Reason.IMAGE_DECODE_FAILED,
                    "업로드된 파일을 읽을 수 없습니다. 다시 시도해 주세요.");
        }
    }
}
