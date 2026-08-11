"use client";

import { useState } from "react";
import { Check } from "lucide-react";
import type { ColorView } from "@/lib/api/types";
import { cn } from "@/lib/utils";

/**
 * 추천/기피 팔레트.
 *
 * 색 데이터는 전부 DB 소유의 큐레이션이다 (ADR-005) — 프론트는 받은
 * hex를 그대로 그린다. 칩을 클릭하면 hex를 클립보드로 복사한다.
 */

function ColorChip({ color, muted = false }: { color: ColorView; muted?: boolean }) {
  const [copied, setCopied] = useState(false);

  return (
    <button
      type="button"
      title={`${color.name} ${color.hex} — 클릭하면 복사`}
      onClick={() => {
        void navigator.clipboard.writeText(color.hex).then(() => {
          setCopied(true);
          setTimeout(() => setCopied(false), 1200);
        });
      }}
      className={cn(
        "group flex flex-col items-center gap-1.5 rounded-lg p-2 transition-colors hover:bg-muted",
        muted && "opacity-70",
      )}
    >
      <span
        className="relative block size-12 rounded-full border shadow-sm sm:size-14"
        style={{ backgroundColor: color.hex }}
      >
        {copied && (
          <span className="absolute inset-0 flex items-center justify-center rounded-full bg-black/40">
            <Check className="size-5 text-white" aria-hidden />
          </span>
        )}
      </span>
      <span className="max-w-16 truncate text-xs">{color.name}</span>
      <span className="text-[10px] text-muted-foreground uppercase tabular-nums">{color.hex}</span>
    </button>
  );
}

interface PaletteSectionProps {
  bestColors: ColorView[];
  worstColors: ColorView[];
}

export function PaletteSection({ bestColors, worstColors }: PaletteSectionProps) {
  return (
    <div className="space-y-6">
      <div>
        <h3 className="mb-2 text-sm font-semibold">잘 어울리는 색</h3>
        <div className="flex flex-wrap gap-1">
          {bestColors.map((color) => (
            <ColorChip key={color.hex} color={color} />
          ))}
        </div>
      </div>
      <div>
        <h3 className="mb-2 text-sm font-semibold text-muted-foreground">피하면 좋은 색</h3>
        <div className="flex flex-wrap gap-1">
          {worstColors.map((color) => (
            <ColorChip key={color.hex} color={color} muted />
          ))}
        </div>
      </div>
    </div>
  );
}
