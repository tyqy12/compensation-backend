#!/bin/bash
# 软著源代码提取脚本

SOURCE_DIR="/root/compensation-backend/src/main/java"
OUTPUT_DIR="/root/compensation-backend/软著申请材料/源代码"

# 获取所有Java文件并按路径排序
find "$SOURCE_DIR" -name "*.java" -type f | sort > /tmp/java_files.txt

# 计算总有效行数
total_lines=$(find "$SOURCE_DIR" -name "*.java" -type f -exec cat {} \; | grep -v "^[[:space:]]*$" | wc -l)
echo "总有效代码行数: $total_lines"

# 前30页（约750行，按每页25行计算）
echo "正在提取前30页源代码..."
head -n 750 /tmp/java_files.txt | while read f; do
    cat "$f" | grep -v "^[[:space:]]*$"
done > "$OUTPUT_DIR/源代码-前30页.txt"

# 后30页（约750行）
echo "正在提取后30页源代码..."
tail -n 750 /tmp/java_files.txt | while read f; do
    cat "$f" | grep -v "^[[:space:]]*$"
done > "$OUTPUT_DIR/源代码-后30页.txt"

# 统计提取的行数
front_lines=$(wc -l < "$OUTPUT_DIR/源代码-前30页.txt")
back_lines=$(wc -l < "$OUTPUT_DIR/源代码-后30页.txt")
echo "前30页有效行数: $front_lines"
echo "后30页有效行数: $back_lines"

rm /tmp/java_files.txt
echo "提取完成！"
