#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${OUT_DIR:-${ROOT_DIR}/软著申请材料}"

SOFTWARE_NAME="${SOFTWARE_NAME:-薪酬助手系统[简称：薪酬助手]V1.0}"
SOFTWARE_SHORT_NAME="${SOFTWARE_SHORT_NAME:-薪酬助手}"
SERIAL_NO="${SERIAL_NO:-2026R11L0373991}"
APPLICANT="${APPLICANT:-上海异云岛科技有限公司}"
VERSION="${VERSION:-V1.0}"

BACKEND_SRC_DIR="${ROOT_DIR}/src/main/java/com/yiyundao/compensation"
BACKEND_CONTROLLER_DIR="${BACKEND_SRC_DIR}/interfaces/controller"
BACKEND_MAPPER_DIR="${ROOT_DIR}/src/main/resources/mapper"
BACKEND_RESOURCE_DIR="${ROOT_DIR}/src/main/resources"
BACKEND_SQL_DIR="${BACKEND_RESOURCE_DIR}/sql"
FRONTEND_SRC_DIR="${ROOT_DIR}/compensation-react/src"
SOURCE_OUT_DIR="${OUT_DIR}/源代码"
DOC_OUT_DIR="${OUT_DIR}/文档"
FORM_OUT_DIR="${OUT_DIR}/表格"

mkdir -p "${OUT_DIR}" "${SOURCE_OUT_DIR}" "${DOC_OUT_DIR}" "${FORM_OUT_DIR}"

trim_count() {
    tr -d '[:space:]'
}

count_lines_in_glob() {
    local target_dir="$1"
    shift
    find "${target_dir}" -type f "$@" -print0 | xargs -0 cat | wc -l | trim_count
}

count_files_in_glob() {
    local target_dir="$1"
    shift
    find "${target_dir}" -type f "$@" | wc -l | trim_count
}

list_backend_source_files() {
    {
        printf '%s\n' "${BACKEND_SRC_DIR}/CompensationAssistantSystemApplication.java"
        find "${BACKEND_SRC_DIR}" -type f -name "*.java" | sort
        find "${BACKEND_MAPPER_DIR}" -type f -name "*.xml" | sort
    } | awk '!seen[$0]++' | while IFS= read -r file; do
        if [[ -f "${file}" ]]; then
            printf '%s\n' "${file}"
        fi
    done
}

list_backend_java_files() {
    find "${BACKEND_SRC_DIR}" -type f -name "*.java" | sort
}

list_frontend_source_files() {
    find "${FRONTEND_SRC_DIR}" -type f \( -name "*.ts" -o -name "*.tsx" \) | sort
}

relpath() {
    local file="$1"
    printf '%s\n' "${file#"${ROOT_DIR}/"}"
}

render_text_pdf() {
    local input_file="$1"
    local output_file="$2"
    local lines_per_page="$3"
    local orientation="$4"
    local font_stack="$5"
    local font_size_px="$6"
    local line_height_px="$7"
    local title="$8"
    local header_text="$9"
    local mode="${10}"
    local add_cover="${11}"
    local tmp_html
    tmp_html="$(mktemp --suffix=.html)"
    SC_SOFTWARE_NAME="${SOFTWARE_NAME}" \
    SC_APPLICANT="${APPLICANT}" \
    SC_SERIAL_NO="${SERIAL_NO}" \
    SC_VERSION="${VERSION}" \
    SC_DOC_DATE="$(date '+%Y年%-m月')" \
    python3 - "${input_file}" "${tmp_html}" "${lines_per_page}" "${font_stack}" "${font_size_px}" "${line_height_px}" "${title}" "${header_text}" "${mode}" "${add_cover}" <<'PY'
import html
import os
import pathlib
import sys
import re
import unicodedata

input_path = pathlib.Path(sys.argv[1])
output_html = pathlib.Path(sys.argv[2])
lines_per_page = int(sys.argv[3])
font_stack = sys.argv[4]
font_size = int(sys.argv[5])
line_height = int(sys.argv[6])
title = sys.argv[7]
header_text = sys.argv[8]
mode = sys.argv[9]
add_cover = sys.argv[10].lower() == "true"
software_name = os.environ.get("SC_SOFTWARE_NAME", "")
applicant = os.environ.get("SC_APPLICANT", "")
serial_no = os.environ.get("SC_SERIAL_NO", "")
version = os.environ.get("SC_VERSION", "")
doc_date = os.environ.get("SC_DOC_DATE", "")

lines = input_path.read_text(encoding="utf-8").splitlines()
if not lines:
    lines = [""]

def char_width(ch: str) -> int:
    if ch == "\t":
        return 4
    return 2 if unicodedata.east_asian_width(ch) in {"W", "F"} else 1

def wrap_by_visual_width(text: str, width: int, prefix: str = "", continuation: str = ""):
    if not text:
        return [prefix.rstrip()]
    result = []
    current = prefix
    current_width = sum(char_width(ch) for ch in prefix)
    first_prefix_width = current_width
    for ch in text:
        w = char_width(ch)
        if current_width + w > width and current.strip():
            result.append(current.rstrip())
            current = continuation
            current_width = sum(char_width(c) for c in continuation)
        current += ch
        current_width += w
    if current.strip() or not result:
        result.append(current.rstrip())
    return result

def normalize_doc_lines(src_lines):
    wrapped = []
    for line in src_lines:
        if not line.strip():
            wrapped.append("")
            continue
        if line.startswith("```") or line.startswith("    "):
            wrapped.append(line)
            continue
        stripped = line.lstrip()
        leading = line[: len(line) - len(stripped)]
        if stripped.startswith(("- ", "* ")):
            prefix = leading + stripped[:2]
            continuation = leading + "  "
            wrapped.extend(wrap_by_visual_width(stripped[2:], 88, prefix, continuation))
            continue
        number_match = re.match(r"(\d+\.\s+)(.*)", stripped)
        if number_match:
            prefix = leading + number_match.group(1)
            continuation = " " * len(prefix)
            wrapped.extend(wrap_by_visual_width(number_match.group(2), 88, prefix, continuation))
            continue
        wrapped.extend(wrap_by_visual_width(line, 92))
    return wrapped

if mode == "doc":
    lines = normalize_doc_lines(lines)

pages = [lines[i:i + lines_per_page] for i in range(0, len(lines), lines_per_page)]
for page in pages:
    if len(page) < lines_per_page:
        page.extend([""] * (lines_per_page - len(page)))

html_pages = []
total_pages = len(pages)

if add_cover:
    html_pages.append(
        '<section class="cover-page">'
        '<div class="cover-inner">'
        '<div class="cover-topline">计算机软件著作权登记材料</div>'
        f'<div class="cover-main-title">{html.escape(title)}</div>'
        f'<div class="cover-software">{html.escape(header_text)}</div>'
        '<div class="cover-rule"></div>'
        '<div class="cover-info">'
        f'<div><span>软件名称：</span><span>{html.escape(software_name or header_text.replace(" 软件设计开发说明书", ""))}</span></div>'
        f'<div><span>申请人：</span><span>{html.escape(applicant)}</span></div>'
        f'<div><span>流水号：</span><span>{html.escape(serial_no)}</span></div>'
        f'<div><span>版本号：</span><span>{html.escape(version)}</span></div>'
        '<div><span>文档性质：</span><span>补正提交版</span></div>'
        f'<div><span>编制日期：</span><span>{html.escape(doc_date)}</span></div>'
        '</div></div></section>'
    )

for page_index, page in enumerate(pages, start=1):
    rendered = []
    for line in page:
        text = line if line else " "
        rendered.append(f'<div class="line">{html.escape(text)}</div>')
    html_pages.append(
        '<section class="page">'
        f'<div class="page-header"><span class="header-title">{html.escape(header_text)}</span>'
        f'<span class="header-page">第 {page_index} 页 / 共 {total_pages} 页</span></div>'
        '<div class="lines">' + "".join(rendered) + '</div>'
        f'<div class="page-footer">{html.escape(title)}</div>'
        '</section>'
    )

doc = f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <title>{html.escape(title)}</title>
  <style>
    @page {{
      size: A4 landscape;
      margin: 0;
    }}
    html, body {{
      margin: 0;
      padding: 0;
      background: #fff;
    }}
    body {{
      font-family: {font_stack};
      -webkit-print-color-adjust: exact;
      print-color-adjust: exact;
      color: #222;
    }}
    .page {{
      width: 297mm;
      min-height: 210mm;
      box-sizing: border-box;
      padding: 7mm 10mm 6mm 10mm;
      page-break-after: always;
      overflow: hidden;
      display: flex;
      flex-direction: column;
    }}
    .page:last-child {{
      page-break-after: auto;
    }}
    .cover-page {{
      width: 297mm;
      min-height: 210mm;
      box-sizing: border-box;
      padding: 0;
      page-break-after: always;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #fff;
    }}
    .cover-inner {{
      width: 74%;
      min-height: 150mm;
      text-align: center;
      border: 1.5px solid #333;
      padding: 16mm 14mm 18mm 14mm;
      box-sizing: border-box;
    }}
    .cover-topline {{
      font-size: 16px;
      letter-spacing: 2px;
      margin-bottom: 16mm;
      color: #444;
    }}
    .cover-main-title {{
      font-size: 28px;
      font-weight: 700;
      line-height: 1.5;
      margin-bottom: 6mm;
    }}
    .cover-software {{
      font-size: 18px;
      line-height: 1.7;
      margin-bottom: 12mm;
      color: #222;
    }}
    .cover-rule {{
      width: 72%;
      height: 1px;
      background: #666;
      margin: 0 auto 12mm auto;
    }}
    .cover-info {{
      width: 100%;
      display: grid;
      grid-template-columns: 1fr;
      gap: 4mm;
      text-align: left;
      font-size: 15px;
      line-height: 1.8;
      padding: 0 8mm;
      box-sizing: border-box;
    }}
    .cover-info span:first-child {{
      display: inline-block;
      width: 30mm;
      text-align: right;
      font-weight: 600;
    }}
    .cover-info span:last-child {{
      padding-left: 2mm;
    }}
    .page-header {{
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid #777;
      padding-bottom: 2mm;
      margin-bottom: 3mm;
      font-size: 12px;
      min-height: 8mm;
    }}
    .header-title {{
      font-weight: 600;
    }}
    .header-page {{
      color: #444;
    }}
    .lines {{
      width: 100%;
      flex: 1 1 auto;
    }}
    .line {{
      font-size: {font_size}px;
      line-height: {line_height}px;
      height: {line_height}px;
      white-space: pre;
      overflow: hidden;
      word-break: keep-all;
    }}
    .page-footer {{
      border-top: 1px solid #bbb;
      padding-top: 2mm;
      margin-top: 3mm;
      text-align: center;
      font-size: 11px;
      color: #555;
      min-height: 6mm;
    }}
  </style>
</head>
<body>
{''.join(html_pages)}
</body>
</html>
"""
output_html.write_text(doc, encoding="utf-8")
PY
    wkhtmltopdf \
        --enable-local-file-access \
        --encoding utf-8 \
        --page-size A4 \
        --orientation "${orientation^}" \
        --margin-top 0 \
        --margin-bottom 0 \
        --margin-left 0 \
        --margin-right 0 \
        --title "${title}" \
        "${tmp_html}" "${output_file}" >/dev/null 2>&1
    rm -f "${tmp_html}"
}

extract_page_count() {
    local pdf_file="$1"
    pdfinfo "${pdf_file}" | awk '/^Pages:/ {print $2}'
}

render_formal_cover_pdf() {
    local output_file="$1"
    local tmp_html
    tmp_html="$(mktemp --suffix=.html)"
    cat > "${tmp_html}" <<EOF
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <title>${SOFTWARE_NAME} 软件设计开发说明书</title>
  <style>
    html, body {
      margin: 0;
      padding: 0;
      background: #fff;
      font-family: 'WenQuanYi Micro Hei','Droid Sans Fallback',sans-serif;
      color: #222;
    }
    .page {
      width: 297mm;
      min-height: 210mm;
      box-sizing: border-box;
      padding: 18mm 22mm;
    }
    .box {
      border: 1.5px solid #333;
      min-height: 168mm;
      box-sizing: border-box;
      padding: 18mm 20mm;
    }
    .topline {
      text-align: center;
      font-size: 15px;
      letter-spacing: 2px;
      color: #444;
      margin-bottom: 18mm;
    }
    .doctype {
      text-align: center;
      font-size: 28px;
      font-weight: 700;
      line-height: 1.6;
      margin-bottom: 10mm;
    }
    .softname {
      text-align: center;
      font-size: 18px;
      line-height: 1.8;
      margin-bottom: 14mm;
    }
    .divider {
      width: 70%;
      margin: 0 auto 16mm auto;
      border-top: 1px solid #666;
    }
    .info {
      width: 76%;
      margin: 0 auto;
      font-size: 15px;
      line-height: 2.1;
    }
    .row {
      white-space: nowrap;
    }
    .label {
      display: inline-block;
      width: 32mm;
      text-align: right;
      font-weight: 600;
      vertical-align: top;
    }
    .value {
      display: inline-block;
      width: 120mm;
      padding-left: 4mm;
      vertical-align: top;
    }
  </style>
</head>
<body>
  <section class="page">
    <div class="box">
      <div class="topline">计算机软件著作权登记补正材料</div>
      <div class="doctype">软件设计开发说明书</div>
      <div class="softname">${SOFTWARE_NAME}</div>
      <div class="divider"></div>
      <div class="info">
        <div class="row"><span class="label">申请人：</span><span class="value">${APPLICANT}</span></div>
        <div class="row"><span class="label">流水号：</span><span class="value">${SERIAL_NO}</span></div>
        <div class="row"><span class="label">版本号：</span><span class="value">${VERSION}</span></div>
        <div class="row"><span class="label">文档性质：</span><span class="value">补正提交版</span></div>
        <div class="row"><span class="label">编制日期：</span><span class="value">$(date '+%Y年%-m月')</span></div>
      </div>
    </div>
  </section>
</body>
</html>
EOF
    wkhtmltopdf \
        --enable-local-file-access \
        --encoding utf-8 \
        --page-size A4 \
        --orientation Landscape \
        --margin-top 0 \
        --margin-bottom 0 \
        --margin-left 0 \
        --margin-right 0 \
        --title "软件设计开发说明书" \
        "${tmp_html}" "${output_file}" >/dev/null 2>&1
    rm -f "${tmp_html}"
}

backend_java_file_count="$(count_files_in_glob "${BACKEND_SRC_DIR}" -name "*.java")"
backend_java_line_count="$(count_lines_in_glob "${BACKEND_SRC_DIR}" -name "*.java")"
backend_mapper_file_count="$(count_files_in_glob "${BACKEND_MAPPER_DIR}" -name "*.xml")"
backend_mapper_line_count="$(count_lines_in_glob "${BACKEND_MAPPER_DIR}" -name "*.xml")"
frontend_file_count="$(count_files_in_glob "${FRONTEND_SRC_DIR}" \( -name "*.ts" -o -name "*.tsx" \))"
frontend_line_count="$(count_lines_in_glob "${FRONTEND_SRC_DIR}" \( -name "*.ts" -o -name "*.tsx" \))"
controller_file_count="$(find "${BACKEND_CONTROLLER_DIR}" -type f -name "*Controller.java" | wc -l | trim_count)"
service_file_count="$(find "${BACKEND_SRC_DIR}" -type f \( -name "*Service.java" -o -name "*ServiceImpl.java" \) | wc -l | trim_count)"
entity_file_count="$(find "${BACKEND_SRC_DIR}" -type f -path "*/entity/*.java" | wc -l | trim_count)"
table_count="$(rg '^CREATE TABLE' "${BACKEND_SQL_DIR}/schema.sql" | wc -l | trim_count)"
migration_file_count="$(find "${BACKEND_SQL_DIR}/migrations" -type f -name "*.sql" | wc -l | trim_count)"
backend_total_count="$((backend_java_file_count + backend_mapper_file_count))"
backend_total_lines="$((backend_java_line_count + backend_mapper_line_count))"

SOURCE_FULL_FILE="${SOURCE_OUT_DIR}/源代码-完整汇总.txt"
SOURCE_FRONT_FILE="${SOURCE_OUT_DIR}/源代码-前30页.txt"
SOURCE_BACK_FILE="${SOURCE_OUT_DIR}/源代码-后30页.txt"
SOURCE_SUBMISSION_FILE="${SOURCE_OUT_DIR}/源代码-前后60页.txt"
SOURCE_PDF_FILE="${SOURCE_OUT_DIR}/源代码-前后60页.pdf"
DESIGN_DOC_FILE="${DOC_OUT_DIR}/软件设计开发说明书.md"
DESIGN_DOC_FRONT_FILE="${DOC_OUT_DIR}/软件设计开发说明书-前30页.txt"
DESIGN_DOC_BACK_FILE="${DOC_OUT_DIR}/软件设计开发说明书-后30页.txt"
DESIGN_DOC_SUBMISSION_FILE="${DOC_OUT_DIR}/软件设计开发说明书-前后60页.txt"
DESIGN_DOC_PDF_FILE="${DOC_OUT_DIR}/软件设计开发说明书-前后60页.pdf"
DESIGN_DOC_FORMAL_PDF_FILE="${DOC_OUT_DIR}/软件设计开发说明书-正式版.pdf"
SUPPLEMENT_NOTE_FILE="${OUT_DIR}/补正说明.md"
CHECKLIST_FILE="${OUT_DIR}/补正提交检查清单.md"
MATERIAL_INDEX_FILE="${OUT_DIR}/申请材料清单.md"

TMP_SOURCE_RAW="$(mktemp)"
trap 'rm -f "${TMP_SOURCE_RAW}"' EXIT

> "${TMP_SOURCE_RAW}"
while IFS= read -r file; do
    printf '// ===== FILE: %s =====\n' "$(relpath "${file}")" >> "${TMP_SOURCE_RAW}"
    sed $'s/\t/    /g' "${file}" >> "${TMP_SOURCE_RAW}"
    printf '\n' >> "${TMP_SOURCE_RAW}"
done < <(list_backend_source_files)

nl -ba -w6 -s'  ' "${TMP_SOURCE_RAW}" > "${SOURCE_FULL_FILE}"
sed -n '1,1500p' "${SOURCE_FULL_FILE}" > "${SOURCE_FRONT_FILE}"
tail -n 1500 "${SOURCE_FULL_FILE}" > "${SOURCE_BACK_FILE}"
cat "${SOURCE_FRONT_FILE}" "${SOURCE_BACK_FILE}" > "${SOURCE_SUBMISSION_FILE}"

source_full_lines="$(wc -l < "${SOURCE_FULL_FILE}" | trim_count)"
source_submission_lines="$(wc -l < "${SOURCE_SUBMISSION_FILE}" | trim_count)"

cat > "${DESIGN_DOC_FILE}" <<EOF
# ${SOFTWARE_NAME} 软件设计开发说明书

## 1. 文档基本信息

### 1.1 文档用途

本文档用于配合计算机软件著作权登记补正，作为《${SOFTWARE_SHORT_NAME}》${VERSION} 的设计开发文档鉴别材料提交。文档结合当前实际代码仓库、数据库脚本、接口定义、运行配置和前端工程结构编制，用于说明软件的主要功能、技术特点、系统架构、数据库设计、接口设计、核心流程及源程序规模。

### 1.2 申请信息

- 软件名称：${SOFTWARE_NAME}
- 申请人：${APPLICANT}
- 流水号：${SERIAL_NO}
- 登记类型：计算机软件著作权登记
- 文档版本：补正提交版
- 编制依据：当前仓库 compensation-backend、后端源码、前端源码、配置文件、SQL 脚本和接口定义

### 1.3 补正目标

本次补正针对以下两项审查意见展开：

1. 源程序提交页数不足，需要补充为前 30 页、后 30 页，共 60 页，且每页不少于 50 行。
2. 文档鉴别材料过于简略，需要提供更详细的使用说明或设计开发文档，并结合申请表中的主要功能、技术特点和源程序量进行说明。

本文档采用“设计开发说明书”路线处理第二项缺陷，原因如下：

1. 当前软件已形成完整的前后端系统、数据库模型、审批与支付等关键业务模块，设计文档更能体现软件的完整性。
2. 设计开发文档可以自然地关联软件主要功能、技术架构、数据库设计、接口设计、关键类与模块，适合解释源码体量与技术特点。
3. 相较于简短操作手册，设计开发文档更容易扩展为足够详实的连续送审页数。

## 2. 软件概述

### 2.1 建设背景

${SOFTWARE_SHORT_NAME} 面向企业薪酬管理、人员资料管理、发薪审批、薪资确认、批量结算和平台化集成等业务场景建设。系统以 B/S 架构为基础，提供后端业务服务、管理后台前端、第三方平台接口和开放接口，覆盖以下核心业务需求：

1. 员工基础信息、架构外员工和离职员工管理。
2. 薪资模板、薪资项、发薪周期、薪资批次和工资条管理。
3. 薪资导入、核算、确认、异议处理、审批流转和发放对账。
4. 支付批次、支付记录、结算渠道配置和回调处理。
5. 企业微信、钉钉、飞书等组织架构同步与平台账号绑定。
6. 管理员、角色、资源、授权关系和 RBAC 权限控制。
7. 审计日志、系统配置、接口集成配置和监控查询。

### 2.2 软件定位

该软件定位为企业级薪酬协同管理系统，强调薪酬业务链条的闭环管理。系统并非单一的工资表导入工具，而是围绕“数据采集 -> 薪资核算 -> 员工确认 -> 审批流转 -> 结算发放 -> 查询回执 -> 审计追踪”建立完整链路。

### 2.3 目标用户

- 企业管理员：负责系统配置、平台接入、用户授权和全局参数维护。
- 薪酬专员：负责发薪周期维护、模板配置、导入数据和发起薪资批次。
- 审批人员：负责批量发薪、异议处理、权限申请等审批流程。
- 财务人员：负责支付批次复核、结算执行和异常重试。
- 员工个人：查看本人信息、提交联系方式变更、确认工资条或提出异议。
- 开放平台调用方：通过开放接口查询工资条、工资批次等对外数据。

## 3. 运行环境与技术栈

### 3.1 后端运行环境

- 开发语言：Java 17
- 核心框架：Spring Boot 3.5.6
- Web 框架：Spring Web MVC
- 安全框架：Spring Security 6 + JWT
- 数据访问：MyBatis-Plus 3.5.5
- 缓存与会话：Redis
- 消息组件：RabbitMQ
- 调度组件：Quartz / Spring Scheduling
- 数据库：MySQL 8.0
- 监控指标：Micrometer + Prometheus
- 链路追踪：Brave / Zipkin
- 对象存储：MinIO

### 3.2 前端运行环境

- 前端语言：TypeScript
- 前端框架：React
- 构建工具：Vite
- UI 组件：Ant Design
- 状态管理：Redux Toolkit + TanStack Query
- 路由管理：React Router
- 网络访问：Axios
- 测试框架：Vitest + React Testing Library

### 3.3 第三方能力集成

- 支付渠道：支付宝 SDK、云账户结算 SDK
- 企业组织平台：企业微信、钉钉、飞书
- 短信能力：阿里云短信
- 加密能力：Bouncy Castle、Commons Codec

### 3.4 技术特点摘要

1. 采用前后端分离的 B/S 架构，后端负责业务规则、权限与数据处理，前端负责交互页面和流程操作。
2. 采用模块化后端组织方式，围绕 employee、payroll、payment、approval、system、rbac、user 等业务域拆分代码。
3. 提供支付批次、支付记录与结算渠道路由能力，可兼容不同支付提供方。
4. 支持员工自助确认工资条和提出异议，结合审批引擎形成闭环。
5. 支持多平台组织架构同步、员工与外部身份绑定、开放接口对接和审计追踪。
6. 对身份证、收款账号等敏感字段进行加密处理，并限制高敏操作权限。

## 4. 系统总体架构

### 4.1 架构风格

${SOFTWARE_SHORT_NAME} 采用典型的前后端分离架构：

1. 表现层：React 管理后台，提供系统配置、员工管理、薪资管理、审批、支付和报表等页面。
2. 接口层：Spring MVC Controller 对外暴露 REST API、开放接口和第三方回调接口。
3. 业务层：以服务类为主，封装员工管理、薪资批次、审批引擎、支付执行、组织同步等业务逻辑。
4. 持久层：MyBatis-Plus Mapper 与 XML 映射文件完成数据库访问。
5. 基础设施层：配置、安全、日志、消息、调度、加密、通知和监控能力。

### 4.2 后端分层说明

- common/：公共配置、异常、响应包装、工具类、注解、指标和链路追踪。
- security/：JWT 令牌生成、校验、认证过滤器和安全常量。
- interfaces/controller/：控制器，按功能模块对外暴露接口。
- interfaces/dto/、interfaces/vo/：输入输出对象，隔离接口模型与实体模型。
- modules/*/service：业务服务接口和实现。
- modules/*/entity：领域实体和数据库持久化对象。
- infrastructure/dao/：MyBatis-Plus Mapper 接口。
- src/main/resources/mapper/：复杂 SQL 的 XML 映射文件。

### 4.3 前端结构说明

- app/：应用入口、主题和 Provider 组装。
- routes/：路由配置与权限守卫。
- pages/：按业务域划分的页面。
- components/：通用布局组件、导航组件、加载和空态组件。
- services/：API 请求封装、查询 hooks 和本地状态管理。
- hooks/：通用业务钩子。
- types/：统一的接口类型声明。

### 4.4 系统部署形态

系统支持本地开发运行、Docker 容器运行以及基于独立数据库、Redis、消息队列的服务化部署。运行时通过 application.yml 及多环境配置文件区分开发、预发和生产环境。

## 5. 主要功能设计

### 5.1 认证与授权

系统提供账号密码登录、开发环境令牌获取、第三方 OAuth 获取令牌等能力。后端通过 JWT 完成会话认证，通过角色与资源权限进行访问控制。典型角色包括管理员、经理、审批人等，接口通过 @PreAuthorize 或方法级权限判断保护敏感资源。

### 5.2 员工管理

员工模块负责管理员工基础资料、联系方式、部门归属、岗位状态、用工类型、架构外标识、负责人、平台身份和敏感金融信息。系统支持员工创建、更新、分页查询、详情查询、状态变更、平台绑定、自助查看资料、自助修改联系方式以及提交资料变更申请。

关键特点如下：

1. 对身份证号、收款账号、银行卡号等敏感字段进行加密存储。
2. 支持架构外员工和离职员工单独查询。
3. 支持员工与系统用户、外部平台身份进行绑定。
4. 支持员工个人发起资料变更审批。

### 5.3 组织同步与平台集成

系统通过 OrganizationAdapter 模式对接企业微信、钉钉、飞书等组织平台。管理员可检查连接状态、获取平台树、预览员工、执行同步或导入单个员工。平台集成配置支持读取、更新、删除、测试连接以及证书上传。

### 5.4 薪资基础配置

系统支持维护薪资项字典、薪资模板和发薪周期。薪资模板用于描述收入项、扣减项、税社保规则和工资条展示方式；发薪周期用于定义按月或自定义周期发薪的日历规则，为批次计算、确认和发放提供基础参数。

### 5.5 薪资批次与工资条

薪资批次模块负责建立本次发薪的业务容器。管理员可创建批次、更新批次、锁定批次、执行试算、执行计算、查询台账、查看经理复核数据，并在必要时重试支付。工资条模块支持分页查询、详情查询和下载。

### 5.6 薪资导入与人工补录

系统提供导入预览和提交接口，对导入数据进行校验、生成批次明细，并支持人工新增、修改或删除导入项。这样既满足批量导入场景，也满足个别员工补录和更正场景。

### 5.7 员工确认与异议处理

系统支持员工逐条确认工资条、按批次确认工资条以及对工资条提出异议。确认单记录整体确认状态、截止时间、人数统计和策略；确认记录记录具体员工、工资行和拒绝原因。若员工提出异议，可触发异议审批流程。

### 5.8 审批引擎

审批引擎支持批量支付、薪资发放、临时支付、架构外员工、权限申请和薪资异议等不同流程类型。流程启动时根据类型加载审批步骤配置并生成审批节点，流转过程中可进行通过、拒绝、撤销等操作，并通过事件机制在审批结束后触发后续处理。

### 5.9 支付批次与结算处理

系统将工资发放与支付执行解耦。薪资批次经审批和确认后，可生成支付批次与支付记录，由结算服务根据渠道配置和员工结算方式执行单笔或批量转账。系统支持批次预检、异步发放、回调处理、状态查询、失败重试和对账任务。

### 5.10 开放接口与外部访问

系统对外提供开放接口，包括令牌获取、健康探活、工资批次查询、工资条查询等能力。该部分接口适合对接外部门户、小程序或第三方业务系统。

### 5.11 审计、监控与管理后台

系统提供审计日志查询、系统监控汇总、角色管理、用户授权、资源管理、应用注册、用户绑定和集成配置管理等后台功能，用于保障软件运行可观测、可管理、可审计。

## 6. 核心流程设计

### 6.1 员工信息维护流程

1. 管理员录入员工资料。
2. 系统对手机号、邮箱、金融信息进行格式校验。
3. 系统对身份证号、结算账号等敏感数据执行加密。
4. 系统保存员工信息，并在需要时创建用户账号和平台绑定关系。
5. 员工后续可自助修改联系方式或发起资料变更申请。

### 6.2 薪资批次处理流程

1. 管理员创建发薪周期和薪资批次。
2. 导入工资项数据或人工补录。
3. 执行试算与正式计算，形成工资行数据。
4. 根据配置发起薪资审批或员工确认流程。
5. 审批完成、确认结束后进入支付执行阶段。
6. 支付完成后形成支付回执和状态更新。

### 6.3 员工确认与异议流程

1. 系统生成确认单和确认记录。
2. 员工查看待确认工资条。
3. 员工选择确认或提出异议。
4. 如确认，则更新确认记录和确认单汇总统计。
5. 如异议，则记录原因并创建薪资异议审批流程。

### 6.4 结算发放流程

1. 结算服务读取待处理支付记录。
2. 根据支付记录或员工结算配置解析结算渠道。
3. 发放前执行风险校验，检查金额、收款信息和必要字段。
4. 调用渠道适配器完成单笔转账或批量转账。
5. 持久化渠道返回结果，并处理回调或主动查询。
6. 统计批次发放状态，必要时触发通知和重试。

### 6.5 组织同步流程

1. 管理员维护第三方平台接入参数。
2. 系统测试平台连通性。
3. 通过适配器拉取平台部门树和员工信息。
4. 管理员预览后导入或直接同步。
5. 同步后更新本地员工数据和用户绑定关系。

## 7. 数据库设计

### 7.1 设计原则

1. 按业务域划分数据表，覆盖员工、薪资、支付、审批、通知、审计、系统配置和组织架构。
2. 关键表包含逻辑删除、版本号、创建时间、更新时间等基础治理字段。
3. 敏感业务表采用字段注释和命名约束，便于理解数据语义。
4. 通过迁移脚本持续演进数据库结构，避免直接在运行环境执行重建脚本。

### 7.2 核心表说明

- 员工域：employee、employee_department、external_identity
- 薪资域：salary_item、salary_template、pay_cycle、payroll_batch、payroll_line
- 确认域：payroll_confirmation、payroll_confirmation_record
- 分发域：payroll_distribution、payroll_distribution_item
- 支付域：payment_batch、payment_record、settlement_provider_config、settlement_reconciliation
- 审批域：approval_workflow、approval_step
- 系统域：sys_user、sys_config、integration_config、org_department
- 审计与通知域：audit_log、notification_record

### 7.3 数据表规模说明

当前数据库全量建表脚本 schema.sql 中定义了 ${table_count} 张核心业务表，另有 ${migration_file_count} 个增量迁移脚本，说明软件处于持续迭代状态，数据库设计并非单页简要说明可以覆盖。

## 8. 接口设计

### 8.1 接口组织方式

接口按业务域拆分为系统接口、员工接口、审批接口、薪资接口、支付接口、开放接口和后台管理接口。所有接口统一基于 /api 上下文路径提供服务，返回值使用统一响应结构包装。

### 8.2 典型接口类别

- 系统类：健康检查、系统信息、组织同步、平台配置、部门树。
- 认证类：登录、令牌获取、资源查询。
- 员工类：列表、详情、创建、更新、绑定、自助资料、联系方式修改。
- 薪资类：周期、模板、批次、导入、确认、工资条、报表、对账。
- 支付类：批次详情、批次预检、发放启动、记录重试、回调通知。
- 开放类：开放令牌、开放工资条查询、开放工资批次查询。
- 管理类：角色授权、资源管理、应用注册、监控、审计日志。

## 9. 安全设计

### 9.1 认证安全

系统通过 JWT 生成和校验访问令牌，在请求进入时由过滤器解析 Token 并建立认证上下文。开发环境提供开发令牌入口，生产环境通过正式登录接口获取令牌。

### 9.2 权限控制

系统采用角色与资源双层控制：

1. 角色控制用户总体职责范围，如管理员、审批人、经理。
2. 资源权限控制接口和菜单粒度访问。
3. 对解密接口、集成配置、授权管理等高敏能力额外限制。

### 9.3 敏感信息保护

系统对身份证号、收款账号、银行卡号等敏感信息进行加密存储，并在对外展示时通过 VO 层进行脱敏或限制访问。日志中避免打印明文敏感数据。

### 9.4 审计与追踪

系统通过审计日志记录关键操作，通过链路追踪与监控指标记录系统运行状态，便于排查问题和满足内部合规要求。

## 10. 异常处理与运行保障

### 10.1 异常处理

系统统一使用业务异常和全局异常处理器进行错误收敛。对于参数错误、资源不存在、权限不足、流程状态异常、支付失败等情况，均通过统一错误码和消息返回调用方。

### 10.2 日志与监控

系统配置了控制台与文件日志格式、应用级指标、Prometheus 暴露端点和链路追踪标签。支付、审批、组织同步等关键路径均带有明确日志输出，便于定位问题。

### 10.3 调度与异步

系统启用了异步执行和定时任务能力，用于批量支付、对账扫描、组织同步等场景，提升大批量处理效率。

## 11. 测试与质量保障

### 11.1 后端测试

仓库包含基于 JUnit 5 与 Spring Boot Test 的单元测试和接口测试，覆盖员工、授权、支付、薪资确认、开放接口、配置管理、工具类等关键模块。

### 11.2 前端测试

前端使用 Vitest 与 React Testing Library 对布局、守卫、页面组件、系统配置等部分进行测试，辅助保障前端交互稳定。

### 11.3 质量特征

1. 模块职责边界清晰，便于后续迭代。
2. 接口、数据库、配置、前端页面与后端服务形成完整闭环。
3. 测试和文档覆盖多个关键模块，具备工程化交付特征。

## 12. 源程序规模说明

### 12.1 后端规模

- Java 源文件数：${backend_java_file_count}
- Java 源码总行数：${backend_java_line_count}
- Mapper XML 文件数：${backend_mapper_file_count}
- Mapper XML 总行数：${backend_mapper_line_count}
- 后端交存源码合计文件数：${backend_total_count}
- 后端交存源码合计行数：${backend_total_lines}

### 12.2 前端规模

- TypeScript / TSX 文件数：${frontend_file_count}
- TypeScript / TSX 总行数：${frontend_line_count}

### 12.3 模块规模

- 控制器文件数：${controller_file_count}
- 服务类文件数：${service_file_count}
- 实体类文件数：${entity_file_count}
- 核心业务表数：${table_count}
- 增量迁移脚本数：${migration_file_count}

以上数据说明该软件已形成完整的工程代码体系，源程序量远超 60 页源码要求。为了满足软著交存规则，本次补正将单独生成适合提交的“前 30 页 + 后 30 页”源程序文件。

## 13. 附录 A：后端源码文件清单

\`\`\`text
EOF

list_backend_java_files | while IFS= read -r file; do
    relpath "${file}" >> "${DESIGN_DOC_FILE}"
done

cat >> "${DESIGN_DOC_FILE}" <<'EOF'
```

## 14. 附录 B：前端源码文件清单

```text
EOF

list_frontend_source_files | while IFS= read -r file; do
    relpath "${file}" >> "${DESIGN_DOC_FILE}"
done

cat >> "${DESIGN_DOC_FILE}" <<'EOF'
```

## 15. 附录 C：控制器与接口注解清单

```text
EOF

rg -n '@RequestMapping|@GetMapping|@PostMapping|@PutMapping|@PatchMapping|@DeleteMapping|class ' "${BACKEND_CONTROLLER_DIR}" \
    | sed "s#${ROOT_DIR}/##" >> "${DESIGN_DOC_FILE}"

cat >> "${DESIGN_DOC_FILE}" <<'EOF'
```

## 16. 附录 D：主配置文件摘录

### 16.1 Maven 依赖配置

```xml
EOF

sed -n '1,260p' "${ROOT_DIR}/pom.xml" >> "${DESIGN_DOC_FILE}"

cat >> "${DESIGN_DOC_FILE}" <<'EOF'
```

### 16.2 应用基础配置

```yaml
EOF

sed -n '1,260p' "${BACKEND_RESOURCE_DIR}/application.yml" >> "${DESIGN_DOC_FILE}"

cat >> "${DESIGN_DOC_FILE}" <<'EOF'
```

## 17. 附录 E：数据库结构脚本摘录

```sql
EOF

sed -n '1,920p' "${BACKEND_SQL_DIR}/schema.sql" >> "${DESIGN_DOC_FILE}"

cat >> "${DESIGN_DOC_FILE}" <<'EOF'
```

## 18. 附录 F：数据库迁移脚本清单

```text
EOF

find "${BACKEND_SQL_DIR}/migrations" -type f -name "*.sql" | sort | while IFS= read -r file; do
    relpath "${file}" >> "${DESIGN_DOC_FILE}"
done

cat >> "${DESIGN_DOC_FILE}" <<'EOF'
```

## 19. 附录 G：系统目录结构摘录

### 19.1 后端目录

```text
EOF

find "${BACKEND_SRC_DIR}" -maxdepth 3 -type d | sort | while IFS= read -r file; do
    relpath "${file}" >> "${DESIGN_DOC_FILE}"
done

cat >> "${DESIGN_DOC_FILE}" <<'EOF'
```

### 19.2 前端目录

```text
EOF

find "${FRONTEND_SRC_DIR}" -maxdepth 3 -type d | sort | while IFS= read -r file; do
    relpath "${file}" >> "${DESIGN_DOC_FILE}"
done

cat >> "${DESIGN_DOC_FILE}" <<'EOF'
```
EOF

design_doc_lines="$(wc -l < "${DESIGN_DOC_FILE}" | trim_count)"
sed -n '1,900p' "${DESIGN_DOC_FILE}" > "${DESIGN_DOC_FRONT_FILE}"
tail -n 900 "${DESIGN_DOC_FILE}" > "${DESIGN_DOC_BACK_FILE}"
cat "${DESIGN_DOC_FRONT_FILE}" "${DESIGN_DOC_BACK_FILE}" > "${DESIGN_DOC_SUBMISSION_FILE}"
design_doc_submission_lines="$(wc -l < "${DESIGN_DOC_SUBMISSION_FILE}" | trim_count)"

render_text_pdf "${SOURCE_SUBMISSION_FILE}" "${SOURCE_PDF_FILE}" 50 landscape "'WenQuanYi Zen Hei Mono','Droid Sans Fallback',monospace" 10 13 "软件源程序鉴别材料" "${SOFTWARE_NAME}" "code" "false"
render_text_pdf "${DESIGN_DOC_SUBMISSION_FILE}" "${DESIGN_DOC_PDF_FILE}" 30 landscape "'WenQuanYi Micro Hei','Droid Sans Fallback',sans-serif" 13 22 "软件文档鉴别材料" "${SOFTWARE_NAME} 软件设计开发说明书" "doc" "false"
TMP_FORMAL_BODY_PDF="$(mktemp --suffix=.pdf)"
TMP_FORMAL_COVER_PDF="$(mktemp --suffix=.pdf)"
render_text_pdf "${DESIGN_DOC_FILE}" "${TMP_FORMAL_BODY_PDF}" 30 landscape "'WenQuanYi Micro Hei','Droid Sans Fallback',sans-serif" 13 22 "软件设计开发说明书正式版" "${SOFTWARE_NAME} 软件设计开发说明书" "doc" "false"
render_formal_cover_pdf "${TMP_FORMAL_COVER_PDF}"
pdfunite "${TMP_FORMAL_COVER_PDF}" "${TMP_FORMAL_BODY_PDF}" "${DESIGN_DOC_FORMAL_PDF_FILE}"
rm -f "${TMP_FORMAL_BODY_PDF}" "${TMP_FORMAL_COVER_PDF}"

source_pdf_pages="$(extract_page_count "${SOURCE_PDF_FILE}")"
design_doc_pdf_pages="$(extract_page_count "${DESIGN_DOC_PDF_FILE}")"
design_doc_formal_pdf_pages="$(extract_page_count "${DESIGN_DOC_FORMAL_PDF_FILE}")"

cat > "${SUPPLEMENT_NOTE_FILE}" <<EOF
# ${SOFTWARE_NAME} 软著补正说明

## 1. 补正对象

- 流水号：${SERIAL_NO}
- 软件名称：${SOFTWARE_NAME}
- 申请人：${APPLICANT}

## 2. 审查缺陷与处理结果

### 2.1 关于“源程序不足 60 页”的补正

已根据当前仓库的实际后端源程序生成专用交存材料：

- 完整源程序汇总文件：软著申请材料/源代码/源代码-完整汇总.txt
- 前 30 页源程序：软著申请材料/源代码/源代码-前30页.txt
- 后 30 页源程序：软著申请材料/源代码/源代码-后30页.txt
- 可直接合并提交的 60 页版本：软著申请材料/源代码/源代码-前后60页.txt
- 建议实际上传 PDF：软著申请材料/源代码/源代码-前后60页.pdf

当前完整汇总源程序共 ${source_full_lines} 行，远超 3000 行；提交版文件共 ${source_submission_lines} 行，对应“前 30 页 + 后 30 页”的 60 页源码要求。当前已同步生成 PDF，页数为 ${source_pdf_pages} 页。提交前仍建议人工复核分页和每页行数。

建议在补正说明中写明：

> 针对补正通知书第 1 项，现提交《源程序鉴别材料》1 份，内容为该软件源程序连续前 30 页和连续后 30 页，共 60 页，每页不少于 50 行。

### 2.2 关于“文档鉴别材料过于简略”的补正

已补充生成详细设计开发文档：

- 设计开发说明书：软著申请材料/文档/软件设计开发说明书.md
- 前 30 页文档：软著申请材料/文档/软件设计开发说明书-前30页.txt
- 后 30 页文档：软著申请材料/文档/软件设计开发说明书-后30页.txt
- 可直接合并提交的 60 页版本：软著申请材料/文档/软件设计开发说明书-前后60页.txt
- 建议实际上传 PDF：软著申请材料/文档/软件设计开发说明书-前后60页.pdf

该文档全文共 ${design_doc_lines} 行，提交版共 ${design_doc_submission_lines} 行，内容覆盖软件概述、技术架构、功能设计、核心流程、数据库结构、接口设计、安全机制、源程序规模说明以及附录清单。当前已同步生成 PDF，页数为 ${design_doc_pdf_pages} 页。提交前仍建议人工复核分页和每页行数。

建议在补正说明中写明：

> 针对补正通知书第 2 项，现提交《${SOFTWARE_SHORT_NAME}${VERSION} 软件设计开发说明书》1 份。文档结合软件主要功能、技术特点、数据库结构、接口设计及源程序规模进行详细说明，并按要求提交连续前 30 页和连续后 30 页；如全文不足 60 页，则提交全文。

## 3. 提交材料建议清单

1. 补正说明函或在线补正说明。
2. 源程序鉴别材料 PDF 1 份。
3. 文档鉴别材料 PDF 1 份。
4. 对应 TXT/MD 源文件作为本地留档。

## 4. 排版与提交注意事项

1. 源程序材料提交时，优先采用等宽字体，避免自动折行导致每页不足 50 行。
2. 文档材料提交时，建议使用统一标题层级、页码、目录和页眉，提升正式性。
3. 最终是否满足“每页不少于 50 行 / 30 行”，以你导出后的 PDF 实际页数和每页行数为准，提交前必须逐页复核。
4. 软件名称、版本号、申请人名称应与申请表保持完全一致。
5. 若在线补正系统限制上传格式，可先将 Markdown / TXT 转为 Word，再导出 PDF。

## 5. 当前材料的定位说明

本次生成材料基于当前仓库自动整理，已能作为补正底稿直接使用；正式提交前，建议再做一次人工审校，重点核对：

- 软件名称和简称是否与申请表一致。
- 设计文档中是否存在不宜公开的密钥、测试地址或内部注释。
- 源程序页码切分后是否满足每页行数要求。
EOF

cat > "${CHECKLIST_FILE}" <<EOF
# ${SOFTWARE_NAME} 补正提交检查清单

## 1. 文件准备

- [ ] 已准备补正说明函或在线补正文本
- [ ] 已准备源程序 60 页提交版
- [ ] 已准备设计开发说明书 60 页提交版
- [ ] 已准备源程序 PDF：源代码-前后60页.pdf
- [ ] 已准备文档 PDF：软件设计开发说明书-前后60页.pdf

## 2. 一致性核对

- [ ] 软件名称与申请表完全一致：${SOFTWARE_NAME}
- [ ] 申请人名称与申请表完全一致：${APPLICANT}
- [ ] 流水号填写正确：${SERIAL_NO}
- [ ] 文档页眉、封面、目录中的版本号统一为 ${VERSION}

## 3. 源程序核对

- [ ] 确认提交内容为前 30 页和后 30 页
- [ ] 确认源码每页不少于 50 行
- [ ] 确认源码页内未出现大面积空行
- [ ] 确认源码包含文件名标识，便于审查理解

## 4. 文档核对

- [ ] 确认文档内容与主要功能、技术特点、源程序量相关
- [ ] 确认文档每页不少于 30 行
- [ ] 确认文档中没有暴露真实密钥、账号密码等敏感信息
- [ ] 确认文档结构完整，包含系统概述、架构、功能、数据库、接口、运行说明等内容

## 5. 提交前最终复核

- [ ] 已导出最终 PDF 并逐页检查
- [ ] 已确认页数切分正确
- [ ] 已确认补正期限未超 30 日
- [ ] 已保留本地备份和最终提交版
EOF

cat > "${MATERIAL_INDEX_FILE}" <<EOF
# 薪酬助手系统软著补正材料清单

## 一、目录说明

本目录用于处理流水号 ${SERIAL_NO} 的软著补正。本次整理后，目录内仅保留当前补正需要的提交版和生成脚本入口。

## 二、本次建议提交的文件

### 2.1 源程序材料

| 文件名 | 位置 | 行数 | 用途 |
|--------|------|------|------|
| 源代码-前30页.txt | 源代码/ | 1500 行 | 源程序前 30 页 |
| 源代码-后30页.txt | 源代码/ | 1500 行 | 源程序后 30 页 |
| 源代码-前后60页.txt | 源代码/ | ${source_submission_lines} 行 | 便于合并提交的 60 页版本 |
| 源代码-前后60页.pdf | 源代码/ | ${source_pdf_pages} 页 | 建议上传的源程序鉴别材料 |
| 源代码-完整汇总.txt | 源代码/ | ${source_full_lines} 行 | 内部校对参考，不一定直接上传 |

### 2.2 文档材料

| 文件名 | 位置 | 行数 | 用途 |
|--------|------|------|------|
| 软件设计开发说明书.md | 文档/ | ${design_doc_lines} 行 | 设计开发说明书全文 |
| 软件设计开发说明书-前30页.txt | 文档/ | 900 行 | 文档前 30 页 |
| 软件设计开发说明书-后30页.txt | 文档/ | 900 行 | 文档后 30 页 |
| 软件设计开发说明书-前后60页.txt | 文档/ | ${design_doc_submission_lines} 行 | 便于合并提交的 60 页版本 |
| 软件设计开发说明书-前后60页.pdf | 文档/ | ${design_doc_pdf_pages} 页 | 建议上传的文档鉴别材料 |
| 软件设计开发说明书-正式版.pdf | 文档/ | ${design_doc_formal_pdf_pages} 页 | 带封面和页眉的正式版文档 |

### 2.3 说明与校对材料

| 文件名 | 位置 | 用途 |
|--------|------|------|
| 补正说明.md | 根目录 | 逐条回应补正通知书 |
| 补正提交检查清单.md | 根目录 | 提交前逐项复核 |
| 表格/软著登记填表说明.md | 表格/ | 在线填报参考 |

## 三、现有材料存在的问题

1. 旧版 extract_source.sh 按错误规则提取源代码，不能证明“前 30 页 + 后 30 页”。
2. 旧版 软件说明书.md 仅约 338 行，篇幅明显不足，不适合直接用来响应“提交详细文档”的补正要求。
3. 旧版 PDF 未与当前补正内容同步，不能继续作为提交依据。

## 四、本次补正建议

1. 源程序以 源代码-前后60页.pdf 为正式提交版。
2. 文档以 软件设计开发说明书-前后60页.pdf 为正式提交版。
3. 软件设计开发说明书-正式版.pdf 用于内部审阅、打印或存档。
4. TXT/MD 文件保留为本地留档与二次修改底稿。
EOF

printf 'Generated files:\n'
printf '  %s\n' "${SOURCE_FULL_FILE}"
printf '  %s\n' "${SOURCE_FRONT_FILE}"
printf '  %s\n' "${SOURCE_BACK_FILE}"
printf '  %s\n' "${SOURCE_SUBMISSION_FILE}"
printf '  %s\n' "${SOURCE_PDF_FILE}"
printf '  %s\n' "${DESIGN_DOC_FILE}"
printf '  %s\n' "${DESIGN_DOC_FRONT_FILE}"
printf '  %s\n' "${DESIGN_DOC_BACK_FILE}"
printf '  %s\n' "${DESIGN_DOC_SUBMISSION_FILE}"
printf '  %s\n' "${DESIGN_DOC_PDF_FILE}"
printf '  %s\n' "${DESIGN_DOC_FORMAL_PDF_FILE}"
printf '  %s\n' "${SUPPLEMENT_NOTE_FILE}"
printf '  %s\n' "${CHECKLIST_FILE}"
printf '  %s\n' "${MATERIAL_INDEX_FILE}"
