# 2026年2月13日 FamilySys 部署技术总结

**日期**：2026-02-13  
**执行人**：Trae (代表用户)  
**部署环境**：阿里云 ECS (Ubuntu 24.04 LTS)  
**项目状态**：已部署，前后端联调通过

---

## 1. 部署架构概述

本次部署将 FamilySys 系统分为前后端分离架构：

*   **后端 (Backend)**：
    *   **技术栈**：Java (JDK 17), SQLite, Maven (Shade Plugin)
    *   **运行方式**：Systemd 服务守护 (`familysys.service`)
    *   **监听端口**：8000 (HTTP)
    *   **部署位置**：`/opt/familysys`

*   **前端 (Frontend)**：
    *   **技术栈**：原生 HTML5 / JavaScript (ES6+ Fetch API)
    *   **运行方式**：Nginx 静态站点托管
    *   **监听端口**：80 (HTTP)
    *   **部署位置**：`/var/www/familysys-frontend/family-frontend-remote`

---

## 2. 关键配置详情

### 2.1 后端服务配置
我创建了 Systemd 服务文件以确保后端开机自启和崩溃自动重启。

*   **服务文件**：`/etc/systemd/system/familysys.service`
*   **关键指令**：
    ```ini
    [Service]
    WorkingDirectory=/opt/familysys
    ExecStart=/usr/bin/java -jar /opt/familysys/family-backend-1.0-SNAPSHOT.jar
    Restart=on-failure
    ```
*   **安全机制**：
    *   实现了 `X-API-Key` 请求头验证。
    *   CORS 配置允许跨域访问（已添加 `Access-Control-Allow-Headers: X-API-Key`）。

### 2.2 前端 Nginx 配置
我配置了 Nginx 作为静态文件服务器，指向专门为远程环境准备的 `family-frontend-remote` 目录。

*   **站点配置**：`/etc/nginx/sites-available/familysys-frontend`
*   **关键指令**：
    ```nginx
    server {
        listen 80;
        root /var/www/familysys-frontend/family-frontend-remote;
        index family-tree-final.html;
        location / {
            try_files $uri $uri/ /family-tree-final.html;
        }
    }
    ```

---

## 3. 风险隐患排查报告 (Risk Assessment)

在提交代码前，我检查了核心逻辑，发现以下潜在风险，建议后续优化：

### 3.1 🔴 数据持久化风险
**问题描述**：
原 `DatabaseConnection.java` 在 JAR 模式下使用临时文件导致数据丢失。

### 3.2 🟠 硬编码配置
**问题描述**：
前端 HTML 硬编码了 IP 地址。

### 3.3 🟠 HTTP 明文传输 (中风险)
**问题描述**：
当前服务运行在 HTTP (80/8000) 上，未启用 HTTPS/SSL。

**后果**：
数据（包括 API Key）在传输过程中未加密，容易受到中间人攻击。

**建议方案**：
配置 Nginx 反向代理后端，并申请 SSL 证书（如 Let's Encrypt），强制全站 HTTPS。

---

## 4. 结论

系统已成功部署并可访问。基础功能（成员管理、关系查询、族谱展示）在当前环境下运行正常。但**数据持久化问题**是目前最大的稳定性隐患，建议在投入正式使用前优先修复 `DatabaseConnection.java` 的逻辑。

---

## 5. 2026-02-14 部署更新 (Update)

### 5.1 部署架构调整
为了优化目录结构，前端文件已迁移至 `/family` 子目录。
*   **新部署位置**：`/var/www/familysys-frontend/family-frontend-remote/family`
*   **访问地址**：`http://47.109.193.180/family/members2.html`

### 5.2 针对风险项的解决方案
针对第 3 节中提到的风险隐患，本日已实施以下修复：

1.  **解决数据持久化风险 (Fixing 3.1)**：
    *   **方案**：修改 `DatabaseConnection.java` 逻辑，优先使用工作目录 (`/opt/familysys/family.db`) 的数据库文件。若不存在，则从 JAR 包提取一份到该目录。
    *   **效果**：确保了服务重启或重新部署后，用户数据能够持久保存，不再因使用临时文件而丢失。

2.  **解决硬编码配置问题 (Fixing 3.2)**：
    *   **方案**：将所有前端代码中硬编码的 `localhost` / `127.0.0.1` 批量替换为服务器公网 IP `47.109.193.180`。
    *   **效果**：修复了远程用户访问时出现的 `Failed to fetch` 错误。

3.  **自动化部署脚本更新**：
    *   `deploy_update.ps1` 脚本已更新，支持一键部署到新目录结构并自动处理文件清理。
