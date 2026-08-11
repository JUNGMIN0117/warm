/**
 * 업로드 전 클라이언트 축소.
 *
 * 서버(ml-service)도 최대 변 1600px로 줄이므로 그보다 큰 픽셀은 업로드
 * 대역폭 낭비이고, 스마트폰 원본(수 MB~12MB 초과)이 413으로 거절되는
 * 것을 미리 막는다. 색 통계가 목적이므로 JPEG 품질 0.92면 충분하다 —
 * 서버 파이프라인이 어차피 면적 평균으로 다시 줄인다.
 *
 * EXIF 방향은 createImageBitmap의 imageOrientation: "from-image"가 적용해
 * 준다. canvas 재인코딩으로 EXIF는 사라지지만 픽셀이 이미 회전된 뒤라
 * 서버의 EXIF 보정 단계는 그냥 무해하게 지나간다.
 */

const MAX_EDGE = 1600;
const JPEG_QUALITY = 0.92;

export async function downscaleForUpload(file: Blob): Promise<Blob> {
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch {
    // 디코드 불가(손상 파일 등)면 원본을 그대로 보낸다 — 서버가
    // IMAGE_DECODE_FAILED로 정확한 안내를 준다. 여기서 삼키면 안 된다.
    return file;
  }

  const { width, height } = bitmap;
  const longEdge = Math.max(width, height);
  if (longEdge <= MAX_EDGE) {
    bitmap.close();
    return file;
  }

  const scale = MAX_EDGE / longEdge;
  const targetWidth = Math.round(width * scale);
  const targetHeight = Math.round(height * scale);

  const canvas = document.createElement("canvas");
  canvas.width = targetWidth;
  canvas.height = targetHeight;
  const context = canvas.getContext("2d");
  if (context === null) {
    bitmap.close();
    return file;
  }
  context.drawImage(bitmap, 0, 0, targetWidth, targetHeight);
  bitmap.close();

  const blob = await new Promise<Blob | null>((resolve) =>
    canvas.toBlob(resolve, "image/jpeg", JPEG_QUALITY),
  );
  return blob ?? file;
}
