import { describe, expect, it } from "vitest";

import { MAX_FILE_BYTES, validateImageFile } from "./dropzone";

/**
 * 클라이언트 파일 검증 — 서버 계약(12MB, JPEG/PNG)과 같은 기준을 쓴다.
 * 목적은 계약 자체가 아니라 "올려봐야 거절될 업로드"의 시간 낭비 방지다.
 */

function fakeFile(type: string, size: number): File {
  const file = new File([""], "photo.jpg", { type });
  // File 생성자는 내용으로 크기를 정하므로 큰 파일은 size를 직접 재정의한다.
  Object.defineProperty(file, "size", { value: size });
  return file;
}

describe("validateImageFile", () => {
  it("JPEG와 PNG는 통과한다", () => {
    expect(validateImageFile(fakeFile("image/jpeg", 1024))).toBeNull();
    expect(validateImageFile(fakeFile("image/png", 1024))).toBeNull();
  });

  it("다른 형식(webp, gif, pdf)은 거절한다", () => {
    for (const type of ["image/webp", "image/gif", "application/pdf"]) {
      expect(validateImageFile(fakeFile(type, 1024))).not.toBeNull();
    }
  });

  it("12MB 초과는 거절한다 — 서버 FILE_TOO_LARGE와 같은 경계", () => {
    expect(validateImageFile(fakeFile("image/jpeg", MAX_FILE_BYTES + 1))).not.toBeNull();
    expect(validateImageFile(fakeFile("image/jpeg", MAX_FILE_BYTES))).toBeNull();
  });

  it("빈 파일은 거절한다", () => {
    expect(validateImageFile(fakeFile("image/jpeg", 0))).not.toBeNull();
  });
});
