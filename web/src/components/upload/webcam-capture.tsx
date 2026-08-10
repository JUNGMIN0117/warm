"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";

interface WebcamCaptureProps {
  onImage: (blob: Blob) => void;
}

/**
 * 웹캠 캡처.
 *
 * 스트림은 다이얼로그가 열려 있는 동안만 산다 — 닫히면 즉시 모든 트랙을
 * 멈춘다. 카메라 표시등이 계속 켜져 있는 것만큼 신뢰를 깎는 UI는 없다.
 * 캡처는 canvas를 거쳐 JPEG Blob으로 만들어 파일 업로드와 같은 경로를 탄다.
 */
export function WebcamCapture({ onImage }: WebcamCaptureProps) {
  const [open, setOpen] = useState(false);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);

  const stopStream = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
  }, []);

  useEffect(() => {
    if (!open) {
      stopStream();
      setCameraError(null);
      return;
    }

    let cancelled = false;
    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: "user", width: 1280 }, audio: false })
      .then((stream) => {
        if (cancelled) {
          stream.getTracks().forEach((t) => t.stop());
          return;
        }
        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
        }
      })
      .catch(() => {
        if (!cancelled) {
          setCameraError(
            "카메라를 열 수 없습니다. 브라우저 권한을 확인하거나 파일 업로드를 이용해 주세요.",
          );
        }
      });

    return () => {
      cancelled = true;
      stopStream();
    };
  }, [open, stopStream]);

  const capture = () => {
    const video = videoRef.current;
    if (!video || video.videoWidth === 0) return;

    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    canvas.getContext("2d")?.drawImage(video, 0, 0);
    canvas.toBlob(
      (blob) => {
        if (blob) {
          setOpen(false);
          onImage(blob);
        }
      },
      "image/jpeg",
      0.92,
    );
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={<Button variant="outline" size="sm" />}>
        웹캠으로 촬영
      </DialogTrigger>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>웹캠 촬영</DialogTitle>
          <DialogDescription>
            정면을 보고, 얼굴에 그림자가 지지 않는 위치에서 촬영하세요. 영상은
            브라우저 안에서만 처리되고 촬영 버튼을 누른 한 장만 전송됩니다.
          </DialogDescription>
        </DialogHeader>

        {cameraError ? (
          <p role="alert" className="py-8 text-center text-sm text-destructive">
            {cameraError}
          </p>
        ) : (
          <>
            {/* 거울 모드: 사용자는 좌우 반전된 자기 모습에 익숙하다.
                반전은 표시에만 적용한다 — 전송되는 픽셀은 원본이므로 측정에 영향이 없다. */}
            <video
              ref={videoRef}
              autoPlay
              playsInline
              muted
              className="aspect-[4/3] w-full -scale-x-100 rounded-lg bg-muted object-cover"
            />
            <Button onClick={capture} className="w-full">
              이 장면으로 분석
            </Button>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
