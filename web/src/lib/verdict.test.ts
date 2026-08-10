import { describe, expect, it } from "vitest";

import type { Probabilities } from "./api/types";
import { SEASON_FALLBACK_LABELS } from "./season";
import { BORDERLINE_MARGIN, interpret, topTwo, undertoneSentence } from "./verdict";

/**
 * 판정 해석 규칙의 회귀 테스트.
 *
 * 이 규칙이 지키는 프로젝트 원칙: 정확도를 주장하지 않고, 경계 케이스를
 * 단정으로 뭉개지 않는다. 문구가 바뀌는 것은 자유지만 band 분기가 바뀌면
 * 그것은 UX 결정의 변경이므로 테스트도 의도적으로 함께 바꿔야 한다.
 */

const labelOf = (code: keyof Probabilities) => SEASON_FALLBACK_LABELS[code];

function probs(partial: Partial<Probabilities>): Probabilities {
  return {
    spring_warm: 0,
    summer_cool: 0,
    autumn_warm: 0,
    winter_cool: 0,
    ...partial,
  };
}

describe("topTwo", () => {
  it("확률 내림차순으로 상위 두 계절을 뽑는다", () => {
    const p = probs({ spring_warm: 0.1, summer_cool: 0.2, autumn_warm: 0.6, winter_cool: 0.1 });
    expect(topTwo(p)).toEqual(["autumn_warm", "summer_cool"]);
  });
});

describe("interpret", () => {
  const autumnSeason = { code: "autumn_warm" as const };

  it("확실한 결과(과반 + 큰 마진)는 strong — '우세합니다'", () => {
    const verdict = interpret(
      {
        probabilities: probs({ autumn_warm: 0.82, spring_warm: 0.13, winter_cool: 0.05 }),
        topTwoMargin: 0.69,
        season: autumnSeason,
      } as never,
      labelOf,
    );
    expect(verdict.band).toBe("strong");
    expect(verdict.runnerUp).toBeNull();
    expect(verdict.headline).toContain("우세");
  });

  it("55% vs 44%는 사실상 동점 — borderline으로 두 계절을 병기한다", () => {
    // 05-api-spec §10의 topTwoMargin 도입 사유 그대로의 케이스.
    const verdict = interpret(
      {
        probabilities: probs({ autumn_warm: 0.55, spring_warm: 0.44, winter_cool: 0.01 }),
        topTwoMargin: 0.11,
        season: autumnSeason,
      } as never,
      labelOf,
    );
    expect(verdict.band).toBe("borderline");
    expect(verdict.runnerUp).toBe("spring_warm");
    expect(verdict.headline).toContain("사이");
  });

  it("마진이 임계값 바로 위면 borderline이 아니다 (경계 규칙 고정)", () => {
    const verdict = interpret(
      {
        probabilities: probs({ autumn_warm: 0.5, spring_warm: 0.35, winter_cool: 0.15 }),
        topTwoMargin: BORDERLINE_MARGIN,
        season: autumnSeason,
      } as never,
      labelOf,
    );
    expect(verdict.band).not.toBe("borderline");
  });

  it("과반 미만이면서 경계도 아니면 moderate — '보입니다'로 단정을 낮춘다", () => {
    const verdict = interpret(
      {
        probabilities: probs({ autumn_warm: 0.45, spring_warm: 0.25, summer_cool: 0.3 }),
        topTwoMargin: 0.15,
        season: autumnSeason,
      } as never,
      labelOf,
    );
    expect(verdict.band).toBe("moderate");
    expect(verdict.headline).toContain("보입니다");
  });

  it("어떤 band의 headline에도 정확도(%) 주장이 없다", () => {
    // 금지 사항의 회귀 테스트: Phase 3 전까지 정확도 수치를 주장하지 않는다.
    const cases = [
      { p: probs({ autumn_warm: 0.9, spring_warm: 0.1 }), margin: 0.8 },
      { p: probs({ autumn_warm: 0.5, spring_warm: 0.45, summer_cool: 0.05 }), margin: 0.05 },
      { p: probs({ autumn_warm: 0.4, spring_warm: 0.2, summer_cool: 0.4 }), margin: 0.2 },
    ];
    for (const { p, margin } of cases) {
      const verdict = interpret(
        { probabilities: p, topTwoMargin: margin, season: { code: "autumn_warm" } } as never,
        labelOf,
      );
      expect(verdict.headline).not.toMatch(/[0-9]+\s*%/);
      expect(verdict.headline).not.toContain("정확");
    }
  });
});

describe("undertoneSentence", () => {
  it("높은 신뢰도는 '뚜렷', 중간은 '성향', 낮으면 불확실을 인정한다", () => {
    expect(undertoneSentence("warm", 0.95)).toContain("뚜렷");
    expect(undertoneSentence("cool", 0.75)).toContain("성향");
    expect(undertoneSentence("warm", 0.55)).toContain("확실하지 않");
  });
});
