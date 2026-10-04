## Morph / WarpGrep usage

For codebase exploration, planning, refactors, or unfamiliar areas, use Morph MCP `codebase_search` first.

When using Morph MCP / WarpGrep:
- Minimize Morph calls: run one focused `codebase_search` first, then read only relevant files.
- Do not create todo lists or progress narration unless explicitly requested.
- Return final answers only: relevant files, line ranges, and one short reason per file.
- After each integration inspection (feature-to-feature, module-to-module, native bridge, or client-to-backend contract touchpoint), update `.github/docs/plugin_integration_map.md` with discovered hooks, shared tables/stores, config keys, dependencies, and risky couplings.
- Treat `.github/docs/plugin_integration_map.md` as a living integration map for this repository: rewrite affected sections after each relevant inspection, remove stale or duplicate findings, and move durable architecture decisions into ADRs when needed.
- Respect current project authority: `legacy_android_kotlin/` is the active Android beta target, while root Expo/`android/`/`modules/` content is historical unless explicitly reactivated.