"use client";

/* eslint-disable @next/next/no-img-element -- base64 data URI는 next/image 최적화 대상이 아니다 */

import { motion } from "motion/react";

import type { PipelineStages } from "@/lib/api/types";

/**
 * 전처리 5단계 시각화 — 이 프론트가 존재하는 이유 중 하나.
 *
 * 원본 프로젝트의 결과는 숫자 하나였고 사용자가 "내 사진이 어떻게
 * 처리됐는지" 알 방법이 없었다. 여기서는 서버가 실제로 거친 다섯 단계를
 * 이미지로 펼쳐, "측정에 쓰인 픽셀이 정확히 무엇인가"까지 공개한다.
 */
interface StageInfo {
  key: keyof PipelineStages;
  title: string;
  why: string;
}

const STAGES: StageInfo[] = [
  {
    key: "original",
    title: "원본",
    why: "EXIF 회전만 보정한 입력",
  },
  {
    key: "whiteBalanced",
    title: "화이트밸런스",
    why: "얼굴을 제외한 배경으로 조명을 추정해 색 왜곡 제거",
  },
  {
    key: "faceCrop",
    title: "얼굴 영역",
    why: "배경이 측정에 섞이지 않도록 크롭",
  },
  {
    key: "skinMask",
    title: "피부 마스크",
    why: "눈·눈썹·입술을 랜드마크로 제외한 측정 범위",
  },
  {
    key: "measuredPixels",
    title: "측정 픽셀",
    why: "실제 색채 통계에 들어간 픽셀 전부",
  },
];

export function StageStrip({ stages }: { stages: PipelineStages }) {
  return (
    <section aria-label="전처리 단계">
      <h2 className="mb-1 text-lg font-bold">사진이 거친 다섯 단계</h2>
      <p className="mb-4 text-sm text-muted-foreground">
        판정에 쓰인 것은 마지막 &lsquo;측정 픽셀&rsquo;의 색뿐입니다 — 얼굴형이나
        배경은 결과에 들어갈 수 없습니다.
      </p>
      <ol className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {STAGES.map((stage, i) => (
          <motion.li
            key={stage.key}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.12 }}
            className="flex flex-col gap-1.5"
          >
            <img
              src={stages[stage.key]}
              alt={`${stage.title} 단계 이미지`}
              className="aspect-square w-full rounded-lg border object-cover"
            />
            <p className="text-sm font-medium">
              {i + 1}. {stage.title}
            </p>
            <p className="text-xs leading-relaxed text-muted-foreground">{stage.why}</p>
          </motion.li>
        ))}
      </ol>
    </section>
  );
}
