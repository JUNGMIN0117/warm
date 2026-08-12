"""수동 검증셋 라벨링 도구 — Phase 3의 병목(수작업 마찰)을 줄인다.

    uv run python scripts/label_manual_set.py --data data/pseudo --out data/manual_labels.csv

브라우저(http://127.0.0.1:8765)에서 키보드로 라벨링한다:

    undertone(기본): W = warm · C = cool
    season:          1 봄웜 · 2 여름쿨 · 3 가을웜 · 4 겨울쿨
    공통:            S = 건너뛰기(확신 없음) · U = 직전 취소

## 설계 의도

- **규칙 엔진의 판정을 화면에 보여주지 않는다.** 보고 나면 앵커링된다 —
  검증셋이 규칙 엔진 쪽으로 기울면 비교 평가 자체가 무의미해진다
  (02-data-pipeline.md §5). labels.csv에서 읽는 것은 파일 목록뿐이다.
- **확신 없으면 건너뛴다.** 애매한 정답지는 두 모델 모두에게 잡음이다.
  건너뛴 목록은 별도 파일(manual_skipped.csv)에 남겨 재시작 시 다시
  나오지 않게 하되, 평가 입력에는 섞이지 않는다.
- **이어하기가 기본이다.** 300장을 한 번에 끝낼 필요가 없다 — 이미
  라벨링한 파일은 다시 나오지 않는다.
- 표시 순서는 시드 고정 셔플이다. labels.csv 순서(대개 파일명순)를
  그대로 쓰면 라벨링 피로가 특정 구간에 몰려 계통 오차가 될 수 있다.

표준 라이브러리 http.server를 쓴 이유: 라벨링은 로컬 1인용 도구라
FastAPI 앱을 하나 더 만드는 것이 과하고, 의존성 0이 유지된다.
"""

from __future__ import annotations

import argparse
import csv
import json
import random
import sys
import webbrowser
from functools import partial
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from scripts.dataset_common import (
    CROP_DIR_NAMES,
    SEASON_CLASSES,
    UNDERTONE_CLASSES,
    force_utf8_stdout,
    read_labels,
)

_PAGE = """<!doctype html>
<meta charset="utf-8">
<title>사계 — 수동 라벨링</title>
<style>
  body { font-family: sans-serif; display: flex; flex-direction: column;
         align-items: center; gap: 16px; padding-top: 24px; background: #18181b;
         color: #fafafa; }
  img { width: 384px; height: 384px; image-rendering: auto; border-radius: 12px; }
  .keys { display: flex; gap: 8px; }
  .keys span { background: #27272a; padding: 8px 14px; border-radius: 8px; font-size: 14px; }
  kbd { background: #3f3f46; border-radius: 4px; padding: 1px 7px; margin-right: 6px; }
  #progress { color: #a1a1aa; font-size: 14px; }
  #done { font-size: 20px; display: none; }
</style>
<h2>수동 라벨링 — <span id="target"></span></h2>
<p id="progress"></p>
<img id="crop" alt="라벨링할 얼굴 크롭">
<div class="keys" id="keys"></div>
<p><span class="keys"><span><kbd>S</kbd>확신 없음(건너뛰기)</span><span>
<kbd>U</kbd>직전 취소</span></span></p>
<p id="done">🎉 남은 이미지가 없습니다. 창을 닫아도 됩니다.</p>
<script>
let current = null;
const KEYMAPS = {
  undertone: { w: "warm", c: "cool" },
  season: { "1": "spring_warm", "2": "summer_cool", "3": "autumn_warm", "4": "winter_cool" },
};
const LABEL_KO = { warm: "웜", cool: "쿨", spring_warm: "봄웜", summer_cool: "여름쿨",
                   autumn_warm: "가을웜", winter_cool: "겨울쿨" };
let keymap = {};

async function refresh() {
  const state = await (await fetch("/state")).json();
  document.getElementById("target").textContent = state.target;
  keymap = KEYMAPS[state.target];
  document.getElementById("keys").innerHTML = Object.entries(keymap)
    .map(([k, v]) => `<span><kbd>${k.toUpperCase()}</kbd>${LABEL_KO[v]}</span>`).join("");
  document.getElementById("progress").textContent =
    `라벨 ${state.labeled} · 건너뜀 ${state.skipped} · 남음 ${state.remaining}`;
  current = state.next;
  const img = document.getElementById("crop");
  const done = document.getElementById("done");
  if (current === null) { img.style.display = "none"; done.style.display = "block"; }
  else { img.style.display = "block"; done.style.display = "none";
         img.src = `/image/${encodeURIComponent(current)}`; }
}

document.addEventListener("keydown", async (e) => {
  const key = e.key.toLowerCase();
  if (key === "u") { await fetch("/undo", { method: "POST" }); return refresh(); }
  if (current === null) return;
  let body = null;
  if (key === "s") body = { filename: current, label: "__skip__" };
  else if (keymap[key]) body = { filename: current, label: keymap[key] };
  if (body === null) return;
  await fetch("/label", { method: "POST", headers: { "Content-Type": "application/json" },
                          body: JSON.stringify(body) });
  refresh();
});
refresh();
</script>
"""


class LabelStore:
    """라벨·건너뜀의 영속화와 이어하기. CSV가 단일 저장소다."""

    def __init__(self, out_path: Path, skip_path: Path) -> None:
        self.out_path = out_path
        self.skip_path = skip_path
        self.labels: list[tuple[str, str]] = self._read(out_path)
        self.skips: list[str] = [f for f, _ in self._read(skip_path)]
        # undo를 위해 이번 세션의 행동 순서를 기억한다 (label | skip).
        self._history: list[str] = []

    @staticmethod
    def _read(path: Path) -> list[tuple[str, str]]:
        if not path.exists():
            return []
        with path.open(encoding="utf-8", newline="") as f:
            return [(row["filename"], row.get("label", "")) for row in csv.DictReader(f)]

    def seen(self) -> set[str]:
        return {f for f, _ in self.labels} | set(self.skips)

    def add(self, filename: str, label: str) -> None:
        if label == "__skip__":
            self.skips.append(filename)
            self._history.append("skip")
        else:
            self.labels.append((filename, label))
            self._history.append("label")
        self._flush()

    def undo(self) -> None:
        if not self._history:
            return
        kind = self._history.pop()
        if kind == "skip" and self.skips:
            self.skips.pop()
        elif kind == "label" and self.labels:
            self.labels.pop()
        self._flush()

    def _flush(self) -> None:
        """매 행동마다 전체 재기록. 300장 규모에서 성능은 무의미하고,
        중간에 창을 닫아도 마지막 행동까지 항상 디스크에 있다."""
        self.out_path.parent.mkdir(parents=True, exist_ok=True)
        with self.out_path.open("w", encoding="utf-8", newline="") as f:
            writer = csv.writer(f)
            writer.writerow(["filename", "label"])
            writer.writerows(self.labels)
        with self.skip_path.open("w", encoding="utf-8", newline="") as f:
            writer = csv.writer(f)
            writer.writerow(["filename", "reason"])
            writer.writerows((s, "unsure") for s in self.skips)


class LabelingHandler(BaseHTTPRequestHandler):
    """단일 클라이언트 전제의 로컬 핸들러."""

    def __init__(
        self, *args: object, store: LabelStore, queue: list[str],
        crop_dir: Path, target: str, **kwargs: object,
    ) -> None:
        self.store = store
        self.queue = queue
        self.crop_dir = crop_dir
        self.target = target
        super().__init__(*args, **kwargs)  # type: ignore[arg-type]

    def log_message(self, format: str, *args: object) -> None:
        """요청 로그 침묵 — 라벨링 중 터미널 도배 방지."""

    def _send(self, status: int, content_type: str, body: bytes) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _next(self) -> str | None:
        seen = self.store.seen()
        for name in self.queue:
            if name not in seen:
                return name
        return None

    def do_GET(self) -> None:
        if self.path == "/":
            self._send(200, "text/html; charset=utf-8", _PAGE.encode())
        elif self.path == "/state":
            payload = {
                "target": self.target,
                "labeled": len(self.store.labels),
                "skipped": len(self.store.skips),
                "remaining": len(self.queue) - len(self.store.seen() & set(self.queue)),
                "next": self._next(),
            }
            self._send(200, "application/json", json.dumps(payload).encode())
        elif self.path.startswith("/image/"):
            name = Path(self.path.removeprefix("/image/")).name  # 경로 탈출 방지
            file = self.crop_dir / name
            if file.exists():
                self._send(200, "image/png", file.read_bytes())
            else:
                self._send(404, "text/plain", b"not found")
        else:
            self._send(404, "text/plain", b"not found")

    def do_POST(self) -> None:
        if self.path == "/label":
            length = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(length))
            valid = {"__skip__", *UNDERTONE_CLASSES, *SEASON_CLASSES}
            if body.get("label") in valid and isinstance(body.get("filename"), str):
                self.store.add(body["filename"], body["label"])
                self._send(200, "application/json", b"{}")
            else:
                self._send(400, "text/plain", b"bad label")
        elif self.path == "/undo":
            self.store.undo()
            self._send(200, "application/json", b"{}")
        else:
            self._send(404, "text/plain", b"not found")


def main() -> int:
    force_utf8_stdout()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, required=True, help="generate_pseudo_labels 출력 폴더")
    parser.add_argument("--out", type=Path, default=Path("data/manual_labels.csv"))
    parser.add_argument("--target", choices=["undertone", "season"], default="undertone")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--seed", type=int, default=42, help="표시 순서 셔플 시드")
    args = parser.parse_args()

    filenames = [row.filename for row in read_labels(args.data)]
    random.Random(args.seed).shuffle(filenames)

    store = LabelStore(
        out_path=args.out,
        skip_path=args.out.with_name(args.out.stem + "_skipped.csv"),
    )
    handler = partial(
        LabelingHandler,
        store=store,
        queue=filenames,
        crop_dir=args.data / CROP_DIR_NAMES["crop"],
        target=args.target,
    )

    url = f"http://127.0.0.1:{args.port}"
    print(f"라벨링 시작: {url}  (대상 {len(filenames)}장, 이미 처리 {len(store.seen())}장)")
    print("규칙 엔진의 판정은 화면에 표시되지 않습니다 — 의도된 동작입니다 (앵커링 방지).")
    webbrowser.open(url)
    server = HTTPServer(("127.0.0.1", args.port), handler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print(f"\n종료. 라벨 {len(store.labels)}건 → {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
