@echo off
chcp 65001 >nul
echo ============================================
echo   MySQL MCP Server 启动脚本
echo ============================================
echo.

cd /d "%~dp0"

if exist ".env.mcp" (
    echo 正在加载 .env.mcp ...
    for /f "usebackq tokens=1,* delims==" %%A in (".env.mcp") do (
        if not "%%A"=="" if not "%%A:~0,1%"=="#" set "%%A=%%B"
    )
) else (
    echo 未找到 .env.mcp，使用脚本内占位符。请复制 .env.mcp.example 为 .env.mcp 后填写。
    set MYSQL_HOST=127.0.0.1
    set MYSQL_PORT=3306
    set MYSQL_USER=root
    set MYSQL_PASS=your_mysql_password
    set MYSQL_DB=blade
    set ALLOW_INSERT_OPERATION=true
    set ALLOW_UPDATE_OPERATION=true
    set ALLOW_DELETE_OPERATION=true
    set ALLOW_DDL_OPERATION=true
    set MYSQL_DISABLE_READ_ONLY_TRANSACTIONS=true
)

echo 数据库: %MYSQL_DB%
echo 主机: %MYSQL_HOST%:%MYSQL_PORT%
echo.
echo 正在启动 MCP Server...
echo ============================================

".\node_modules\.bin\mcp-server-mysql.cmd"

pause
