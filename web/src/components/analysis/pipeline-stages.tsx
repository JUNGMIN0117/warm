"use client";

import { useState } from "react";
import Image from "next/image";
import { AnimatePresence, motion } from "motion/react";
import { ChevronDown } from "lucide-react";
import type { PreprocessingView, StagesView } from "@/lib/api/types";
import { cn } from "@/lib/utils";

/**
 * 접이식 파이프라인 시각화 (하이브리드 시각화의 뒷부분).
 *
 * "우리가 사진을 얼마나 건드렸는지"를 공개하는 화면이다. 단계 이미지
 * 다섯 장과 보정 수치(WB 게인, 캐스트 강도, 마스크 커버리지)를 함께
 * 보여준다 — 보정하고 침묵하는 것보다 보정량을 밝히는 편이 이 프로젝트의
 * 태도에 맞다 (docs/05-api-spec.md §3).
 */

const STAGE_LABELS: { key: keyof StagesView; title: string; caption: string }[] = [
  { key: "original", title: "① 원본", caption: "EXIF 회전 보정 직후" },
  { key: "whiteBalanced", title: "② 화이트밸런스", caption: "얼굴 제외 배경으로 조명 추정" },
  { key: "faceCrop", title: "③ 얼굴 크롭", caption: "검출된 얼굴 영역" },
  { key: "skinMask", title: "④ 피부 마스크", caption: "눈·눈썹·입술 제외 (무손실)" },
  { key: "measuredPixels", title: "⑤ 측정 픽셀", caption: "실제 색 통계에 쓰인 픽셀" },
];

interface PipelineStagesProps {
  stages: StagesView;
  preprocessing: PreprocessingView;
}

export function PipelineStages({ stages, preprocessing }: PipelineStagesProps) {
  const [open, setOpen] = useState(false);

  return (
    <div className="rounded-xl border">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex w-full items-center justify-between px-4 py-3 text-sm font-medium hover:bg-muted/50"
      >
        <span>분석 과정 자세히 보기 — 사진이 어떻게 처리됐나</span>
        <ChevronDown className={cn("size-4 transition-transform", open && "rotate-180")} aria-hidden />
      </button>
      <AnimatePresence initial={false}>
        {open && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.25 }}
            className="overflow-hidden"
          >
            <div className="space-y-5 border-t px-4 py-4">
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
                {STAGE_LABELS.map(({ key, title, caption }) => (
                  <figure key={key} className="space-y-1.5">
                    <Image
                      src={stages[key]}
                      alt={title}
                      width={256}
                      height={256}
                      unoptimized
                      className="aspect-square w-full rounded-lg border object-cover"
                    />
                    <figcaption>
                      <p className="text-xs font-medium">{title}</p>
                      <p className="text-[11px] text-muted-foreground">{caption}</p>
                    </figcaption>
                  </figure>
                ))}
              </div>
              <dl className="grid grid-cols-2 gap-x-6 gap-y-2 rounded-lg bg-muted/40 p-3 text-xs sm:grid-cols-4">
                <div>
                  <dt className="text-muted-foreground">화이트밸런스</dt>
                  <dd className="font-medium">{preprocessing.whiteBalanceMethod}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">RGB 게인</dt>
                  <dd className="font-medium tabular-nums">
                    {preprocessing.gains.map((g) => g.toFixed(3)).join(" / ")}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">색 편향 강도</dt>
                  <dd className="font-medium tabular-nums">{preprocessing.castStrength.toFixed(4)}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">마스크 커버리지</dt>
                  <dd className="font-medium tabular-nums">
                    {(preprocessing.maskCoverageRatio * 100).toFixed(1)}%
                  </dd>
                </div>
              </dl>
              <p className="text-[11px] leading-relaxed text-muted-foreground">
                게인이 1.0에 가까울수록 조명 보정이 거의 없었다는 뜻입니다. 마스크 커버리지는
                얼굴 영역 중 피부로 판정된 비율로, 낮으면 머리카락·그림자 등이 많이 제외된
                것입니다.
              </p>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
