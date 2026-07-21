# YourStageSkybox

Minecraft Forge 模组 —— 为地图创作者提供 API 和指令，通过资源包导入天空盒材质后快捷切换，实现场景氛围的即时变化。

## 支持的版本

| 目录 | MC 版本 | Forge | 状态 |
|------|--------|-------|------|
| `1.12.2/` | 1.12.2 | 14.23.5.2847 | ✅ 稳定 |
| `1.16.5/` | 1.16.5 | 36.2.39 | ✅ 可编译 |
| `1.20.1/` | 1.20.1 | 47.2.0 | ✅ 可编译 |
| `1.21.1/` | 1.21.1 | 52.1.15 | ⚠️ 移植中 |

## 安装

1. 将对应版本的 JAR 放入 Minecraft 的 `mods/` 目录
2. 将天空盒资源包放入 `resourcepacks/` 目录
3. 启动游戏，在资源包界面启用天空盒资源包

## 制作天空盒资源包

### 文件结构

```
你的资源包/
├── pack.mcmeta
└── assets/
    └── yourstageskybox/
        └── textures/
            └── skyboxes/
                ├── 天空盒名称1/
                │   ├── panorama_0.png   ← 北 (-Z)
                │   ├── panorama_1.png   ← 东 (+X)
                │   ├── panorama_2.png   ← 南 (+Z)
                │   ├── panorama_3.png   ← 西 (-X)
                │   ├── panorama_4.png   ← 上 (+Y)
                │   └── panorama_5.png   ← 下 (-Y)
                └── 天空盒名称2/
                    └── ...
```

### pack.mcmeta 示例

```json
{
    "pack": {
        "pack_format": 15,
        "description": "我的天空盒资源包"
    }
}
```

> `pack_format` 取值：1.12.2 用 `3`，1.16.5 用 `6`，1.20.1 用 `15`

### 图片要求

- 每面正方形（推荐 1024×1024 或 2048×2048，`.png` 格式）
- 六张图的光照和色温需一致，否则接缝处可见
- 底部（panorama_5）可选单色填充或透明，降低视觉干扰

### 方向对应

```
panorama_0 ← 玩家面朝北方时看到的画面（-Z）
panorama_1 ← 玩家面朝东方时看到的画面（+X）
panorama_2 ← 玩家面朝南方时看到的画面（+Z）
panorama_3 ← 玩家面朝西方时看到的画面（-X）
panorama_4 ← 玩家抬头看到的画面（+Y）
panorama_5 ← 玩家低头看到的画面（-Y）
```

### 测试资源包

`1.12.2/resourcepack/` 目录包含两个示例天空盒（`demo`、`starry`），可直接复制到 `resourcepacks/` 测试。

## 指令参考

权限等级 ≥ 2（OP）。别名：`/yss`。

```
/yourstageskybox set <名称> [alpha] [duration] [player] [dim]
    设置天空盒，alpha 0.0~1.0（默认 1.0），duration 过渡秒数（默认 2.0）

/yourstageskybox clear [player] [dim]
    恢复原版天空

/yourstageskybox alpha <0.0~1.0> [player] [dim]
    调整透明度（不触发过渡动画）

/yourstageskybox duration <秒> [player] [dim]
    设置下次切换的过渡时长

/yourstageskybox info [dim]
    查看当前天空盒信息及过渡进度

/yourstageskybox list
    列出所有已注册天空盒

/yourstageskybox reload
    重新扫描资源包（客户端）
```

**参数说明：**
- `player`：`@a`（所有玩家）、`@p`（最近）、`@r`（随机）、玩家名，留空=所有
- `dim`：`0`=主世界、`-1`=下界、`1`=末地，留空=0

**示例：**
```
/yourstageskybox set starry                    # 全体玩家主世界切换到 starry
/yourstageskybox set sunset 0.8 3.0 @p        # 最近玩家 3 秒过渡到 sunset，透明度 0.8
/yourstageskybox clear @a 1                   # 清除全部玩家末地的天空盒
/yourstageskybox alpha 0.5 @p                # 最近玩家天空盒半透明
```

## 配置文件

`config/yourstageskybox.cfg`（1.12.2）或 `config/yourstageskybox-common.toml`（1.16.5+）：

| 设置 | 默认 | 说明 |
|------|------|------|
| `autoRegister` | `false` | 客户端启动时自动扫描资源包注册天空盒 |

## API 使用

```java
// 设置天空盒
SkyboxAPI.setSkybox(level, "starry");
SkyboxAPI.setSkybox(level, "sunset", 0.8f, 3000, player);

// 清除
SkyboxAPI.clearSkybox(level);
SkyboxAPI.clearSkybox(level, player);

// 查询
String name = SkyboxAPI.getSkybox(level);
float alpha = SkyboxAPI.getSkyboxAlpha(level, player);
```
