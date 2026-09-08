# 数据集配置（仅 yaml）

Ultralytics / YOLO 训练与校验用的数据描述文件。图像与标签仍在本机 `path` 所指目录（默认 `D:/opendataset/Wind-Turbine`），本目录不包含数据集本身。

| 文件 | 用途 |
|---|---|
| `windturbie-rgb.yaml` | RGB 单模态（文件名沿用原拼写） |
| `windturbine-ir.yaml` | 红外单模态 |
| `windturbine.yaml` | RGB + IR 双模态 |

训练示例：

```bash
yolo detect train data=detect_service/datasets/windturbine.yaml model=...
```
