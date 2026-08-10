"use client";

import { useState } from "react";
import { toast } from "sonner";

import type { PaletteColor } from "@/lib/api/types";

/**
 * 추천/기피 팔레트. 색은 서버(DB 큐레이션)가 소유하고 여기는 표시만 한다.
 * 칩을 클릭하면 HEX가 복사된다 — 쇼핑몰 검색 등에 바로 쓰라는 의도.
 */
export function PaletteGrid({
  title,
  colors,
  tone,
}: {
  title: string;
  colors: PaletteColor[];
  tone: "best" | "worst";
}) {
  return (
    <div>
      <h3 className="mb-2 text-sm font-medium text-muted-foreground">{title}</h3>
      <ul className="grid grid-cols-4 gap-2 sm:grid-cols-6">
        {colors.map((color) => (
          <ColorChip key={color.hex} color={color} dimmed={tone === "worst"} />
        ))}
      </ul>
    </div>
  );
}

function ColorChip({ color, dimmed }: { color: PaletteColor; dimmed: boolean }) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(color.hex);
      setCopied(true);
      toast(`${color.name} ${color.hex} 복사됨`);
      setTimeout(() => setCopied(false), 1200);
    } catch {
      // 클립보드 권한이 없으면 조용히 무시 — 복사는 부가 기능이다.
    }
  };

  return (
    <li>
      <button
        type="button"
        onClick={copy}
        title={`${color.name} ${color.hex} — 클릭하면 복사`}
        className="group flex w-full flex-col items-center gap-1"
      >
        <span
          className={`block h-12 w-full rounded-lg border transition-transform group-hover:scale-105 ${
            dimmed ? "opacity-60" : ""
          }`}
          style={{ backgroundColor: color.hex }}
          aria-hidden
        />
        <span className="max-w-full truncate text-xs text-muted-foreground">
          {copied ? "복사됨!" : color.name}
        </span>
      </button>
    </li>
  );
}
