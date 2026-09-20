# Releasing

Pushing a tag builds an APK and publishes it to
[Releases](https://github.com/Dalakoti07/-Vibe-Coding-Speech-to-Text/releases).
Nothing else triggers a build — ordinary pushes and pull requests do nothing.

```bash
./scripts/release.sh 1.0.0
```

The script refuses to run on a dirty tree, rejects a version that isn't
`MAJOR.MINOR.PATCH`, checks the tag doesn't already exist locally or on the remote,
shows you the commits since the last tag, and asks before pushing. Then
[`.github/workflows/release.yml`](../.github/workflows/release.yml) takes over.

By hand, if you prefer:

```bash
git tag -a v1.0.0 -m "v1.0.0"
git push origin v1.0.0
```

A hyphen makes it a pre-release: `v2.0.0-beta1`.

## What the workflow does

1. Derives `versionName` from the tag and `versionCode` arithmetically — `1.2.3` → `10203`,
   so a newer tag always outranks an older install
2. Decodes the signing keystore, **if the secrets exist**
3. `./gradlew :app:assembleRelease` with R8 on
4. Renames to `parakeet-<version>.apk` and records its size and SHA-256
5. Uploads it as a workflow artefact (kept 30 days)
6. Creates the GitHub Release with notes: build metadata, the model-install instructions,
   and the commit list since the previous tag

`workflow_dispatch` runs stop after step 5 — you get an APK to check without cutting a
release.

## Signing

**It works with no setup.** Without secrets the APK is signed with the debug key: fully
installable, but it cannot *update* an install made with a different key — you would have
to uninstall first. That is fine for a sideloaded personal app, and the workflow prints a
warning so it never surprises you.

To sign properly, generate a keystore once:

```bash
keytool -genkeypair -v \
  -keystore release.jks -alias parakeet \
  -keyalg RSA -keysize 2048 -validity 10000
```

Then add four repository secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -i release.jks \| pbcopy` |
| `KEYSTORE_PASSWORD` | the store password |
| `KEY_ALIAS` | `parakeet` |
| `KEY_PASSWORD` | the key password |

**Keep `release.jks` out of the repo and back it up somewhere.** Lose it and you can never
update an existing install — Android identifies an app by its signing key, not its name.

## Cost

| Repository | Actions minutes |
|---|---|
| Public | **Unlimited, free** |
| Private | 2,000 min/month on the free plan |

A release build is roughly 4–6 minutes, and only tags trigger it. Either way you would
have to try hard to reach a limit.

## The APK does not contain the model

624 MB of weights are not going in a GitHub release asset, and they were never meant to
be bundled. The release notes carry the `adb push` instructions, and a fresh install shows
the same thing on screen until a model is found.

## Verified before this was written

| Check | Result |
|---|---|
| `assembleRelease` with R8 | **passes** — 28 MB APK, `libonnxruntime.so` 22 MB of it |
| Keep rules held | `OfflineRecognizer` maps to itself in `mapping.txt`; native method names survive |
| Both `.so` files packaged | present under `lib/arm64-v8a/` |
| Tag-derived versioning | built with `-PversionName=1.0.0` → installs as `versionName=1.0.0`, `versionCode=10000` |
| **R8 output on hardware** | **passes** — release APK installed on a Galaxy S24 Ultra (Android 16); model loaded in 2,874 ms, no `UnsatisfiedLinkError` |

That last row is the one that mattered: R8 stripping the sherpa JNI bindings was a
standing risk that only ever shows up in a release build. It is now closed on a real
device.
