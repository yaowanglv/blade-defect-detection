@echo off
chcp 65001 >nul
echo ============================================
echo   钉钉 MCP Server 启动脚本
echo ============================================
echo.

cd /d "%~dp0"

:: 钉钉应用配置（请修改为你的实际配置）
:: 获取方式：https://open-dev.dingtalk.com/ 创建企业内部应用
set DINGTALK_Client_ID=your_dingtalk_client_id
set DINGTALK_Client_SECRET=your_dingtalk_client_secret

:: 钉钉文档MCP配置（可选）
set DING_DOC_COOKIE=your_dingtalk_cookie

echo 钉钉 Client ID: %DINGTALK_Client_ID%
echo.
echo 正在启动钉钉 MCP Servers...
echo ============================================
echo.

echo [1/2] 启动 dingtalk-mcp (消息/文件发送服务)...
start "DingTalk MCP" dingtalk-mcp
echo.

echo [2/2] 启动 mcp-dingtalk-doc (文档管理服务)...
start "DingTalk Doc MCP" mcp-dingtalk-doc
echo.

echo ============================================
echo 钉钉 MCP Servers 已启动!
echo ============================================
pause
