package me.eternalblue.agent4minecraft.i18n

enum class PluginLanguage(
    val configValue: String,
) {
    ZH_CN("zh_CN"),
    EN_US("en_US"),
    ;

    companion object {
        val DEFAULT: PluginLanguage = ZH_CN

        fun parse(value: String?): PluginLanguage {
            val normalized = value
                ?.trim()
                ?.replace('-', '_')
                ?.lowercase()
                .orEmpty()
            return when (normalized) {
                "", "zh", "zh_cn", "cn", "chinese", "simplified_chinese", "简体中文" -> ZH_CN
                "en", "en_us", "english" -> EN_US
                else -> throw IllegalArgumentException(
                    "plugin.language must be one of: zh_CN, en_US.",
                )
            }
        }
    }
}
