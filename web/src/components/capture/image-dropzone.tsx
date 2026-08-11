"use client";

import { useCallback, useRef, useState } from "react";
import { ImagePlus } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * 파일 선택 + 드래그앤드롭.
 *
 * 파일을 고르는 즉시 부모에게 넘긴다 — 미리보기·분석 시작은 부모(ImagePicker)의
 * 책임이다. 이 컴포넌트는 "이미지 파일 하나를 얻는다"만 안다.
 */

interface ImageDropzoneProps {
  onSelect: (file: File) => void;
  disabled?: boolean;
}

export function ImageDropzone({ onSelect, disabled = false }: ImageDropzoneProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);

  const handleFiles = useCallback(
    (files: FileList | null) => {
      if (files === null || files.length === 0) return;
      const file = files[0];
      // 형식 검증은 서버가 최종 권위다(IMAGE_DECODE_FAILED). 여기서는
      // 명백히 이미지가 아닌 것만 미리 거른다.
      if (!file.type.startsWith("image/")) return;
      onSelect(file);
    },
    [onSelect],
  );

  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => inputRef.current?.click()}
      onDragOver={(e) => {
        e.preventDefault();
        setDragOver(true);
      }}
      onDragLeave={() => setDragOver(false)}
      onDrop={(e) => {
        e.preventDefault();
        setDragOver(false);
        handleFiles(e.dataTransfer.files);
      }}
      className={cn(
        "flex w-full flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed",
        "border-muted-foreground/25 bg-gradient-to-br from-rose-50/40 via-transparent to-indigo-50/40",
        "px-6 py-14 text-center transition-colors",
        "dark:from-rose-950/15 dark:to-indigo-950/15",
        "hover:border-primary/50 hover:from-rose-50/70 hover:to-indigo-50/70",
        "dark:hover:from-rose-950/30 dark:hover:to-indigo-950/30 focus-visible:outline-2",
        dragOver && "border-primary from-rose-50 to-indigo-50",
        disabled && "pointer-events-none opacity-50",
      )}
    >
      <span className="bg-gradient-brand flex size-12 items-center justify-center rounded-full">
        <ImagePlus className="size-6 text-white" aria-hidden />
      </span>
      <div className="space-y-1">
        <p className="font-medium">사진을 끌어다 놓거나 클릭해서 선택</p>
        <p className="text-sm text-muted-foreground">
          정면 얼굴이 나온 JPEG·PNG · 자연광에서 찍은 사진일수록 정확합니다
        </p>
      </div>
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        className="hidden"
        onChange={(e) => {
          handleFiles(e.target.files);
          // 같은 파일을 다시 골라도 change가 뜨도록 초기화한다.
          e.target.value = "";
        }}
      />
    </button>
  );
}
