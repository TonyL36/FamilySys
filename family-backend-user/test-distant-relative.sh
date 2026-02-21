#!/bin/bash

# 远亲关系查询测试脚本
# 使用 curl 测试远亲关系API端点

echo "=== 家族关系管理系统 - 远亲关系查询测试 ==="
echo

# 检查curl是否可用
if ! command -v curl &> /dev/null; then
    echo "错误: curl 未安装。请先安装curl工具。"
    exit 1
fi

# 测试服务器地址
SERVER_URL="http://localhost:8000"

# 测试用例1：查询两个已知成员的远亲关系
echo "测试1: 查询成员17和成员19的远亲关系"
echo "curl -X GET \"$SERVER_URL/relationship?distantRelative=&member1ID=17&member2ID=19\""
curl -s -X GET "$SERVER_URL/relationship?distantRelative=&member1ID=17&member2ID=19" | python3 -m json.tool
echo

echo "测试2: 查询成员1和成员50的远亲关系（可能无共同祖先）"
echo "curl -X GET \"$SERVER_URL/relationship?distantRelative=&member1ID=1&member2ID=50\""
curl -s -X GET "$SERVER_URL/relationship?distantRelative=&member1ID=1&member2ID=50" | python3 -m json.tool
echo

echo "测试3: 查询成员8和成员9的远亲关系（兄弟关系）"
echo "curl -X GET \"$SERVER_URL/relationship?distantRelative=&member1ID=8&member2ID=9\""
curl -s -X GET "$SERVER_URL/relationship?distantRelative=&member1ID=8&member2ID=9" | python3 -m json.tool
echo

echo "=== 测试完成 ==="
echo "提示: 请确保家族服务正在运行 (mvn exec:java -Dexec.mainClass=Application)"
echo "提示: 如果需要安装jq工具来美化JSON输出，请运行: npm install -g jq"}