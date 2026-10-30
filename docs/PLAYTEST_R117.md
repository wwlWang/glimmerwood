# R117 试玩记录（Pass 4，2026-10-30）

基线 `9c8770f`（R117 C），自 `b8c9f42` 起新增 4 个提交：
- `5f5898b` R116 forest_spring v1：分级地形、流体围护、外部选址/预留（大量 worldgen 证据与 GameTest）。
- `47907ff` R117 A：解锁弓/锤/矛自动赠送武器；能量不足/冷却时按住不再重复播放动作（拒绝锁存）；腐光/风暴祭坛击败后 24000 tick 冷却（方块实体保存）；攀爬/Sticky 只对 `#orimod:spirit_climbable`（原木/树叶/藤蔓/梯子等）生效，蹬墙跳/下滑仍对任意墙；锤下砸与 Stomp 加速下落（-1.2→-3.3 b/t），冲击随高度放大（封顶 24 格）。
- `81ec23c` R117 B：一人称回到原版 ItemInHandRenderer（爪子贴合原版手臂/物品）；灵矛按住举起、松开投掷；滑翔羽毛横举头顶；HUD 生命=原版血条位、能量=饥饿条位，每行 10 格向上堆叠。
- `9c8770f` R117 C：碎片槽界面（4 槽网格、拖拽交换、锁定槽提示）；6 个新碎片（磁石/深息/夜目/矿脉感应/余烬之肤/丰收）；新金索树 Course 3 / Escape 2：水之心在底部、技能攀升到树冠出口、缺技能拒绝、分段水速 + 服务端倍率、封闭水体渲染。

Headless Xvfb :99 + llvmpipe，`./gradlew --no-daemon runDevtoolsClient`。截图 `/workspace/oriMod/shots/playtest-p4-*`，脚本 `/workspace/oriMod/pt117/`。**不改代码**，只记录。标注 **[已修复] / [仍存在] / [有变化] / [新·复现] / [新·怀疑]**。

