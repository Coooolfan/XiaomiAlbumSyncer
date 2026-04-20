#!/usr/bin/env bash
set -euo pipefail

pagesize=0
offset=0
show_all=0

usage() {
  cat <<'EOF'
Usage:
  git-tags.sh [--pagesize N] [--offset N]
  git-tags.sh [-p N] [-o N]
  git-tags.sh --all

Examples:
  ./git-tags.sh
  ./git-tags.sh --pagesize 10
  ./git-tags.sh --pagesize 10 --offset 20
  ./git-tags.sh --all

Notes:
  - Standard release tags are sorted by version precedence DESC (highest first):
    Release > RC > Beta > Alpha.
  - Non-standard tags fall back to creatordate DESC.
  - For annotated tags, tag_time = taggerdate.
  - For lightweight tags, tag_time falls back to the target commit's committer date,
    because lightweight tags do not store their own timestamp.
EOF
}

is_nonneg_int() {
  [[ "$1" =~ ^[0-9]+$ ]]
}

normalize_one_line() {
  printf '%s' "$1" \
    | tr '\r\n\t' '   ' \
    | sed -E 's/[[:space:]]+/ /g; s/^ //; s/ $//'
}

build_sort_key() {
  local tag="$1"
  local major minor patch prerelease_num stage_rank created_at

  if [[ "$tag" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    major="${BASH_REMATCH[1]}"
    minor="${BASH_REMATCH[2]}"
    patch="${BASH_REMATCH[3]}"
    stage_rank=3
    prerelease_num=0
    printf '1\t%09d\t%09d\t%09d\t%02d\t%09d\t%010d' \
      "$major" "$minor" "$patch" "$stage_rank" "$prerelease_num" 0
    return
  fi

  if [[ "$tag" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)-rc-([0-9]+)$ ]]; then
    major="${BASH_REMATCH[1]}"
    minor="${BASH_REMATCH[2]}"
    patch="${BASH_REMATCH[3]}"
    prerelease_num="${BASH_REMATCH[4]}"
    stage_rank=2
    printf '1\t%09d\t%09d\t%09d\t%02d\t%09d\t%010d' \
      "$major" "$minor" "$patch" "$stage_rank" "$prerelease_num" 0
    return
  fi

  if [[ "$tag" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)-beta-([0-9]+)$ ]]; then
    major="${BASH_REMATCH[1]}"
    minor="${BASH_REMATCH[2]}"
    patch="${BASH_REMATCH[3]}"
    prerelease_num="${BASH_REMATCH[4]}"
    stage_rank=1
    printf '1\t%09d\t%09d\t%09d\t%02d\t%09d\t%010d' \
      "$major" "$minor" "$patch" "$stage_rank" "$prerelease_num" 0
    return
  fi

  if [[ "$tag" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)-alpha-([0-9]+)$ ]]; then
    major="${BASH_REMATCH[1]}"
    minor="${BASH_REMATCH[2]}"
    patch="${BASH_REMATCH[3]}"
    prerelease_num="${BASH_REMATCH[4]}"
    stage_rank=0
    printf '1\t%09d\t%09d\t%09d\t%02d\t%09d\t%010d' \
      "$major" "$minor" "$patch" "$stage_rank" "$prerelease_num" 0
    return
  fi

  created_at="$(git for-each-ref --format='%(creatordate:unix)' "refs/tags/$tag")"
  [[ -n "$created_at" ]] || created_at=0
  printf '0\t%09d\t%09d\t%09d\t%02d\t%09d\t%010d' 0 0 0 0 0 "$created_at"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --pagesize|-p)
      [[ $# -ge 2 ]] || { echo "missing value for $1" >&2; exit 1; }
      pagesize="$2"
      shift 2
      ;;
    --offset|-o)
      [[ $# -ge 2 ]] || { echo "missing value for $1" >&2; exit 1; }
      offset="$2"
      shift 2
      ;;
    --all)
      show_all=1
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

is_nonneg_int "$pagesize" || { echo "pagesize must be a non-negative integer" >&2; exit 1; }
is_nonneg_int "$offset"   || { echo "offset must be a non-negative integer" >&2; exit 1; }

if (( show_all == 1 )); then
  pagesize=0
  offset=0
fi

git rev-parse --git-dir >/dev/null 2>&1 || {
  echo "not inside a git repository" >&2
  exit 1
}

all_tags=()
while IFS= read -r line; do
  all_tags+=("${line##*$'\t'}")
done < <(
  while IFS= read -r tag; do
    printf '%s\t%s\n' "$(build_sort_key "$tag")" "$tag"
  done < <(git for-each-ref --format='%(refname:short)' refs/tags) \
    | sort -t $'\t' -k1,1nr -k2,2nr -k3,3nr -k4,4nr -k5,5nr -k6,6nr -k7,7nr
)

total="${#all_tags[@]}"
if (( total == 0 )); then
  echo "no tags found"
  exit 0
fi

if (( offset >= total )); then
  echo "offset ($offset) is out of range; total tags: $total" >&2
  exit 0
fi

if (( pagesize == 0 )); then
  slice=( "${all_tags[@]:offset}" )
else
  slice=( "${all_tags[@]:offset:pagesize}" )
fi

output() {
  printf 'tag_name\ttag_type\ttag_description\ttag_time\tcommit_time\n'

  local tag raw_type tag_type tag_time commit_time desc commit_id
  for tag in "${slice[@]}"; do
    raw_type="$(git cat-file -t "refs/tags/$tag")"

    if [[ "$raw_type" == "tag" ]]; then
      tag_type="annotated"
      tag_time="$(git for-each-ref --format='%(taggerdate:iso-strict)' "refs/tags/$tag")"
      desc="$(git for-each-ref --format='%(subject)' "refs/tags/$tag")"
    else
      tag_type="lightweight"
      tag_time="$(git for-each-ref --format='%(creatordate:iso-strict)' "refs/tags/$tag")"
      desc=""
    fi

    if commit_id="$(git rev-parse -q --verify "${tag}^{commit}" 2>/dev/null)"; then
      commit_time="$(git show -s --format='%cI' "$commit_id")"
    else
      commit_time="-"
    fi

    desc="$(normalize_one_line "$desc")"
    [[ -n "$tag_time" ]] || tag_time="-"

    printf '%s\t%s\t%s\t%s\t%s\n' \
      "$tag" "$tag_type" "$desc" "$tag_time" "$commit_time"
  done
}

if command -v column >/dev/null 2>&1; then
  output | column -t -s $'\t'
else
  output
fi
