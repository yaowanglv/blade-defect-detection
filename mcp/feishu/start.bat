@echo off
chcp 65001 >nul
echo ============================================
echo   飞书 MCP Server 启动脚本
echo ============================================
echo.

cd /d "%~dp0"

:: 飞书应用配置（请修改为你的实际配置，或复制 .env.example 为 .env 后自行加载）
set LARK_APP_ID=your_feishu_app_id
set LARK_APP_SECRET=your_feishu_app_secret
set LARK_MCP_PORT=3001

echo 飞书 App ID: %LARK_APP_ID%
echo 服务端口: %LARK_MCP_PORT%
echo.
echo 正在启动飞书 MCP Server...
echo ============================================

lark-mcp --port %LARK_MCP_PORT%

pause
