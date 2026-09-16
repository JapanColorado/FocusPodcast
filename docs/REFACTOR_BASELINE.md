# Refactor baseline

Counts taken on 2026-09-16 at commit 6c8252a, before the fdroid-only refactor.
Each phase updates the "now" column. Submodule figures cover focus-common,
focus-theme and searchpreference (vendored as `lib/*` in Phase 1); the
focus-purchase submodule (128k lines of vendored Alipay SDK) is excluded
because it is deleted outright.

| Metric | Baseline | Now |
|---|---:|---:|
| First-party source lines (java + kt) | 71,121 | 65,483 |
| Kotlin `!!` assertions (app, core, lib) | 1,865 | 1,690 |
| `printStackTrace()` calls | 135 | 126 |
| Chinese (CJK) lines outside translation resources | 626 | 0 |
| Rx `subscribe(` with no error handler | ~11 | ~11 |
| Build flavors | 3 | 1 |
| Git submodules | 4 | 0 |
| Unit test files (passing tests) | 15 (51) | 10 (58) |

Commands used:

```bash
find app core model storage parser playback net ui event -name '*.java' -o -name '*.kt' \
  | grep -v /build/ | xargs cat | wc -l
grep -rho '!!' --include='*.kt' app core lib | grep -v /build/ | wc -l
grep -rn 'printStackTrace' --include='*.java' --include='*.kt' . | grep -v /build/ | wc -l
grep -rnP '[\x{4e00}-\x{9fff}]' --include='*.java' --include='*.kt' --include='*.xml' \
  --include='*.gradle' --include='*.cfg' --include='*.properties' . \
  | grep -v /build/ | grep -v values-zh | grep -v values-ja | wc -l
```
