# 数据集配置（仅 yaml）

Ultralytics / YOLO 训练与校验用的数据描述文件。图像与标签不在本仓库，请下载后放到本机 `path` 目录。

## 下载

- 文件：`Wind-Turbine.zip`（夸克网盘）
- 链接：https://pan.quark.cn/s/1dd65aad843c?pwd=ekdX
- 提取码：`ekdX`
- 分享口令：`/~c23c3akaJD~:/`

解压到：

```text
D:\opendataset\Wind-Turbine
```

## 本机目录与 yaml 对应

```text
D:\opendataset\Wind-Turbine\
├── images\train|val|test      RGB
├── images_ir\train|val|test   红外（与 RGB 文件名对齐）
├── labels\train|val|test      YOLO 标签
└── windturbine-ir\            红外单模态（内含 images/ 与 labels/）
```

| 文件 | `path` | 用途 |
|---|---|---|
| `windturbie-rgb.yaml` | `D:/opendataset/Wind-Turbine` | RGB 单模态（文件名沿用原拼写） |
| `windturbine-ir.yaml` | `D:/opendataset/Wind-Turbine/windturbine-ir` | 红外单模态 |
| `windturbine.yaml` | `D:/opendataset/Wind-Turbine` | RGB + IR 双模态 |

`windturbine.yaml` 中红外字段为 `images_Dual`，本机目录名为 `images_ir`。不一致时请改 yaml 或建同名目录。

训练示例：

```bash
yolo detect train data=detect_service/datasets/windturbine.yaml model=...
```
