# 2026年2月14日 FamilySys 部署技术总结

**日期**：2026-02-14
**执行人**：Trae (代表用户)
**相关部署环境**：阿里云 ECS (Ubuntu 24.04 LTS)
**项目状态**：已更新前端部署路径，修复API连接问题

---

## 1. 关键变更详情

### 1.1 前端部署路径调整
为了规范化服务器目录结构，避免根目录混淆，前端文件已迁移至 `/family` 子目录。
*   **新部署位置**：`/var/www/familysys-frontend/family-frontend-remote/family`
*   **访问URL变更**：
    *   原：`http://47.109.193.180/members2.html`
    *   新：`http://47.109.193.180/family/members2.html`

### 1.2 前端配置修正 (Fix Hardcoded IP)
修复了前端代码中硬编码为 `localhost` / `127.0.0.1` 的问题，统一替换为服务器公网 IP。
*   **变更前**：`const API_BASE_URL = 'http://127.0.0.1:8000';`
*   **变更后**：`const API_BASE_URL = 'http://47.109.193.180:8000';`
*   **涉及文件**：所有前端 `.html` 文件中的 JavaScript 配置段。

### 1.3 部署脚本优化 (`deploy_update.ps1`)
更新了自动化部署脚本以适配新的目录结构。
*   **清理逻辑**：增加了对 `/family` 子目录的创建。
*   **传输逻辑**：前端文件现在被传输到 `$REMOTE_FRONTEND_DIR/family`。

### 1.4 数据持久化修复验证
确认 `DatabaseConnection.java` 已修复之前的临时文件数据丢失风险。
*   **逻辑**：现在优先检查工作目录 (`/opt/familysys/family.db`)，若不存在则从 JAR 包提取并保存为**非临时文件**。
*   **效果**：重启服务后，用户新增的数据（成员/关系）不再丢失。

---

## 2. 部署验证检查表

| 检查项 | 状态 | 备注 |
| :--- | :--- | :--- |
| **后端服务状态** | ✅ 运行中 | `systemctl status familysys` 正常 |
| **端口监听** | ✅ 正常 | 8000 (API), 80 (Nginx) |
| **前端访问** | ✅ 正常 | `/family/members2.html` 可加载 |
| **API连通性** | ✅ 正常 | 前端可成功 fetch 数据 |
| **数据持久化** | ✅ 已修复 | 重启后数据保留 |
| **CORS配置** | ✅ 已配置 | 允许跨域请求 |

---

## 3. 后续建议

1.  **HTTPS 升级**：当前仍使用 HTTP，建议尽快申请 SSL 证书启用 HTTPS。
2.  **配置抽离**：建议将 `API_BASE_URL` 提取到单独的 `config.js`，避免每次修改 IP 都要替换所有 HTML 文件。
