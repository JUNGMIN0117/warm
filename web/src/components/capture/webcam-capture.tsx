"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Camera, VideoOff } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * 웹캠 캡처.
 *
 * 미리보기는 거울처럼 좌우 반전(scale-x-[-1])하지만 **캡처 프레임은 반전하지
 * 않는다** — 사용자는 거울상에 익숙해 미리보기가 자연스럽고, 측정은 색 통계라
 * 좌우가 무의미하지만 파이프라인 시각화에서 원본과 어긋나면 혼란스럽다.
 *
 * 스트림 수명: 탭이 보일 때만 카메라를 켠다. 언마운트/전환 시 반드시
 * 트랙을 stop해야 브라우저 카메라 표시등이 꺼진다.
 */

interface WebcamCaptureProps {
  onCapture: (blob: Blob) => void;
  disabled?: boolean;
}

type CameraState = "starting" | "ready" | "denied" | "unavailable";

export function WebcamCapture({ onCapture, disabled = false }: WebcamCaptureProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [state, setState] = useState<CameraState>("starting");

  useEffect(() => {
    let cancelled = false;

    async function start() {
      if (typeof navigator === "undefined" || !navigator.mediaDevices?.getUserMedia) {
        setState("unavailable");
        return;
      }
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 720 } },
          audio: false,
        });
        if (cancelled) {
          stream.getTracks().forEach((t) => t.stop());
          return;
        }
        streamRef.current = stream;
        if (videoRef.current !== null) {
          videoRef.current.srcObject = stream;
        }
        setState("ready");
      } catch {
        if (!cancelled) setState("denied");
      }
    }

    void start();
    return () => {
      cancelled = true;
      streamRef.current?.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    };
  }, []);

  const capture = useCallback(() => {
    const video = videoRef.current;
    if (video === null || video.videoWidth === 0) return;
    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const context = canvas.getContext("2d");
    if (context === null) return;
    context.drawImage(video, 0, 0);
    canvas.toBlob(
      (blob) => {
        if (blob !== null) onCapture(blob);
      },
      "image/jpeg",
      0.92,
    );
  }, [onCapture]);

  if (state === "denied" || state === "unavailable") {
    return (
      <div className="flex flex-col items-center gap-3 rounded-xl border bg-muted/30 px-6 py-14 text-center">
        <VideoOff className="size-9 text-muted-foreground" aria-hidden />
        <p className="font-medium">
          {state === "denied" ? "카메라 권한이 거부되었습니다" : "이 기기에서 카메라를 쓸 수 없습니다"}
        </p>
        <p className="text-sm text-muted-foreground">
          {state === "denied"
            ? "브라우저 주소창의 카메라 아이콘에서 권한을 허용한 뒤 새로고침해 주세요."
            : "파일 업로드 탭을 이용해 주세요."}
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div className="relative overflow-hidden rounded-xl border bg-black">
        {/* 거울상 미리보기 — 캡처 픽셀은 반전하지 않는다 (위 주석 참조) */}
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          className="aspect-[4/3] w-full scale-x-[-1] object-cover"
        />
        {state === "starting" && (
          <div className="absolute inset-0 flex items-center justify-center text-sm text-white/80">
            카메라를 켜는 중…
          </div>
        )}
      </div>
      <Button
        type="button"
        onClick={capture}
        disabled={disabled || state !== "ready"}
        className="w-full"
        size="lg"
      >
        <Camera aria-hidden /> 촬영
      </Button>
    </div>
  );
}
