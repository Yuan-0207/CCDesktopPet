                                                    # BabyDragon 动作索引说明

> 中文注释：本文件用于记录 `BabyDragon.glb` 内的动画索引与含义，方便后续配置互动按钮映射。
>  
> 中文注释：索引来自当前项目内文件 `app/src/main/assets/models/BabyDragon.glb` 的实际解析结果。

## 当前模型文件

- 路径：`app/src/main/assets/models/BabyDragon.glb`
- 动画总数：`16`

## 动作索引表

| 索引 | 动作名 | 中文说明 | 备注 |
| --- | --- | --- | --- |
| 0 | Agree_Gesture | 点头/同意手势 | 适合“打招呼” |
| 1 | Alert | 警觉姿态 | 可用于“注意我” |
| 2 | All_Night_Dance | 持续舞蹈 | 时长较长 |
| 3 | Arise | 起身动作 | 过渡动作 |
| 4 | Attack | 攻击动作 | 进攻姿态 |
| 5 | BeHit_FlyUp | 受击飞起 | 反馈类动作 |
| 6 | Boom_Dance | 爆发舞蹈 | 适合“卖萌/庆祝” |
| 7 | Boxing_Practice | 练拳 | 活力动作 |
| 8 | Casual_Walk | 休闲走路 | 当前默认待机动作 |
| 9 | Dead | 倒地/死亡 | 不建议常规互动使用 |
| 10 | running | 跑步 | 极短过渡动作 |
| 11 | Skill_01 | 技能动作 01 | 当前用于“摸摸头” |
| 12 | Triple_Combo_Attack | 三连击 | 攻击演示动作 |
| 13 | Unsteady_Walk | 踉跄行走 | 特殊状态动作 |
| 14 | walking_man | 普通行走 | 短行走动作 |
| 15 | You_Groove | 律动舞蹈 | 时长较长 |

## 当前互动按钮映射（代码已接入）

> 中文注释：以下映射对应 `PetInteraction.kt` 里小龙崽专用配置。

- 打招呼 -> 索引 `0` (`Agree_Gesture`)
- 摸摸头 -> 索引 `11` (`Skill_01`)
- 卖个萌 -> 索引 `6` (`Boom_Dance`)

## 动态追加规则（已升级）

> 中文注释：互动列表已升级为“核心动作 + 自动追加动作”混合模式。

- 核心动作固定保留：`打招呼 / 摸摸头 / 卖个萌`
- 自动追加：遍历模型动画，把未被核心占用的动作加入互动列表
- 默认待机动作不进互动列表：索引 `8` (`Casual_Walk`)
- 自动追加动作命名：优先用“中文别名映射”
- 若出现“未配置中文别名”的动作：暂不展示，并在日志中记录待确认

## 中文别名映射（用于自动追加动作）

> 中文注释：该映射与 `PetInteraction.kt` 中 `aliasByAnimationName/aliasByAnimationIndex` 保持一致。

| 动作名 | 中文别名 |
| --- | --- |
| Agree_Gesture | 打招呼 |
| Alert | 警觉 |
| All_Night_Dance | 整夜热舞 |
| Arise | 起身 |
| Attack | 攻击 |
| BeHit_FlyUp | 受击飞起 |
| Boom_Dance | 卖个萌 |
| Boxing_Practice | 练拳 |
| Casual_Walk | 休闲走路 |
| Dead | 倒地 |
| running | 跑步 |
| Skill_01 | 摸摸头 |
| Triple_Combo_Attack | 三连击 |
| Unsteady_Walk | 踉跄走 |
| walking_man | 普通走路 |
| You_Groove | 律动舞蹈 |

## 待命名动作清单（持续维护）

> 中文注释：当替换新模型或动作集后，如出现未命名动作，请先在此处补中文，再更新代码映射。

- 当前版本：无

## 默认动作说明

> 中文注释：为了保持原有体验，默认待机动作固定为 `Casual_Walk`。

- 默认待机索引：`8`
- 动作名：`Casual_Walk`

