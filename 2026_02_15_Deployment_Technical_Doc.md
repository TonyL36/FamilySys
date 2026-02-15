# 2026年2月15日 FamilySys 技术文档

本文档记录 2026-02-15 的页面风格同步、部署脚本修复与云端部署更新，并附关键代码与部署验证信息。

## 1. 背景与目标

1. 统一前端页面风格与导航体验，提升系统一致性与可维护性。
2. 修复部署脚本中的变量拼写错误，确保自动化部署稳定可靠。
3. 完成云端更新与基本可用性验证。

## 2. 变更内容

### 2.1 前端页面风格同步
已将成员管理、关系管理、家族关系树、远亲关系查询等页面统一页眉样式与导航按钮，并补齐“近亲网状视图”入口，保持本地与远端页面一致。

涉及页面（本地与远端）：
- family-frontend：members2.html、relationships4.html、family-tree-final.html、distant-relative.html
- family-frontend-remote：members2.html、relationships4.html、family-tree-final.html、distant-relative.html

### 2.2 部署脚本修复
修复 `deploy_update.ps1` 中数据库变量的拼写问题，避免脚本执行时变量未定义导致的部署中断。

```powershell
$LOCAL_JAR = "family-backend\target\family-backend-1.0-SNAPSHOT.jar"
$LOCAL_DB = "family-backend\src\main\resources\family.db"
```

### 2.3 云端部署更新
更新部署产物到阿里云 ECS（Ubuntu 24.04），并完成前端与后端可达性验证。

## 3. 部署与验证

### 3.1 本地打包
```bash
cd family-backend
mvn clean package
```

### 3.2 一键部署脚本
```powershell
powershell -ExecutionPolicy Bypass -File .\deploy_update.ps1
```

### 3.3 线上可用性检查
```bash
curl -I http://47.109.193.180/family/members2.html
curl -I http://47.109.193.180:8000
```

## 4. 关键产出

- 前端页面体验一致：统一页眉、按钮风格、导航结构，减少认知切换成本。
- 部署脚本可用性修复：消除变量拼写错误带来的部署阻断风险。
- 线上更新成功：前端 200 OK，后端端口可达。

## 5. 传统文化融合式总结

家族系统讲究“修谱以正名，续脉以传承”。这次更新如同修订族谱中的条目：
- **“礼以定分”**：统一页面风格与导航，如同族谱的条目规范，确保各支脉秩序分明。
- **“慎终追远”**：部署流程的稳定性修补，既是对现有数据的守护，也是对后续传承的负责。
- **“协和万邦”**：前后端联动与云端发布，如同族脉相通，系统整体运转顺畅而有序。

技术是器，文化是道。以器载道，系统才有长久之道。
