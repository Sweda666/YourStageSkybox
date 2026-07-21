# AGENTS.md

## Project
Minecraft 1.12.2 Forge mod. Provides API and `/commands` for map creators to switch skybox textures at runtime. Textures are loaded via resource packs; the mod handles the switching logic.

Multi-version monorepo layout:
```
yourstageskybox/
├── 1.12.2/          — 当前稳定版（Forge 14.23.5.2847, Gradle 4.9, JDK 8）
├── 1.16.5/          — 可编译（Forge 36.2.39, Gradle 7.4.2, JDK 8）
├── 1.20.1/          — 可编译（Forge 47.2.0, Gradle 8.8, JDK 17）
├── 1.21.1/          — 移植中（Forge 52.1.15, Gradle 8.12.1, JDK 21）
│                     ⚠ 需要网络下载 Gradle 8.12.1+ 及 Forge 依赖；当前环境离线
├── AGENTS.md        — 本文件
├── _downloads/       — 测试素材（非代码）
└── startOpenCode..bat
```

## Build
- **JDK 8 required** (1.12.2 Forge constraint — no Java 9+ APIs, lambdas ok but no var/records/modules)
- Gradle wrapper pins **4.9** — not 7.x/8.x
- `gradlew setupDecompWorkspace` — first-time setup (generates Forge workspace, downloads MC assets; can take 10+ min first time)
- `gradlew build` — compile and package JAR (outputs to `build/libs/`)
- `gradlew runClient` — launch modded client for in-game manual testing
- Uses **anatawa12 ForgeGradle fork** (`com.anatawa12.forge:ForgeGradle:2.3-1.0.+`) because `jcenter()` shut down and official `net.minecraftforge.gradle:ForgeGradle:2.3-SNAPSHOT` is unresolvable
- Forge `14.23.5.2847`, mappings `stable_39`
- `gradle.properties`: `-Xmx3G`, daemon disabled — IDE may freeze with insufficient heap

## Testing
- **No automated tests.** The only way to verify behavior is `gradlew runClient` and manual in-game testing. Do not look for test commands or test files.

## Config
配置文件位于 `config/yourstageskybox.cfg`，由 `ModConfig` 管理：

| 设置 | 默认值 | 说明 |
|---|---|---|
| `autoRegister` | `true` | `true` = 客户端启动时自动扫描资源包注册天空盒；`false` = 仅通过指令注册 |

自动注册会扫描所有已加载资源包的 `assets/yourstageskybox/textures/skyboxes/` 目录（支持文件夹和 zip 格式），对每个含 `panorama_0.png` 的子目录自动调用 `registerSkybox()`。

## Package structure
```
yourstageskybox/                  — root package (not a domain; modid = "yourstageskybox")
├── YourStageSkybox.java          — @Mod main class, lifecycle + player login sync
├── YourStageSkyboxLocale.java    — standalone i18n loader (reads .lang from classpath, bypasses @SideOnly)
├── ModConfig.java             — autoRegister config (config/yourstageskybox.cfg)
├── proxy/
│   ├── CommonProxy.java       — server-side: registers /yourstageskybox command
│   └── ClientProxy.java       — inits SkyboxManager, auto-registers if configured
├── skybox/
│   ├── SkyboxManager.java     — texture registration, active SkyboxState per dim, transition engine
│   ├── SkyboxState.java       — name + alpha + RGB color + durationMs per-dim/player
│   ├── TransitionState.java   — client-only fade (fromName, toName, startTime, easeInOutCubic)
│   ├── SkyboxRenderer.java    — IRenderHandler: dual-cubemap crossfade with alpha blending
│   └── SkyboxWorldData.java   — WorldSavedData: persists player SkyboxState assignments
├── command/
│   └── CommandYourStageSkybox.java — /yourstageskybox (alias /yss), OP level 2
├── network/
│   ├── NetworkHandler.java    — SimpleNetworkWrapper setup + sync helpers
│   └── PacketSyncSkybox.java  — IMessage: dimension + full SkyboxState → client
└── api/
    └── SkyboxAPI.java         — public API for other mods/scripts
```

## Skybox texture convention
Textures go in a resource pack at:
```
assets/yourstageskybox/textures/skyboxes/<name>/panorama_0.png  ← 北 (-Z)
assets/yourstageskybox/textures/skyboxes/<name>/panorama_1.png  ← 东 (+X)
assets/yourstageskybox/textures/skyboxes/<name>/panorama_2.png  ← 南 (+Z)
assets/yourstageskybox/textures/skyboxes/<name>/panorama_3.png  ← 西 (-X)
assets/yourstageskybox/textures/skyboxes/<name>/panorama_4.png  ← 上 (+Y)
assets/yourstageskybox/textures/skyboxes/<name>/panorama_5.png  ← 下 (-Y)
```

ResourceLocations are constructed as `yourstageskybox:textures/skyboxes/<name>/panorama_<i>.png` (full path including `textures/` prefix and `.png` suffix) — see `SkyboxManager.buildFaceLocation()`.

A demo resource pack exists at `1.12.2/resourcepack/` with two sample skyboxes (`demo`, `starry`). Copy it to Minecraft's `resourcepacks/` folder to test.

The script `1.12.2/import_skybox.py` converts common skybox formats (Humus, cross-cubemap, individual faces) into the `panorama_0~5.png` convention. Usage: `python import_skybox.py <input> <name>` — outputs to `src/main/resources/assets/yourstageskybox/textures/skyboxes/<name>/`.

## Commands (权限等级 ≥ 2)
| Command | Effect |
|---|---|
| `/yourstageskybox set <name> [alpha] [duration] [player] [dim]` | Switch skybox for player(s) in dimension |
| `/yourstageskybox clear [player] [dim]` | Revert to vanilla sky |
| `/yourstageskybox alpha <0.0~1.0> [player] [dim]` | Set current skybox opacity |
| `/yourstageskybox duration <秒> [player] [dim]` | Set transition duration for next switch |
| `/yourstageskybox info [dim]` | Show active skybox name, alpha, color, transition progress |
| `/yourstageskybox list` | List registered skyboxes (mark active) |
| `/yourstageskybox reload` | Re-scan resource packs (client-only) |

**player**: `@a` (all), `@p` (nearest), `@r` (random), player name, or empty (all players)
**dim**: numeric dimension ID (0=Overworld, -1=Nether, 1=End), or empty (default: 0)
**alpha**: 0.0~1.0, default 1.0
**duration**: transition seconds, default 2.0

## Key architecture notes
- **Rendering**: `SkyboxRenderer` is set via `WorldProvider#setSkyRenderer(IRenderHandler)` in `WorldEvent.Load` handler (`YourStageSkybox#onWorldLoad`). Do NOT use `RenderGlobal#setSkyRenderer()` — that method does not exist in Forge 1.12.2.
- **Transition system**: Skybox switches trigger `TransitionState` (client-only). `SkyboxRenderer.render()` checks `SkyboxManager.updateTransition(dim)` first — if transitioning, draws OLD cubemap at `alpha×(1-progress)` then NEW at `alpha×progress` with easeInOutCubic timing. Transition cleans itself up when complete.
- **Data model**: `activeSkyboxes` and `playerActiveSkyboxes` now store `SkyboxState` (name + alpha + RGB + durationMs) instead of bare `String`. `PacketSyncSkybox` carries the full state.
- **GL blending**: `SkyboxRenderer` enables GL_BLEND with SRC_ALPHA / ONE_MINUS_SRC_ALPHA during draw. Must restore blend state in the exit block — if blend stays enabled, other mods' rendering may break.
- **Client/server split**: `SkyboxManager` has both client-only methods (`@SideOnly`) and shared methods. `reloadSkyboxes()` uses `FMLCommonHandler#getEffectiveSide()` runtime check to avoid stripping on dedicated server.
- **Network sync**: Server stores active skybox name per dimension in `SkyboxManager.activeSkyboxes`. On change, pushes `PacketSyncSkybox` via `NetworkHandler.sendSkyboxSync(dimension, name)`. On player login, `PlayerEvent.PlayerLoggedInEvent` syncs all per-player entries.
- **Per-player tracking**: Server tracks `Map<UUID, Map<Integer, SkyboxState>>` in `SkyboxManager.playerActiveSkyboxes`. Commands and API support per-player skyboxes with `[player] [dim]` parameters.
- **Persistence**: Per-player data is persisted via `SkyboxWorldData` (extends `WorldSavedData`) to the overworld save. Data is lazily restored on player login (`PlayerLoggedInEvent`) — this is the primary path because `PlayerLoggedInEvent` fires before `WorldEvent.Load`. `WorldEvent.Load` (dim 0) serves as a fallback. Write occurs via `markDirty()` on every mutation.
- **Auto-registration**: `/yourstageskybox set <name>` triggers lazy registration — tries to load `panorama_0.png`, succeeds if resource exists. Textures are loaded via `buildFaceLocation()` which constructs the ResourceLocation with full path (see above).
- **Command is server-side**: Registered via `FMLServerStartingEvent#registerServerCommand`. Works across all dimensions.

## Quirks / footguns
- **Java 8 only** — no var, records, switch expressions, text blocks, or Java 9+ APIs. Lambda and try-with-resources ok.
- `@SideOnly(Side.CLIENT)` strips methods from dedicated server at class-transform time. Methods called from command execution (server-side) MUST use runtime side-check (`FMLCommonHandler#getEffectiveSide()`) instead.
- `GL_QUADS` rendering in `SkyboxRenderer` is deprecated in later MC versions; do not "upgrade" to triangle strips without understanding the cubemap face winding.
- **GL state must be strictly paired** — every `disableXxx()` must have a corresponding `enableXxx()` in the state restoration block. Missing restorations (e.g., `enableCull()` after `disableCull()`) can break subsequent rendering like clouds appearing black from below.
- `simpleimpl` channel name max 20 chars — current `"YSB_SKYBOX"` is 10 chars, safe. Do not rename without checking length.
- Skybox renders behind all world geometry (`depthMask(false)`) but before fog. If the sky doesn't appear: verify `activeSkyboxes` has entries for the player's dimension, and confirm `SkyboxRenderer.render()` is being called.
- `1.12.2/介绍.txt` is the original requirements doc in Chinese — reference for feature scope.

## Multi-version differences
- **1.12.2**: `@Mod(modid=...)` + `@SidedProxy` + `FMLPreInitializationEvent`/`FMLServerStartingEvent` lifecycle. `simpleimpl` network layer. `WorldProvider#setSkyRenderer()`.
- **1.16.5 / 1.20.1**: `@Mod(MODID)` + `DistExecutor.safeRunForDist()` + `FMLJavaModLoadingContext` + `Mod.EventBusSubscriber`. Modern Forge network layer. Both have full source trees but may have compilation issues — check `build.log` / `compile.log` in each version directory before working on them.
- **1.21.1**: Same modern Forge pattern as 1.20.1 (`@Mod(MODID)`, `DistExecutor`, `FMLJavaModLoadingContext`). Uses ForgeGradle 7 plugin system (`id 'net.minecraftforge.gradle' version '[7.0.3,8)'`) — no `buildscript` block. Gradle 8.12.1, JDK 21. Source ported from 1.20.1, may have API changes that need fixing.

## i18n
语言文件位于 `src/main/resources/assets/yourstageskybox/lang/`，目前 4 种语言：

| 文件 | 语言 |
|---|---|
| `en_us.lang` | English |
| `zh_cn.lang` | 简体中文 |
| `ru_ru.lang` | Русский |
| `ja_jp.lang` | 日本語 |

- **所有消息翻译使用 `YourStageSkyboxLocale`**（而非 `TextComponentTranslation` / `I18n`）。`YourStageSkyboxLocale` 直接从 classpath 读取 `.lang` 文件（UTF-8），通过反射获取客户端当前语言，不受 `@SideOnly` 限制。
- 调用方式：`YourStageSkyboxLocale.translate(key)` 获取原始翻译，`YourStageSkyboxLocale.format(key, args...)` 获取格式化文本。消息通过 `TextComponentString` 发送（服务器端已翻译为文本，非翻译键）。
- `YourStageSkyboxLocale.init()` 在 `YourStageSkybox.preInit` 中调用，始终先加载 `en_us.lang` 作为 fallback，再加载当前语言文件覆盖同名键。
- 新增翻译键时须同步更新全部 4 个 `.lang` 文件。
- `§` 颜色代码可直接写在 lang 值中，它们是 Minecraft 格式化代码不参与翻译。
