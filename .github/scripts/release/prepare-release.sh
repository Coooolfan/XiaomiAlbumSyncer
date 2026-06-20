#!/usr/bin/env bash
set -euo pipefail

SEMVER_REGEX='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-(alpha|beta|rc)-(0|[1-9][0-9]*))?$'
if [[ ! "$INPUT_VERSION" =~ $SEMVER_REGEX ]]; then
  echo "::error::版本号必须是 0.1.0 或 0.1.0-rc-1 形式，预发布标签仅允许 alpha、beta、rc"
  exit 1
fi

if [[ "$INPUT_VERSION" == v* ]]; then
  echo "::error::发布 tag 不使用 v 前缀，请输入 0.1.0 而不是 v0.1.0"
  exit 1
fi

case "$INPUT_LATEST" in
  true | false) ;;
  *)
    echo "::error::latest 参数必须是 true 或 false，当前为 $INPUT_LATEST"
    exit 1
    ;;
esac

NOTES_FILE="docs/release_notes/${INPUT_VERSION}.md"
if [[ ! -f "$NOTES_FILE" ]]; then
  echo "::error::Release notes 文件不存在: $NOTES_FILE"
  exit 1
fi

git fetch --force --tags origin
TARGET_SHA="$(git rev-parse --verify HEAD)"
if [[ "$INPUT_VERSION" == *-* ]]; then
  PRERELEASE=true
else
  PRERELEASE=false
fi

if [[ "$PRERELEASE" == "true" && "$INPUT_LATEST" == "true" ]]; then
  echo "::error::预发布版本不能发布为 latest。请取消 latest，或使用稳定版版本号"
  exit 1
fi

EXISTING_TAG_SHA="$(git rev-parse --verify --quiet "refs/tags/$INPUT_VERSION^{}" || true)"
if [[ -n "$EXISTING_TAG_SHA" ]]; then
  if [[ "$EXISTING_TAG_SHA" != "$TARGET_SHA" ]]; then
    echo "::error::tag $INPUT_VERSION 已存在，但指向 $EXISTING_TAG_SHA，不是目标 $TARGET_SHA"
    exit 1
  fi

  if RELEASE_DRAFT="$(gh release view "$INPUT_VERSION" --json isDraft --jq .isDraft 2>/dev/null)"; then
    if [[ "$RELEASE_DRAFT" != "true" ]]; then
      echo "::error::Release $INPUT_VERSION 已发布，不能重跑同版本发布"
      exit 1
    fi
    echo "::notice::发现同版本草稿 Release，将复用该草稿"
  else
    echo "::notice::发现同版本 tag 但没有 Release，将创建草稿 Release"
  fi
fi

if [[ "$INPUT_LATEST" == "true" ]]; then
  git fetch --force origin main
  MAIN_SHA="$(git rev-parse --verify origin/main)"
  if [[ "$TARGET_SHA" != "$MAIN_SHA" ]]; then
    echo "::error::latest 只能从 origin/main 当前 HEAD 发布。workflow_ref=$GITHUB_REF_NAME target_sha=$TARGET_SHA origin/main=$MAIN_SHA"
    exit 1
  fi
fi

{
  echo "version=$INPUT_VERSION"
  echo "target_sha=$TARGET_SHA"
  echo "prerelease=$PRERELEASE"
  echo "publish_latest=$INPUT_LATEST"
} >> "$GITHUB_OUTPUT"
