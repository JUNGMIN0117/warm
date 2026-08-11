import type { Metadata } from "next";
import "pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css";
import "./globals.css";
import { Providers } from "@/components/providers";
import { SiteHeader } from "@/components/layout/site-header";

export const metadata: Metadata = {
  title: "사계 — 사진 한 장으로 알아보는 퍼스널 컬러",
  description:
    "얼굴 사진 한 장으로 봄웜·여름쿨·가을웜·겨울쿨을 판정하고, 판정 근거를 수치로 보여주는 퍼스널 컬러 분석. 원본 사진은 저장하지 않습니다.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body className="min-h-dvh antialiased">
        <Providers>
          <SiteHeader />
          <main className="mx-auto w-full max-w-5xl px-4 py-8">{children}</main>
          <footer className="border-t py-6">
            <p className="mx-auto max-w-5xl px-4 text-center text-xs leading-relaxed text-muted-foreground">
              <span className="font-medium">사계</span>의 분석 결과는 참고용 재미 지표이며
              의료·피부과적 진단이 아닙니다. 조명·화장·카메라에 따라 결과가 달라질 수 있습니다.
              원본 사진은 서버에 저장되지 않습니다.
            </p>
          </footer>
        </Providers>
      </body>
    </html>
  );
}
