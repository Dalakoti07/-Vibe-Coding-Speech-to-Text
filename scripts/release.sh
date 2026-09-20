#!/usr/bin/env bash
#
# Cut a release: tag the current commit and push it. GitHub Actions does the rest —
# builds the APK and creates the release at
#   https://github.com/Dalakoti07/-Vibe-Coding-Speech-to-Text/releases
#
#   ./scripts/release.sh 1.0.0
#   ./scripts/release.sh 2.0.0-beta1      # a hyphen marks it a pre-release
#
set -euo pipefail

VERSION="${1:-}"
REMOTE="${REMOTE:-origin}"

die() { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }
note() { printf '\033[36m→\033[0m %s\n' "$*"; }

[ -n "$VERSION" ] || die "usage: $0 <version>   e.g. $0 1.0.0"

# Accept 1.2.3 and 1.2.3-beta1; reject anything that would confuse versionCode.
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$ ]] \
  || die "'$VERSION' is not MAJOR.MINOR.PATCH[-suffix]"

TAG="v$VERSION"

cd "$(git rev-parse --show-toplevel)"

[ -z "$(git status --porcelain)" ] \
  || die "working tree is dirty — commit or stash first"

git rev-parse -q --verify "refs/tags/$TAG" >/dev/null \
  && die "tag $TAG already exists locally"

git ls-remote --exit-code --tags "$REMOTE" "refs/tags/$TAG" >/dev/null 2>&1 \
  && die "tag $TAG already exists on $REMOTE"

BRANCH=$(git rev-parse --abbrev-ref HEAD)
note "tagging $BRANCH @ $(git rev-parse --short HEAD) as $TAG"

# Everything since the previous tag becomes the annotation.
PREV=$(git describe --tags --abbrev=0 2>/dev/null || true)
if [ -n "$PREV" ]; then
  BODY=$(git log --no-merges --pretty='- %s' "${PREV}..HEAD")
  note "changes since $PREV:"
else
  BODY="First release."
  note "no previous tag — this is the first release"
fi
printf '%s\n\n' "$BODY"

read -r -p "Push $TAG to $REMOTE and trigger the release build? [y/N] " reply
[[ "$reply" =~ ^[Yy]$ ]] || die "aborted — nothing was tagged"

git tag -a "$TAG" -m "$TAG" -m "$BODY"
git push "$REMOTE" "$TAG"

note "pushed. Watch the build:"
echo "  https://github.com/Dalakoti07/-Vibe-Coding-Speech-to-Text/actions"
echo "  https://github.com/Dalakoti07/-Vibe-Coding-Speech-to-Text/releases/tag/$TAG"
