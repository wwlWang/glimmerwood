# R116 试玩记录（playtest pass, 2026-10-26）

基线 `f3cc83c` (R116)，headless Xvfb :99，llvmpipe 软渲染（低 FPS 属预期）。世界 `orimod_dev_pt116`。
截图：`/workspace/oriMod/shots/playtest-r116-*`。每个区域测完即追加，标注 **[复现]** / **[怀疑]**。

## 环境
- 启动前清理了上次中断遗留的 `gradlew runDevtoolsClient`（pid 472850/472907）。
- 日志噪声（非 bug）：OpenAL 无设备（Xvfb 无声卡）、GLSL 4.60 shader compiler error（llvmpipe 4.5）、mixin 对未安装 Mek/IE 等的 Error loading class 警告。

## 区域 1：移动（devtools input_script + record_motion，`/ori unlock all`）
- 地面跳：峰值 1.62 格 / 13 tick 空中，下落逐 tick 加速（-0.098→-0.47 …）**[通过]**
- 潜行跳：潜行按住时可起跳，峰值同上 **[通过]**
- 二段跳（顶点附近第二次按）：峰值 2.28；单点按松不触发滑翔；按住第二跳进入 glide，匀速下沉 ≈0.12/tick **[通过，滑翔匀速是设计]**
- 蹬墙：wall_slide → wall_jump 触发，离墙 ≈3 格 **[通过]**
- 翻上沿（mantle）：墙顶高于脚 2、3 格可翻上；4 格退化为蹬墙。lastAction 不记录 mantle（仅客户端 SpiritMantle），无法由状态字段验证，只由终点位置推断 **[通过/信息]**
- 游泳：不按疾跑 = 站姿下沉（原版行为）；疾跑+前进 → `SWIMMING`、速度正常、第三人称为俯冲姿势 **[通过]**
- 截图：`shots/playtest-r116-swim-*.png`、`fp-*`、`tpf-*`
