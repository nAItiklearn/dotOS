## What this changes

<!-- A short description. Link the issue it fixes, if any. -->

## Why

## How to check it

<!-- Steps, or the CI artifact / screenshots to look at. For UI changes, trigger the Screenshots workflow or attach your own screenshots of DotOS. -->

## Checklist

- [ ] **No network.** No `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, location or advertising-id permission, and no dependency or SDK that adds them (analytics, crash reporting, ads, Firebase, Play Services, remote config).
- [ ] **Original assets only.** No Nothing wallpapers, icon artwork, logos or fonts; no traced or look-alike third-party brand logos; no screenshots of other launchers or operating systems committed. New fonts or libraries are open-source, added to `THIRD_PARTY_LICENSES.md` (and `licenses/` for fonts, and the in-app license list).
- [ ] **CI is green:** `assembleDebug`, `assembleRelease`, `testDebugUnitTest`, `lintRelease` and the Privacy check.
- [ ] New runtime permissions are requested on first use only, and the README permission table and `PRIVACY.md` are updated.
- [ ] Anything that stores new data keeps it in app-private storage, and `PRIVACY.md` says so.
- [ ] Tests added or updated for pure logic where practical.
- [ ] Docs updated if behaviour changed (`README.md`, `docs/`). Unfinished parts are marked as planned.
- [ ] Code follows `.editorconfig` and the existing module structure (no new DI framework, no one-composable-per-dot rendering).
- [ ] No secrets, keystores or personal data in the diff.
