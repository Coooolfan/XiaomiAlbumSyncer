#!/usr/bin/env bash
set -euo pipefail

git fetch --force --tags origin
EXISTING_TAG_SHA="$(git rev-parse --verify --quiet "refs/tags/$VERSION^{}" || true)"
if [[ -n "$EXISTING_TAG_SHA" ]]; then
  if [[ "$EXISTING_TAG_SHA" != "$TARGET_SHA" ]]; then
    echo "::error::tag $VERSION 已存在，但指向 $EXISTING_TAG_SHA，不是目标 $TARGET_SHA"
    exit 1
  fi
  echo "::notice::tag $VERSION 已存在且指向目标 commit，继续复用"
else
  git config user.name "github-actions[bot]"
  git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
  git tag -a "$VERSION" "$TARGET_SHA" -m "Release $VERSION"
  git push origin "$VERSION"
fi

NOTES_FILE="docs/release_notes/${VERSION}.md"
if [[ ! -f "$NOTES_FILE" ]]; then
  echo "::error::Release notes 文件不存在: $NOTES_FILE"
  exit 1
fi

if RELEASE_JSON="$(gh release view "$VERSION" --json isDraft,isPrerelease 2>/dev/null)"; then
  RELEASE_DRAFT="$(jq -r .isDraft <<< "$RELEASE_JSON")"
  RELEASE_PRERELEASE="$(jq -r .isPrerelease <<< "$RELEASE_JSON")"
  if [[ "$RELEASE_DRAFT" != "true" ]]; then
    echo "::error::Release $VERSION 已发布，不能复用"
    exit 1
  fi
  if [[ "$RELEASE_PRERELEASE" != "$PRERELEASE" ]]; then
    echo "::error::草稿 Release 的 prerelease=$RELEASE_PRERELEASE 与本次输入 prerelease=$PRERELEASE 不一致"
    exit 1
  fi
  echo "::notice::草稿 Release $VERSION 已存在，继续复用"
else
  RELEASE_ARGS=(
    --draft
    --verify-tag
    --target "$TARGET_SHA"
    --title "$VERSION"
    --notes-file "$NOTES_FILE"
  )
  if [[ "$PRERELEASE" == "true" ]]; then
    RELEASE_ARGS+=(--prerelease)
  fi
  gh release create "$VERSION" "${RELEASE_ARGS[@]}"
fi
