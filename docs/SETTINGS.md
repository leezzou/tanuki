# 设置搜索与外观

设置搜索按标题、同义词和路径匹配固定目录。例如“夜间”定位主题模式，“存储与空间 恢复”定位应用数据恢复入口。多个词共同筛选，支持英文大小写与全角输入；无结果时可清除查询后按分类查找。

结果只负责导航：展开相应折叠区、滚动到控件并显示定位文字。清理缓存、恢复备份、导出等操作仍需用户点击原有控件，搜索不会代为执行。工作区容量、温控与省电策略定位到完整设置组件，其内部子控件不单独索引。返回结果页保留查询；根搜索页返回先清除查询，再次返回退出设置。

外观提供三种持久模式：跟随系统（默认）、浅色、深色。后台原子保存成功后更新应用内主题；失败时保留原生效模式并提示重新选择。读取失败时阻止覆盖旧设置，提供重新读取。离开页面不会中断已经开始的保存。偏好独立存放，不读取账号、密钥或工作区数据。

手动模式作用于应用内界面。接入有效主题策略的 Activity 同步系统栏背景和图标；网页内容与独立系统浮窗保留各自主题。语言仍为简体中文，字号跟随系统，不提供不可用的切换控件。

## 实现与集成

- [白名单目录与匹配](../app/src/main/kotlin/com/tanuki/ui/SettingsCatalog.kt)、[搜索与控件定位](../app/src/main/kotlin/com/tanuki/ui/SettingsSearch.kt)。
- [偏好状态](../app/src/main/kotlin/com/tanuki/ui/AppearancePreferences.kt)、[独立原子存储](../app/src/main/kotlin/com/tanuki/ui/AppearancePreferenceStore.kt)、[外观页](../app/src/main/kotlin/com/tanuki/ui/AppearanceSettingsPage.kt)。
- [根主题消费](../app/src/main/kotlin/com/tanuki/ui/TanukiDesign.kt)、[有效主题系统栏策略](../app/src/main/kotlin/com/tanuki/ui/TanukiSystemBars.kt)。

模块与设置入口、独立会话 Activity 接线分别交付；不能把模块编译和单元测试当成完整入口已合入或设备验收。实际依赖、命令、通过范围和未验证项见[本轮交接](handoffs/20261112-0053-settings-appearance.md)。
