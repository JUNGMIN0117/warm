"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import { RotateCcw, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ImageDropzone } from "./image-dropzone";
import { WebcamCapture } from "./webcam-capture";

/**
 * 사진 입력 단계 전체: 업로드/웹캠 탭 → 미리보기 → 분석 시작.
 *
 * 선택 즉시 분석하지 않고 미리보기를 거치는 이유: 웹캠 캡처는 흔들리거나
 * 눈을 감은 프레임이 잡히기 쉽고, 분석은 몇 초가 걸리는 작업이라
 * "이 사진으로 갈지"를 사용자가 확정하는 단계가 있어야 재시도 루프가 짧다.
 */

interface ImagePickerProps {
  onAnalyze: (image: Blob) => void;
  disabled?: boolean;
}

export function ImagePicker({ onAnalyze, disabled = false }: ImagePickerProps) {
  const [selected, setSelected] = useState<Blob | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  // ObjectURL은 만든 쪽이 해제해야 메모리 누수가 없다.
  useEffect(() => {
    if (selected === null) {
      setPreviewUrl(null);
      return;
    }
    const url = URL.createObjectURL(selected);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [selected]);

  if (selected !== null && previewUrl !== null) {
    return (
      <div className="space-y-4">
        <div className="overflow-hidden rounded-xl border">
          {/* data/blob URL이라 next/image 최적화 대상이 아니다 — unoptimized로 그대로 표시 */}
          <Image
            src={previewUrl}
            alt="분석할 사진 미리보기"
            width={640}
            height={480}
            unoptimized
            className="max-h-[420px] w-full object-contain"
          />
        </div>
        <div className="flex gap-3">
          <Button
            type="button"
            variant="outline"
            className="flex-1"
            size="lg"
            disabled={disabled}
            onClick={() => setSelected(null)}
          >
            <RotateCcw aria-hidden /> 다시 선택
          </Button>
          <Button
            type="button"
            className="bg-gradient-brand flex-1 border-0 text-white hover:opacity-90"
            size="lg"
            disabled={disabled}
            onClick={() => onAnalyze(selected)}
          >
            <Sparkles aria-hidden /> 분석 시작
          </Button>
        </div>
      </div>
    );
  }

  return (
    <Tabs defaultValue="upload">
      <TabsList className="w-full">
        <TabsTrigger value="upload" className="flex-1">
          파일 업로드
        </TabsTrigger>
        <TabsTrigger value="webcam" className="flex-1">
          웹캠 촬영
        </TabsTrigger>
      </TabsList>
      <TabsContent value="upload">
        <ImageDropzone onSelect={setSelected} disabled={disabled} />
      </TabsContent>
      <TabsContent value="webcam">
        <WebcamCapture onCapture={setSelected} disabled={disabled} />
      </TabsContent>
    </Tabs>
  );
}
