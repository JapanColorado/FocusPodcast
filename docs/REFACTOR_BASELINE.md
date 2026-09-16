# Refactor baseline

Counts taken on 2026-09-16 at commit 6c8252a, before the fdroid-only refactor.
Each phase updates the "now" column. Submodule figures cover focus-common,
focus-theme and searchpreference (vendored as `lib/*` in Phase 1); the
focus-purchase submodule (128k lines of vendored Alipay SDK) is excluded
because it is deleted outright.

| Metric | Baseline | Now |
|---|---:|---:|
| First-party source lines (java + kt) | 71,121 | 71,121 |
| Kotlin `!!` assertions (app, core, lib) | 1,865 | 1,865 |
| `printStackTrace()` calls | 135 | 135 |
| Chinese (CJK) lines outside translation resources | 626 | 626 |
| Rx `subscribe(` with no error handler | ~11 | ~11 |
| Build flavors | 3 | 3 |
| Git submodules | 4 | 4 |
| Unit test files (passing tests) | 15 (51) | 15 (54) |

Commands used:

```bash
grep -rho '!!' --include='*.kt' app core lib | grep -v /build/ | wc -l
grep -rn 'printStackTrace' --include='*.java' --include='*.kt' . | grep -v /build/ | wc -l
grep -rnP '[\x{4e00}-\x{9fff}]' --include='*.java' --include='*.kt' --include='*.xml' \
  --include='*.gradle' --include='*.cfg' --include='*.properties' . \
  | grep -v /build/ | grep -v values-zh | grep -v values-ja | wc -l
```
