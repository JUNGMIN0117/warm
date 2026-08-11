import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { SeasonCard } from "./season-card";
import { analysisFixture } from "@/test/fixtures";

describe("SeasonCard", () => {
  it("판정·신뢰도·언더톤을 표시한다", () => {
    render(<SeasonCard result={analysisFixture()} />);

    expect(screen.getByText("가을 웜")).toBeInTheDocument();
    expect(screen.getByText("82.2%")).toBeInTheDocument();
    expect(screen.getByText("웜")).toBeInTheDocument();
  });

  it("마진이 크면 경계 판정 안내를 띄우지 않는다", () => {
    render(<SeasonCard result={analysisFixture({ topTwoMargin: 0.69 })} />);

    expect(screen.queryByText(/경계 판정/)).not.toBeInTheDocument();
  });

  it("마진이 작으면 2위 계절과 함께 경계 판정임을 알린다", () => {
    render(
      <SeasonCard
        result={analysisFixture({
          topTwoMargin: 0.08,
          probabilities: {
            spring_warm: 0.45,
            autumn_warm: 0.53,
            summer_cool: 0.01,
            winter_cool: 0.01,
          },
        })}
      />,
    );

    expect(screen.getByText(/경계 판정/)).toBeInTheDocument();
    // 2위인 봄 웜이 안내문에 등장해야 한다
    expect(screen.getByText(/봄 웜/)).toBeInTheDocument();
  });
});
