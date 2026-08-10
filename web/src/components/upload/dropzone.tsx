"use client";

import { useCallback, useRef, useState } from "react";

import { cn } from "@/lib/utils";

/** 서버의 FILE_TOO_LARGE 기준(12MB)과 같은 값 — 올려봐야 거절될 파일은 여기서 거른다. */
export const MAX_FILE_BYTES = 12 * 1024 * 1024;

const ACCEPTED_TYPES = new Set(["image/jpeg", "image/png"]);

/**
 * 파일 검증. 실패 사유를 문자열로 돌려준다 (null이면 통과).
 * 서버와 중복 검증이지만 목적이 다르다 — 서버는 계약을 지키고,
 * 프론트는 12MB 업로드가 끝난 뒤에야 거절당하는 시간 낭비를 막는다.
 */
export function validateImageFile(file: File): string | null {
  if (!ACCEPTED_TYPES.has(file.type)) {
    return "JPEG 또는 PNG 사진만 분석할 수 있습니다.";
  }
  if (file.size > MAX_FILE_BYTES) {
    return "12MB 이하의 사진만 올릴 수 있습니다.";
  }
  if (file.size === 0) {
    return "빈 파일입니다. 다른 사진을 선택해 주세요.";
  }
  return null;
}

interface DropzoneProps {
  onImage: (file: File) => void;
}

export function Dropzone({ onImage }: DropzoneProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [rejection, setRejection] = useState<string | null>(null);

  const handleFile = useCallback(
    (file: File | undefined) => {
      if (!file) return;
      const problem = validateImageFile(file);
      if (problem) {
        setRejection(problem);
        return;
      }
      setRejection(null);
      onImage(file);
    },
    [onImage],
  );

  return (
    <div className="flex w-full max-w-md flex-col items-center gap-2">
      <button
        type="button"
        aria-label="사진 선택 또는 끌어다 놓기"
        className={cn(
          "flex h-52 w-full cursor-pointer flex-col items-center justify-center gap-2",
          "rounded-2xl border-2 border-dashed transition-colors",
          dragOver
            ? "border-primary bg-primary/5"
            : "border-muted-foreground/30 hover:border-primary/60",
        )}
        onClick={() => inputRef.current?.click()}
        onDragOver={(e) => {
          e.preventDefault();
          setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragOver(false);
          handleFile(e.dataTransfer.files[0]);
        }}
      >
        <span className="text-4xl" aria-hidden>
          🎨
        </span>
        <span className="font-medium">사진을 끌어다 놓거나 클릭해서 선택</span>
        <span className="text-xs text-muted-foreground">
          정면, 자연광, 민낯에 가까울수록 정확합니다
        </span>
      </button>

      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png"
        className="hidden"
        onChange={(e) => {
          handleFile(e.target.files?.[0]);
          // 같은 파일을 다시 선택해도 change가 뜨도록 초기화
          e.target.value = "";
        }}
      />

      {rejection && (
        <p role="alert" className="text-sm text-destructive">
          {rejection}
        </p>
      )}
    </div>
  );
}
