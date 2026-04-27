package me.eternalblue.agent4minecraft.i18n

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

object PluginMessageLoader {
    private const val LANGUAGE_DIR = "lang"

    fun load(
        plugin: JavaPlugin,
        language: PluginLanguage,
    ): PluginMessages {
        ensureLanguageFile(plugin, PluginLanguage.ZH_CN)
        ensureLanguageFile(plugin, PluginLanguage.EN_US)

        val selectedTemplates = loadTemplates(plugin.languageFile(language))
        val fallbackTemplates = loadTemplates(plugin.languageFile(PluginLanguage.EN_US))
        return PluginMessages(
            templates = selectedTemplates,
            fallbackTemplates = fallbackTemplates,
        )
    }

    internal fun loadFromFiles(
        selectedFile: File,
        fallbackFile: File = selectedFile,
    ): PluginMessages {
        return PluginMessages(
            templates = loadTemplates(selectedFile),
            fallbackTemplates = loadTemplates(fallbackFile),
        )
    }

    private fun ensureLanguageFile(
        plugin: JavaPlugin,
        language: PluginLanguage,
    ) {
        val target = plugin.languageFile(language)
        if (!target.exists()) {
            plugin.saveResource("$LANGUAGE_DIR/${language.configValue}.yml", false)
        }
    }

    private fun JavaPlugin.languageFile(language: PluginLanguage): File {
        return dataFolder.resolve(LANGUAGE_DIR).resolve("${language.configValue}.yml")
    }

    private fun loadTemplates(file: File): Map<String, String> {
        val configuration = YamlConfiguration.loadConfiguration(file)
        val templates = linkedMapOf<String, String>()
        flatten(configuration, prefix = "", output = templates)
        return templates
    }

    private fun flatten(
        section: ConfigurationSection,
        prefix: String,
        output: MutableMap<String, String>,
    ) {
        section.getKeys(false).forEach { key ->
            val path = if (prefix.isBlank()) key else "$prefix.$key"
            val nested = section.getConfigurationSection(key)
            if (nested != null) {
                flatten(nested, path, output)
                return@forEach
            }
            section.getString(key)?.let { value ->
                output[path] = value
            }
        }
    }
}
