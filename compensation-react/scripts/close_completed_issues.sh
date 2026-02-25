#!/usr/bin/env bash
set -euo pipefail

# 关闭已完成的 Issue 并追加进度评论
# 依赖：GitHub CLI (gh)，已 login

REPO="${REPO:-}"

if ! command -v gh >/dev/null 2>&1; then
  echo "[错误] 需要 GitHub CLI (gh)" >&2
  exit 1
fi
if ! gh auth status >/dev/null 2>&1; then
  echo "[错误] 请先 gh auth login" >&2
  exit 1
fi

if [[ -z "$REPO" ]]; then
  if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    REPO=$(git remote get-url origin 2>/dev/null | sed -E 's#(git@|https://)github.com[:/](.*)\.git#\2#')
  else
    echo "[错误] 未在仓库内，且未设置 REPO=owner/repo" >&2
    exit 1
  fi
fi

close_by_title() {
  local title="$1"; shift
  local comment="$1"; shift
  local num
  num=$(gh issue list --repo "$REPO" --state open --search "\"$title\" in:title" --json number,title | jq -r '.[0].number // empty')
  if [[ -z "$num" ]]; then
    echo "未找到打开状态的 Issue：$title" >&2
    return 0
  fi
  gh issue comment "$num" --repo "$REPO" --body "$comment"
  gh issue close "$num" --repo "$REPO"
  echo "已关闭：#$num $title"
}

close_by_title "接口契约对齐与 DTO 整理" "进度更新：已完成（Step 1）。类型与 Query Keys 已落地并应用。"
close_by_title "axios 错误处理与全局提示" "进度更新：已完成（Step 2）。拦截器统一错误、全局消息已接入。"
close_by_title "OAuth 回调对接" "进度更新：已完成（Step 3）。回调获取会话并跳转首页，异常提示。"
close_by_title "路由守卫与 403 页面" "进度更新：已完成。新增 /403 页面，守卫缺权跳转。"

echo "完成。"

