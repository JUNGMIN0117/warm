"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Save, ShieldAlert, Trash2 } from "lucide-react";
import { fetchSeasons, updateSeasonCuration } from "@/lib/api/endpoints";
import type { ColorView, CurationUpdateRequest, SeasonView } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/auth-context";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { toast } from "sonner";

/**
 * 관리자 — 큐레이션 편집.
 *
 * ADR-005("큐레이션은 재배포 없이 갱신")의 화면이다. 저장 즉시 결과
 * 화면·팔레트 조회에 반영된다 — 캐시에는 측정값만 있고 큐레이션은
 * 응답 조립 시 DB에서 조인되기 때문.
 *
 * role 검사는 UI 노출 판단일 뿐이다. localStorage를 조작해 이 화면을
 * 열어도 저장 요청은 서버의 hasRole(ADMIN)에서 403으로 거절된다.
 */

interface Draft {
  keywords: string;
  description: string;
  bestColors: ColorView[];
  worstColors: ColorView[];
  stylingTips: string[];
}

function toDraft(season: SeasonView): Draft {
  return {
    keywords: season.keywords.join(", "),
    description: season.description,
    bestColors: season.bestColors.map((c) => ({ ...c })),
    worstColors: season.worstColors.map((c) => ({ ...c })),
    stylingTips: [...season.stylingTips],
  };
}

function toRequest(draft: Draft): CurationUpdateRequest {
  return {
    keywords: draft.keywords.split(",").map((k) => k.trim()).filter(Boolean),
    description: draft.description,
    bestColors: draft.bestColors,
    worstColors: draft.worstColors,
    stylingTips: draft.stylingTips.map((t) => t.trim()).filter(Boolean),
  };
}

export default function AdminPage() {
  const { status, auth } = useAuth();
  const queryClient = useQueryClient();
  const [code, setCode] = useState("spring_warm");
  const [draft, setDraft] = useState<Draft | null>(null);

  const SEASON_ORDER = ["spring_warm", "summer_cool", "autumn_warm", "winter_cool"];
  const seasons = useQuery({
    queryKey: ["seasons"],
    queryFn: fetchSeasons,
    enabled: status === "authenticated" && auth?.role === "ADMIN",
    // DB 조회 순서는 보장이 없다 — 계절의 자연 순서로 고정한다.
    select: (data) =>
      [...data].sort((a, b) => SEASON_ORDER.indexOf(a.code) - SEASON_ORDER.indexOf(b.code)),
  });

  const selected = seasons.data?.find((s) => s.code === code);

  // 계절을 바꾸면 그 계절의 서버 상태로 초안을 다시 만든다.
  useEffect(() => {
    if (selected !== undefined) setDraft(toDraft(selected));
  }, [selected]);

  const save = useMutation({
    mutationFn: (body: CurationUpdateRequest) => updateSeasonCuration(code, body),
    onSuccess: () => {
      toast.success("저장되었습니다 — 결과 화면에 즉시 반영됩니다.");
      void queryClient.invalidateQueries({ queryKey: ["seasons"] });
    },
    onError: (error) => toast.error(error.message),
  });

  if (status === "loading") {
    return <Skeleton className="mx-auto mt-8 h-96 max-w-3xl rounded-xl" />;
  }

  if (status !== "authenticated" || auth?.role !== "ADMIN") {
    return (
      <div className="mx-auto max-w-md pt-16 text-center">
        <ShieldAlert className="mx-auto mb-4 size-10 text-muted-foreground" aria-hidden />
        <h1 className="text-xl font-semibold">관리자 전용 화면입니다</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          관리자 계정으로 로그인해야 큐레이션을 편집할 수 있습니다.
        </p>
        <Button render={<Link href="/login" />} className="mt-6">
          로그인
        </Button>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <div>
        <h1 className="text-2xl font-bold">큐레이션 편집</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          팔레트·키워드·팁은 측정이 아니라 큐레이션입니다 — 여기서 바꾸면 재배포 없이
          즉시 반영됩니다. 라벨과 이모지는 계절의 표기 정체성이라 편집 대상이 아닙니다.
        </p>
      </div>

      <Tabs value={code} onValueChange={setCode}>
        <TabsList className="w-full">
          {(seasons.data ?? []).map((s) => (
            <TabsTrigger key={s.code} value={s.code} className="flex-1">
              {s.emoji} {s.labelKo}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      {seasons.isPending && <Skeleton className="h-96 rounded-xl" />}
      {seasons.isError && (
        <Alert variant="destructive">
          <AlertTitle>카탈로그를 불러오지 못했습니다</AlertTitle>
          <AlertDescription>{seasons.error.message}</AlertDescription>
        </Alert>
      )}

      {draft !== null && (
        <form
          className="space-y-5"
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate(toRequest(draft));
          }}
        >
          <Card>
            <CardHeader>
              <CardTitle className="text-base">기본 정보</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="keywords">키워드 (쉼표로 구분)</Label>
                <Input
                  id="keywords"
                  value={draft.keywords}
                  onChange={(e) => setDraft({ ...draft, keywords: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="description">설명</Label>
                <textarea
                  id="description"
                  className="min-h-20 w-full rounded-lg border bg-background p-3 text-sm"
                  value={draft.description}
                  onChange={(e) => setDraft({ ...draft, description: e.target.value })}
                />
              </div>
            </CardContent>
          </Card>

          <PaletteEditor
            title="추천 색 (최소 6개)"
            colors={draft.bestColors}
            onChange={(bestColors) => setDraft({ ...draft, bestColors })}
          />
          <PaletteEditor
            title="기피 색 (최소 3개)"
            colors={draft.worstColors}
            onChange={(worstColors) => setDraft({ ...draft, worstColors })}
          />

          <Card>
            <CardHeader>
              <CardTitle className="text-base">스타일링 팁</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {draft.stylingTips.map((tip, index) => (
                <div key={index} className="flex gap-2">
                  <Input
                    value={tip}
                    onChange={(e) => {
                      const stylingTips = [...draft.stylingTips];
                      stylingTips[index] = e.target.value;
                      setDraft({ ...draft, stylingTips });
                    }}
                  />
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label="팁 삭제"
                    onClick={() =>
                      setDraft({
                        ...draft,
                        stylingTips: draft.stylingTips.filter((_, i) => i !== index),
                      })
                    }
                  >
                    <Trash2 aria-hidden />
                  </Button>
                </div>
              ))}
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setDraft({ ...draft, stylingTips: [...draft.stylingTips, ""] })}
              >
                <Plus aria-hidden /> 팁 추가
              </Button>
            </CardContent>
          </Card>

          <div className="flex justify-end gap-3">
            <Button
              type="button"
              variant="outline"
              disabled={selected === undefined}
              onClick={() => selected !== undefined && setDraft(toDraft(selected))}
            >
              되돌리기
            </Button>
            <Button type="submit" disabled={save.isPending} className="bg-gradient-brand border-0 text-white hover:opacity-90">
              <Save aria-hidden /> {save.isPending ? "저장 중…" : "저장"}
            </Button>
          </div>
        </form>
      )}
    </div>
  );
}

function PaletteEditor({
  title,
  colors,
  onChange,
}: {
  title: string;
  colors: ColorView[];
  onChange: (colors: ColorView[]) => void;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {colors.map((color, index) => (
          <div key={index} className="flex items-center gap-2">
            {/* 네이티브 컬러 피커 — hex 타이핑 없이도 고를 수 있게 */}
            <input
              type="color"
              aria-label={`${color.name} 색상`}
              className="size-9 shrink-0 cursor-pointer rounded border bg-transparent"
              value={/^#[0-9A-Fa-f]{6}$/.test(color.hex) ? color.hex : "#000000"}
              onChange={(e) => {
                const next = [...colors];
                next[index] = { ...color, hex: e.target.value.toUpperCase() };
                onChange(next);
              }}
            />
            <Input
              value={color.name}
              placeholder="색 이름"
              onChange={(e) => {
                const next = [...colors];
                next[index] = { ...color, name: e.target.value };
                onChange(next);
              }}
            />
            <Input
              value={color.hex}
              placeholder="#RRGGBB"
              className="w-28 font-mono uppercase"
              onChange={(e) => {
                const next = [...colors];
                next[index] = { ...color, hex: e.target.value.toUpperCase() };
                onChange(next);
              }}
            />
            <Button
              type="button"
              variant="ghost"
              size="icon"
              aria-label="색 삭제"
              onClick={() => onChange(colors.filter((_, i) => i !== index))}
            >
              <Trash2 aria-hidden />
            </Button>
          </div>
        ))}
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => onChange([...colors, { name: "", hex: "#CCCCCC" }])}
        >
          <Plus aria-hidden /> 색 추가
        </Button>
      </CardContent>
    </Card>
  );
}
