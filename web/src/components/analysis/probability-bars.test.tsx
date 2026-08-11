import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { ProbabilityBars } from "./probability-bars";
import type { SeasonCode } from "@/lib/api/types";

/**
 * 확률 분포 표시 계약.
 *
 * 도메인 불변식 4(확률 분포 전체 반환)의 UI 측 대응 — 네 계절이 전부,
 * 확률 내림차순으로 보여야 한다.
 */
describe("ProbabilityBars", () => {
  const probabilities: Record<SeasonCode, number> = {
    spring_warm: 0.132,
    summer_cool: 0.004,
    autumn_warm: 0.822,
    winter_cool: 0.042,
  };

  it("네 계절을 모두 표시한다 — top-1만 보여주지 않는다", () => {
    render(<ProbabilityBars probabilities={probabilities} winner="autumn_warm" />);

    for (const label of ["봄 웜", "여름 쿨", "가을 웜", "겨울 쿨"]) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
  });

  it("확률 내림차순으로 정렬한다", () => {
    const { container } = render(
      <ProbabilityBars probabilities={probabilities} winner="autumn_warm" />,
    );

    const labels = [...container.querySelectorAll("span:first-child")].map(
      (el) => el.textContent,
    );
    expect(labels).toEqual(["가을 웜", "봄 웜", "겨울 쿨", "여름 쿨"]);
  });

  it("퍼센트를 소수 첫째 자리까지 표시한다", () => {
    render(<ProbabilityBars probabilities={probabilities} winner="autumn_warm" />);

    expect(screen.getByText("82.2%")).toBeInTheDocument();
    expect(screen.getByText("0.4%")).toBeInTheDocument();
  });
});
