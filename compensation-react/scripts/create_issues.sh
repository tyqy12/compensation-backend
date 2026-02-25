#!/usr/bin/env bash
set -euo pipefail

# 批量创建项目 Issue（需安装并登录 GitHub CLI: gh）
# 用法：
#   ./scripts/create_issues.sh                # 在当前仓库创建
#   REPO=owner/repo ./scripts/create_issues.sh # 指定仓库
#   MILESTONE=v0.1 ./scripts/create_issues.sh  # 指定里程碑（可选）

REPO="${REPO:-}"
MILESTONE="${MILESTONE:-}"

if ! command -v gh >/dev/null 2>&1; then
  echo "[错误] 需要 GitHub CLI (gh)。安装参考: https://cli.github.com/" >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "[错误] 需要先执行 'gh auth login' 完成登录" >&2
  exit 1
fi

# 推断仓库（未显式传入 REPO 时）
if [[ -z "$REPO" ]]; then
  if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    echo "[错误] 未在 Git 仓库下，且未提供 REPO=owner/repo" >&2
    exit 1
  fi
  REPO=$(git remote get-url origin 2>/dev/null | sed -E 's#(git@|https://)github.com[:/](.*)\.git#\2#')
fi

echo "目标仓库: $REPO"

# 确保常用标签存在
create_label() {
  local name="$1"; local color="$2"; local desc="$3"
  if ! gh label list --repo "$REPO" --limit 1000 | awk '{print $1}' | grep -Fxq "$name"; then
    gh label create "$name" --color "$color" --description "$desc" --repo "$REPO" || true
  fi
}

create_label "type:task" "0e8a16" "任务 / Task"
create_label "type:feature" "1d76db" "功能 / Feature"

create_issue() {
  local title="$1"; shift
  local labels="$1"; shift
  local assignees="$1"; shift
  local body="$1"; shift || true

  local args=(issue create --repo "$REPO" --title "$title" --label "$labels" --assignee "$assignees" --body-file -)
  if [[ -n "$MILESTONE" ]]; then
    args+=(--milestone "$MILESTONE")
  fi
  echo "$body" | gh "${args[@]}"
}

issue_exists() {
  local title="$1"
  gh issue list --repo "$REPO" --state all --search "\"$title\" in:title" -L 1 --json title | grep -F "\"title\":\"$title\"" >/dev/null 2>&1
}

ensure_issue() {
  local title="$1"; shift
  local labels="$1"; shift
  local assignees="$1"; shift
  local body="$1"; shift || true
  if issue_exists "$title"; then
    echo "已存在：$title" >&2
  else
    create_issue "$title" "$labels" "$assignees" "$body"
  fi
}

echo "开始创建 Issue…"

ensure_issue "接口契约对齐与 DTO 整理" "type:task" "tyqy12" "$(cat <<'EOF'
目标：与后端确认 Swagger/OpenAPI；补齐 src/types/api.ts DTO、分页/筛选参数与错误码。

验收标准：
- [ ] DTO 覆盖现有页面需求
- [ ] 统一 code/message 约定并记录
- [ ] Mock 数据可驱动页面渲染

依赖：后端接口文档
EOF
)"

ensure_issue "axios 错误处理与全局提示" "type:task" "tyqy12" "$(cat <<'EOF'
目标：在 services/api.ts 增加错误转换与统一 message/notification 规则。

验收标准：
- [ ] 接口异常显示易懂提示
- [ ] 401 自动清理并引导登录
- [ ] 网络异常有兜底文案
EOF
)"

ensure_issue "登录/登出/刷新 Token 接入" "type:feature" "tyqy12" "$(cat <<'EOF'
目标：接入 /api/auth/login /refresh /logout；填充 auth.user。

验收标准：
- [ ] 登录成功跳转来源页
- [ ] 刷新生效一次且幂等
- [ ] 登出清理本地状态与 auth_token
依赖：契约对齐
EOF
)"

ensure_issue "OAuth 回调对接" "type:feature" "tyqy12" "$(cat <<'EOF'
目标：完善 /oauth/callback/:platform 处理与异常分支（state 校验失败/未绑定）。

验收标准：
- [ ] 回调可获得会话
- [ ] 异常提示清晰，可重试登录
EOF
)"

ensure_issue "路由守卫与 403 页面" "type:task" "tyqy12" "$(cat <<'EOF'
目标：完善 ProtectedRoute 角色检查，新增 403 页面与返回首页链接。

验收标准：
- [ ] 无权限访问跳转 403
- [ ] 登录态丢失跳登录并保留来源
EOF
)"

ensure_issue "集成配置接入（查询/保存/测试）" "type:feature" "tyqy12" "$(cat <<'EOF'
目标：接入 /system/integration/{platform} GET/PUT 与 /test-connection。

验收标准：
- [ ] ProForm 校验完整
- [ ] 保存/测试有加载状态与结果提示
- [ ] 成功后刷新查询缓存
EOF
)"

ensure_issue "组织同步接入" "type:feature" "tyqy12" "$(cat <<'EOF'
目标：平台列表/检查/同步操作，按钮态管理。

验收标准：
- [ ] 同步中禁用控件
- [ ] 结果提示清晰
- [ ] 错误不阻塞其他操作
EOF
)"

ensure_issue "用户-平台绑定接入" "type:feature" "tyqy12" "$(cat <<'EOF'
目标：列表/绑定/解绑，支持分页与筛选。

验收标准：
- [ ] 状态实时更新
- [ ] 分页/筛选可用
- [ ] 空态/错误态统一
EOF
)"

ensure_issue "员工列表与详情接入" "type:feature" "tyqy12" "$(cat <<'EOF'
目标：分页、筛选、缓存策略；详情展示关键字段。

验收标准：
- [ ] 列表滚动/翻页性能良好
- [ ] 返回列表保留筛选与页码
EOF
)"

ensure_issue "支付批次列表与详情接入" "type:feature" "tyqy12" "$(cat <<'EOF'
目标：列表（状态/记录数）与详情时间线（创建/校验/处理等）。

验收标准：
- [ ] 状态与后端一致
- [ ] 详情由批次号驱动
- [ ] 错误可重试
EOF
)"

ensure_issue "单元/组件测试补齐" "type:task" "tyqy12" "$(cat <<'EOF'
目标：为核心模块（auth、integration、org、binding）补充 Vitest + RTL 用例。

验收标准：
- [ ] 覆盖率 ≥ 60%
- [ ] 关键分支与错误分支断言
EOF
)"

ensure_issue "端到端冒烟（可选）" "type:task" "tyqy12" "$(cat <<'EOF'
目标：登录、跳转、核心 CRUD 冒烟（Playwright）。

验收标准：
- [ ] CI 无头通过
- [ ] 失败截图保存为构件
EOF
)"

ensure_issue "性能与可访问性优化" "type:task" "tyqy12" "$(cat <<'EOF'
目标：路由分包、staleTime 策略、Skeleton；可访问性优化。

验收标准：
- [ ] 首屏稳定，无明显布局抖动
- [ ] 主要页面无关键 A11y 报警
EOF
)"

ensure_issue "CI/CD 与多环境配置" "type:task" "tyqy12" "$(cat <<'EOF'
目标：Actions 跑 lint+test+build；配置 .env.staging/.env.production。

验收标准：
- [ ] PR 自动检查通过
- [ ] 构建产物可预览
- [ ] 多环境变量正确注入
EOF
)"

ensure_issue "预发联调与上线" "type:task" "tyqy12" "$(cat <<'EOF'
目标：预发联调、灰度发布与监控接入；回滚预案。

验收标准：
- [ ] 预发冒烟通过
- [ ] 上线变更记录与回滚脚本
- [ ] 监控无异常
EOF
)"

echo "完成。"
