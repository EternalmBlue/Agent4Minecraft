package me.eternalblue.agent4minecraft.qa

import me.eternalblue.agent4minecraft.domain.ServerPluginInfo
import org.bukkit.plugin.Plugin

object ServerPluginInventory {
    fun snapshot(plugins: Array<Plugin>): List<ServerPluginInfo> {
        return plugins
            .mapNotNull { plugin ->
                val pluginMeta = plugin.pluginMeta
                val name = pluginMeta.name.trim()
                if (name.isEmpty()) {
                    return@mapNotNull null
                }
                ServerPluginInfo(
                    name = name,
                    version = pluginMeta.version.trim(),
                    enabled = plugin.isEnabled,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}
