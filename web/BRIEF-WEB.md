> **2026-10-28 已过时**：Web 改为桌面端独立产品（Landing + 侧边栏功能页 + 账号同步），以 `../docs/WEB-DESIGN.md` 为准；下文「Mobile-first / 不做 landing / 演示模式」等条款作废。

# 今日小票 · Web — Build Brief (read me first)

Read `../docs/BRIEF.md` (product rules + design language — they apply here too) and `../docs/SPEC.md`
(§4–6). This folder is the **contest-facing Web agent** built on the official QMuse template
(React 19 + Vite + TanStack Router (file routes) + Tailwind v4 + shadcn/Radix + framer-motion + sonner + vaul).
Also read `AGENTS.md` here (QMuse template rules).

## Shape
- Mobile-first web app (judges open it on phone or desktop; desktop shows a centered ~430px phone column
  with a soft canvas around it — Apple-like). First screen is the task, not a landing page.
- Same visual language as the Android app: warm paper canvas `#F5F3EE`, white cards, ink `#1C1C1E`,
  secondary `#8E8E93`, one coral accent `#FF6B4A` (soft `#FFE7E0`), stamp red `#E5484D`. Express them as oklch
  tokens in `src/styles.css` (template requires oklch) — no raw hex in components.
- SF-like system font stack, large bold titles, 16px gutters, 16px card radius, hairlines, springs
  (framer-motion `type: "spring"`), sonner toasts with undo, vaul drawers for sheets. No browser alert/confirm.
- Chinese copy, 俏皮可爱, never shaming. Humans make every choice; AI records/summarizes/reminds/simulates only.

## Data
- Domain contracts: `src/domain/types.ts` (shared; additive changes only). Money is integer cents.
- QMuse 云服务 is NOT initialized yet (needs the owner's login: `qmuse-cli cloud init`). So:
  - Put all persistence behind small domain services in `src/services/` with an interface
    (`LedgerService`, etc.). Implement **`demo` adapters** that hold clearly-labelled demo data in memory
    for the judges' "演示模式" (visible `演示数据` pill). Do NOT add localStorage persistence as a fake backend.
  - Do NOT run `qmuse-cli`, do NOT create `src/services/appwrite.ts`, do NOT edit `.qmuse/`. The integrator will
    wire QMuse 云服务 with the owner later.
- "AI" text (greetings, comments, quick-add parsing) is template/rule based for now behind an interface
  (`src/ai/`), so a cloud-function LLM can replace it later.

## Lanes (stay inside yours)
| Lane | Owns |
|---|---|
| web-c | `src/styles.css`, `src/routes/__root.tsx`, `src/routes/index.tsx`, all other C-side routes, `src/features/{home,entry,receipt,ledger,insights,privacy}/**`, `src/ai/**`, `src/services/**`, `src/logic/**`, `src/components/app/**` (app shell, tab bar, shared app components) |
| web-b | `src/routes/merchant*.tsx` / `src/routes/merchant/**`, `src/routes/game*.tsx` / `src/routes/game/**`, `src/features/{merchant,game}/**` |
- web-b: until web-c lands the shell, render your pages self-contained using shadcn `src/components/ui/*`
  and the CSS variables in `styles.css`; the integrator will mount the tab entry.
- `src/routeTree.gen.ts` is generated — conflicts there are resolved by regenerating; don't hand-edit.
- Don't add dependencies unless essential (everything needed is already installed).

## Verify
`npm run check` and `npm run build` must pass. Pure logic gets unit tests — add `vitest` as a devDependency
(the only allowed new dep) with `npm i -D vitest --ignore-scripts`, script `"test": "vitest run"`; web-c adds it,
web-b may add the same line (merge will dedupe). Commit in your worktree branch with conventional commits ending
`Co-Authored-By: Claude Sonnet <noreply@anthropic.com>`. Don't push or merge.
