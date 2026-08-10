import type { Metadata } from "next";
import { Geist_Mono, Noto_Sans_KR } from "next/font/google";

import { Header } from "@/components/layout/header";
import { Toaster } from "@/components/ui/sonner";

import { Providers } from "./providers";
import "./globals.css";

// 한국어 UI가 주이므로 본문은 Noto Sans KR. 측정 수치(h°, ITA° 등)는
// 표 정렬이 중요해 모노스페이스를 유지한다.
const notoSansKr = Noto_Sans_KR({
  variable: "--font-sans",
  subsets: ["latin"],
  weight: ["400", "500", "700"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Personal Color AI",
  description:
    "얼굴 사진 한 장으로 4계절 퍼스널 컬러를 판정하고, 판정 근거를 수치로 보여줍니다.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body className={`${notoSansKr.variable} ${geistMono.variable} antialiased`}>
        <Providers>
          <Header />
          <main className="mx-auto w-full max-w-5xl px-4 pb-24 pt-8">{children}</main>
          <footer className="border-t py-6 text-center text-xs text-muted-foreground">
            {/* 도메인 판정을 의료·피부과 조언처럼 표현하지 않는다 — 프로젝트 금지 사항 */}
            퍼스널 컬러 판정은 참고용 재미 지표입니다. 의료적·피부과적 판단이 아닙니다.
            업로드한 사진은 저장되지 않습니다.
          </footer>
          <Toaster />
        </Providers>
      </body>
    </html>
  );
}
