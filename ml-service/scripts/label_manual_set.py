"""수동 검증셋 라벨링 도구 — Phase 3의 병목(수작업 마찰)을 줄인다.

    uv run python scripts/label_manual_set.py --data data/pseudo --out data/manual_labels.csv

브라우저(http://127.0.0.1:8765)에 이미지가 **그리드로 24장씩** 나온다.

    클릭        라벨 순환 (없음 → 웜 → 쿨 → 없음 / season이면 4계절 순환)
    Enter       페이지 확정 — 클릭 안 한 이미지는 전부 "확신 없음(건너뜀)"
    U           직전 페이지 취소

## 왜 그리드인가

검증셋 전략이 "명백한 케이스만 라벨링"이므로(02-data-pipeline.md §5),
한 장씩 넘기며 전부에 답하는 UI는 낭비다. 화면에 깔아놓고 눈에 확 띄는
것만 집는 방식이 같은 시간에 몇 배를 훑는다. 클릭하지 않음 = 건너뜀이
기본값인 것도 의도다 — 애매한 사진에 답을 강요하지 않는다.

## 설계 의도 (그리드와 무관하게 유지되는 것)

- **규칙 엔진의 판정·측정값을 화면에 보여주지 않는다.** 보고 나면
  앵커링된다 — 검증셋이 규칙 엔진 쪽으로 기울면 비교 평가 자체가
  무의미해진다. labels.csv에서 읽는 것은 파일 목록뿐이다.
- **이어하기가 기본이다.** 페이지 확정마다 즉시 저장되므로 언제 닫아도
  이미 처리한 파일은 다시 나오지 않는다.
- 표시 순서는 시드 고정 셔플 — 라벨링 피로가 특정 구간에 몰려 계통
  오차가 되는 것을 막는다.
- 건너뜀은 별도 파일(*_skipped.csv)에 남아 평가 입력에 섞이지 않는다.

표준 라이브러리 http.server를 쓰는 이유: 라벨링은 로컬 1인용 도구라
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
    classes_for_target,
    force_utf8_stdout,
    read_labels,
)

PAGE_SIZE = 24

_PAGE = """<!doctype html>
<meta charset="utf-8">
<title>사계 — 수동 라벨링</title>
<style>
  body { font-family: sans-serif; display: flex; flex-direction: column;
         align-items: center; gap: 12px; padding: 16px; background: #18181b; color: #fafafa; }
  #grid { display: grid; grid-template-columns: repeat(6, 148px); gap: 10px; }
  .cell { position: relative; cursor: pointer; border-radius: 10px;
          border: 3px solid transparent; }
  .cell img { width: 148px; height: 148px; border-radius: 8px; display: block; }
  .cell .badge { position: absolute; top: 4px; left: 4px; padding: 2px 8px;
                 border-radius: 6px; font-weight: 700; font-size: 13px; display: none; }
  .cell[data-label="warm"] { border-color: #f59e0b; }
  .cell[data-label="warm"] .badge { display: block; background: #f59e0b; color: #000; }
  .cell[data-label="cool"] { border-color: #38bdf8; }
  .cell[data-label="cool"] .badge { display: block; background: #38bdf8; color: #000; }
  .cell[data-label="spring_warm"] { border-color: #fb7185; }
  .cell[data-label="spring_warm"] .badge { display: block; background: #fb7185; }
  .cell[data-label="summer_cool"] { border-color: #38bdf8; }
  .cell[data-label="summer_cool"] .badge { display: block; background: #38bdf8; color: #000; }
  .cell[data-label="autumn_warm"] { border-color: #f59e0b; }
  .cell[data-label="autumn_warm"] .badge { display: block; background: #f59e0b; color: #000; }
  .cell[data-label="winter_cool"] { border-color: #818cf8; }
  .cell[data-label="winter_cool"] .badge { display: block; background: #818cf8; }
  #bar { color: #a1a1aa; font-size: 14px; }
  button { background: #fafafa; color: #18181b; border: 0; border-radius: 8px;
           padding: 10px 22px; font-size: 15px; font-weight: 700; cursor: pointer; }
  kbd { background: #3f3f46; border-radius: 4px; padding: 1px 7px; }
  #done { font-size: 20px; display: none; }
</style>
<div id="bar"></div>
<p style="color:#a1a1aa;font-size:13px;margin:0">
  <b>확 눈에 띄는 것만</b> 클릭하세요 (클릭 = 라벨 순환) · 나머지는 자동 건너뜀 ·
  <kbd>Enter</kbd> 페이지 확정 · <kbd>U</kbd> 직전 페이지 취소
</p>
<div id="grid"></div>
<button id="commit">이 페이지 확정 → 다음 (Enter)</button>
<p id="done">🎉 남은 이미지가 없습니다. 창을 닫아도 됩니다.</p>
<script>
const LABEL_KO = { warm: "웜", cool: "쿨", spring_warm: "봄웜", summer_cool: "여름쿨",
                   autumn_warm: "가을웜", winter_cool: "겨울쿨" };
let cycle = [];   // [null, ...classes] — 서버가 준다
let files = [];

async function load() {
  const page = await (await fetch("/page")).json();
  cycle = [null, ...page.classes];
  files = page.files;
  document.getElementById("bar").textContent =
    `라벨 ${page.labeled} · 건너뜀 ${page.skipped} · 남음 ${page.remaining}`;
  const grid = document.getElementById("grid");
  grid.innerHTML = "";
  const commit = document.getElementById("commit");
  const done = document.getElementById("done");
  commit.style.display = files.length ? "block" : "none";
  done.style.display = files.length ? "none" : "block";
  for (const f of files) {
    const cell = document.createElement("div");
    cell.className = "cell";
    cell.dataset.file = f;
    cell.innerHTML = `<img src="/image/${encodeURIComponent(f)}" loading="lazy">
                      <span class="badge"></span>`;
    cell.onclick = () => {
      const idx = cycle.indexOf(cell.dataset.label || null);
      const next = cycle[(idx + 1) % cycle.length];
      if (next === null) { delete cell.dataset.label; }
      else { cell.dataset.label = next;
             cell.querySelector(".badge").textContent = LABEL_KO[next] ?? next; }
    };
    grid.appendChild(cell);
  }
}

async function commitPage() {
  if (!files.length) return;
  const items = [...document.querySelectorAll(".cell")].map((c) => ({
    filename: c.dataset.file,
    label: c.dataset.label ?? "__skip__",
  }));
  await fetch("/batch", { method: "POST", headers: { "Content-Type": "application/json" },
                          body: JSON.stringify({ items }) });
  load();
}

document.getElementById("commit").onclick = commitPage;
document.addEventListener("keydown", async (e) => {
  if (e.key === "Enter") { e.preventDefault(); commitPage(); }
  if (e.key.toLowerCase() === "u") { await fetch("/undo", { method: "POST" }); load(); }
});
load();
</script>
"""


class LabelStore:
    """라벨·건너뜀의 영속화와 이어하기. CSV가 단일 저장소다."""

    def __init__(self, out_path: Path, skip_path: Path) -> None:
        self.out_path = out_path
        self.skip_path = skip_path
        self.labels: list[tuple[str, str]] = self._read(out_path)
        self.skips: list[str] = [f for f, _ in self._read(skip_path)]
        # undo 단위는 "페이지 확정" 하나 — 각 항목이 어느 목록에 갔는지 기억한다.
        self._history: list[list[str]] = []

    @staticmethod
    def _read(path: Path) -> list[tuple[str, str]]:
        if not path.exists():
            return []
        with path.open(encoding="utf-8", newline="") as f:
            return [(row["filename"], row.get("label", "")) for row in csv.DictReader(f)]

    def seen(self) -> set[str]:
        return {f for f, _ in self.labels} | set(self.skips)

    def add_batch(self, items: list[tuple[str, str]]) -> None:
        kinds: list[str] = []
        for filename, label in items:
            if label == "__skip__":
                self.skips.append(filename)
                kinds.append("skip")
            else:
                self.labels.append((filename, label))
                kinds.append("label")
        self._history.append(kinds)
        self._flush()

    def undo_batch(self) -> None:
        if not self._history:
            return
        for kind in reversed(self._history.pop()):
            if kind == "skip" and self.skips:
                self.skips.pop()
            elif kind == "label" and self.labels:
                self.labels.pop()
        self._flush()

    def _flush(self) -> None:
        """확정마다 전체 재기록. 수백 장 규모에서 성능은 무의미하고,
        중간에 창을 닫아도 마지막 확정까지 항상 디스크에 있다."""
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

    def _pending(self) -> list[str]:
        seen = self.store.seen()
        return [name for name in self.queue if name not in seen]

    def do_GET(self) -> None:
        if self.path == "/":
            self._send(200, "text/html; charset=utf-8", _PAGE.encode())
        elif self.path == "/page":
            pending = self._pending()
            payload = {
                "classes": list(classes_for_target(self.target)),
                "labeled": len(self.store.labels),
                "skipped": len(self.store.skips),
                "remaining": len(pending),
                "files": pending[:PAGE_SIZE],
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
        if self.path == "/batch":
            length = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(length))
            valid = {"__skip__", *classes_for_target(self.target)}
            items: list[tuple[str, str]] = []
            for item in body.get("items", []):
                if isinstance(item.get("filename"), str) and item.get("label") in valid:
                    items.append((item["filename"], item["label"]))
            if items:
                self.store.add_batch(items)
            self._send(200, "application/json", b"{}")
        elif self.path == "/undo":
            self.store.undo_batch()
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
    parser.add_argument(
        "--exclude", type=Path, action="append", default=[],
        help="이 CSV(filename 열)에 있는 파일을 큐에서 제외 — 이전 라운드의 "
             "라벨·건너뜀 파일을 주면 새 라운드가 겹치지 않는다 (반복 지정 가능)",
    )
    args = parser.parse_args()

    excluded: set[str] = set()
    for path in args.exclude:
        with path.open(encoding="utf-8", newline="") as f:
            excluded.update(row["filename"] for row in csv.DictReader(f))

    filenames = [
        row.filename for row in read_labels(args.data) if row.filename not in excluded
    ]
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
    print("확 눈에 띄는 것만 클릭하세요 — 클릭 안 한 이미지는 자동으로 건너뜁니다.")
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
