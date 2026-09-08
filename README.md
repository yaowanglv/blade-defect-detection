# 风机叶片缺陷智能检测系统

基于 **Vue 3 + Spring Boot + Flask/YOLO** 的风力发电机叶片缺陷检测系统，支持图像/视频检测、RGB/IR 双模态、检测记录与看板、JWT 登录，以及可选的大模型分析与飞书上传。

仓库地址：https://github.com/yaowanglv/blade-defect-detection

## 技术栈

| 部分 | 说明 |
|---|---|
| 前端 | Vue 3 + Vite + Element Plus，开发端口 `2655` |
| 后端 | Spring Boot 3（Java 17+），端口 `1234` |
| 检测服务 | Flask + 定制 Ultralytics，端口 `6522` |
| 数据库 | MySQL 8，库名 `blade` |
| 权重 | `detect_service/models/`（RGB / IR / Dual） |
| 数据集配置 | `detect_service/datasets/`（yaml；图像仍在 `DATASET_ROOT`，默认 `D:/opendataset/Wind-Turbine`） |

## 目录结构

```
.
├── vue/                 前端
├── springb/             Spring Boot 后端
├── detect_service/      Python 检测服务（含定制 ultralytics、权重、数据集 yaml）
├── sql/                 MySQL 结构脚本（仅结构，不含数据）
├── mcp/                 可选：MySQL / 飞书 / 钉钉 MCP 辅助配置
└── README.md
```

## 环境要求

- Node.js 18+（前端已在 Node 24 验证）
- JDK 17+、Maven 3.9+
- MySQL 8
- Python 3.10（检测服务与 conda 环境 `v11dmt` 对齐）
- 可选：NVIDIA GPU + CUDA 12.1（与 `torch==2.2.2+cu121` 一致）

## 数据集下载与放置

仓库只包含 yaml 配置（`detect_service/datasets/`），**不包含图像和标签**。请自行下载后解压到本机：

```text
D:\opendataset\Wind-Turbine
```

- 网盘文件：`Wind-Turbine.zip`（夸克网盘）
- 链接：https://pan.quark.cn/s/1dd65aad843c?pwd=ekdX
- 提取码：`ekdX`
- 分享口令：`/~c23c3akaJD~:/`（也可复制整段到夸克 APP 打开）

解压后目录应对齐下面结构（与本机 `D:\opendataset\Wind-Turbine` 一致）。后端 `dataset.root` / 环境变量 `DATASET_ROOT` 默认也是该路径。

```text
D:\opendataset\Wind-Turbine\
├── images\                 RGB 图像（windturbie-rgb.yaml、windturbine.yaml 的 train/val/test）
│   ├── train\              约 1534 张
│   ├── val\                约 431 张
│   └── test\               约 226 张
├── images_ir\              红外图像（与 RGB 同名对齐，供系统双模态配对）
│   ├── train\
│   ├── val\
│   └── test\
├── labels\                 YOLO 标签（与 images 划分一致）
│   ├── train\
│   ├── val\
│   └── test\
├── windturbine-ir\         红外单模态训练根目录（windturbine-ir.yaml 的 path）
│   ├── images\train|val|test
│   └── labels\train|val|test
├── 风机\                   按类别归档的原图（非 YOLO 划分，可选）
│   ├── damage\
│   ├── dirt\
│   └── 正常\
└── data.yaml               Roboflow 导出说明（训练请用仓库内 detect_service/datasets/*.yaml）
```

与仓库 yaml 的对应关系：

| yaml | `path` | 图像目录 | 说明 |
|---|---|---|---|
| `detect_service/datasets/windturbie-rgb.yaml` | `D:/opendataset/Wind-Turbine` | `images/train`、`images/val` | RGB 单模态 |
| `detect_service/datasets/windturbine-ir.yaml` | `D:/opendataset/Wind-Turbine/windturbine-ir` | `images/train|val|test` | 红外单模态 |
| `detect_service/datasets/windturbine.yaml` | `D:/opendataset/Wind-Turbine` | RGB：`images/`；红外：`images_ir/`（见下） | 双模态 |

`windturbine.yaml` 里写的红外目录名是 `images_Dual`。本机实际文件夹是 **`images_ir`**。若训练报找不到红外图，把 yaml 中 `ir: images_Dual` 改成 `ir: images_ir`，或自行建 `images_Dual` 目录/软链。

类别：`damage`、`dirt`（`nc: 2`）。

## 1. 初始化数据库

在 MySQL 中按序号执行 `sql/`：

```sql
-- sql/00_init.sql 会创建库 blade
SOURCE sql/00_init.sql;
SOURCE sql/01_user_auth.sql;
SOURCE sql/02_detection.sql;
SOURCE sql/03_system_config.sql;
SOURCE sql/04_dataview.sql;
SOURCE sql/05_views.sql;
```

| 文件 | 内容 |
|---|---|
| `00_init.sql` | 建库 |
| `01_user_auth.sql` | 用户、角色、权限、token 黑名单 |
| `02_detection.sql` | 图像/视频检测记录、日志 |
| `03_system_config.sql` | 系统配置 |
| `04_dataview.sql` | 看板统计表 |
| `05_views.sql` | 看板视图 |

脚本只导出结构。账号需要自己插入；本机开发可用明文密码（后端会在登录时迁移为 bcrypt）。

## 2. 配置后端密钥（不要提交真实值）

```bash
cd springb/src/main/resources
cp application-local.yml.example application-local.yml
```

至少填写：

- MySQL 用户名 / 密码
- `jwt.secret`：Base64 密钥，解码后至少 64 字节

```bash
python -c "import os,base64; print(base64.b64encode(os.urandom(64)).decode())"
```

可选：DeepSeek / GLM / Kimi API Key、飞书 App ID/Secret。也可用环境变量覆盖，见 `application.yml` 注释。

`application-local.yml` 已被 `.gitignore` 忽略。

## 3. 启动检测服务

```bash
cd detect_service
pip install -r requirements.txt
# 不要 pip install ultralytics，使用本目录定制源码
set PYTHONPATH=%CD%
python detect-api-blade.py
```

服务地址：`http://localhost:6522`

权重默认在：

- `detect_service/models/rgb/yolo11n.pt`
- `detect_service/models/ir/best.pt`
- `detect_service/models/dual/yolo11.pt`

## 4. 启动后端

```bash
cd springb
mvn spring-boot:run
```

服务地址：`http://localhost:1234`

## 5. 启动前端

```bash
cd vue
npm install
npm run dev
```

浏览器打开：http://localhost:2655/login

前端请求后端 `http://localhost:1234`。登录接口：`POST /auth/login`。

## 端口一览

| 服务 | 端口 |
|---|---|
| Vue | 2655 |
| Spring Boot | 1234 |
| Python 检测 | 6522 |

## 可选：MCP

`mcp/` 仅给 AI 编程客户端用，产品运行不需要。说明见 `mcp/README.md`。

## 不会进入 Git 的内容

- `node_modules/`、`springb/target/`
- `application-local.yml`、`.env*`（保留 `*.example`）
- `.idea/`、`.vscode/`、`.agents/`
- 运行时 `uploads/`、`results/`、日志
- 除 `detect_service/models/` 以外的 `*.pt`

检测服务使用的权重会随仓库提交。
