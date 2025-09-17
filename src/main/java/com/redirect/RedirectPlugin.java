package com.example.redirect;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Plugin(
        id = "uuidredirect",
        name = "UUID Redirect",
        version = "1.0.4",
        description = "Redirects specific UUIDs or usernames to pre-registered Velocity servers and prevents switching",
        authors = {"Owen Osborne"}
)
public class RedirectPlugin {

    private final ProxyServer proxyServer;
    private final Map<UUID, ConfigEntry> uuidMap = new HashMap<>();
    private final Map<String, ConfigEntry> usernameMap = new HashMap<>(); // lowercase username -> entry
    private final Path pluginFolder = Path.of("plugins/UUIDRedirect");
    private final Path configPath = pluginFolder.resolve("config.json");
    private final Gson gson = new Gson();

    @Inject
    public RedirectPlugin(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;

        try {
            if (!Files.exists(pluginFolder)) {
                Files.createDirectories(pluginFolder);
                proxyServer.getConsoleCommandSource().sendMessage(
                        Component.text("Created plugin folder: " + pluginFolder)
                );
            }

            if (!Files.exists(configPath)) {
                String defaultConfig = "{\n" +
                        "  \"11111111-1111-1111-1111-111111111111\": {\n" +
                        "    \"username\": \"ExamplePlayer\",\n" +
                        "    \"server\": \"survival\"\n" +
                        "  },\n" +
                        "  \"\": {\n" +  // example username-only entry
                        "    \"username\": \"NameOnlyPlayer\",\n" +
                        "    \"server\": \"lobby\"\n" +
                        "  }\n" +
                        "}";
                Files.writeString(configPath, defaultConfig);
                proxyServer.getConsoleCommandSource().sendMessage(
                        Component.text("Created default config.json in " + configPath)
                );
            }

            loadConfig();

        } catch (IOException e) {
            proxyServer.getConsoleCommandSource().sendMessage(
                    Component.text("Failed to set up config.json: " + e.getMessage())
            );
        }
    }

    private static class ConfigEntry {
        String username;
        String server;
    }

    private void loadConfig() {
        try {
            uuidMap.clear();
            usernameMap.clear();
            String json = Files.readString(configPath);
            Type type = new TypeToken<Map<String, ConfigEntry>>() {}.getType();
            Map<String, ConfigEntry> map = gson.fromJson(json, type);

            for (Map.Entry<String, ConfigEntry> e : map.entrySet()) {
                String key = e.getKey();
                ConfigEntry entry = e.getValue();

                // Map UUID if present
                if (key != null && !key.isEmpty()) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        uuidMap.put(uuid, entry);
                    } catch (IllegalArgumentException ex) {
                        proxyServer.getConsoleCommandSource().sendMessage(
                                Component.text("Invalid UUID in config.json: " + key)
                        );
                    }
                }

                // Map username if present
                if (entry.username != null && !entry.username.isEmpty()) {
                    usernameMap.put(entry.username.toLowerCase(), entry);
                }
            }

            proxyServer.getConsoleCommandSource().sendMessage(
                    Component.text("Loaded UUID redirect config successfully!")
            );

        } catch (IOException e) {
            proxyServer.getConsoleCommandSource().sendMessage(
                    Component.text("Failed to load config.json: " + e.getMessage())
            );
        }
    }

    private void saveConfig() {
        try {
            Map<String, ConfigEntry> mapToSave = new HashMap<>();

            // Save UUID entries
            uuidMap.forEach((uuid, entry) -> mapToSave.put(uuid.toString(), entry));

            // Save username-only entries (skip duplicates)
            for (Map.Entry<String, ConfigEntry> e : usernameMap.entrySet()) {
                ConfigEntry entry = e.getValue();
                if (!uuidMap.containsValue(entry)) {
                    mapToSave.put("", entry);
                }
            }

            String json = gson.toJson(mapToSave);
            Files.writeString(configPath, json);

        } catch (IOException e) {
            proxyServer.getConsoleCommandSource().sendMessage(
                    Component.text("Failed to save config.json: " + e.getMessage())
            );
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        String currentName = player.getUsername();

        ConfigEntry entry = uuidMap.get(playerUUID);

        // If UUID entry doesn't exist, check username-only entries
        if (entry == null) {
            entry = usernameMap.get(currentName.toLowerCase());
            if (entry != null) {
                // Associate UUID for future
                uuidMap.put(playerUUID, entry);
                saveConfig();
            }
        }

        if (entry != null) {
            // Update username if changed
            if (!currentName.equals(entry.username)) {
                entry.username = currentName;
                usernameMap.put(currentName.toLowerCase(), entry);
                saveConfig();
            }

            final String serverName = entry.server; // final for lambda
            proxyServer.getServer(serverName).ifPresentOrElse(
                    server -> player.createConnectionRequest(server).connect(),
                    () -> proxyServer.getConsoleCommandSource().sendMessage(
                            Component.text("Server " + serverName + " not found for " + currentName)
                    )
            );
        }
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (uuidMap.containsKey(uuid)) {
            final String forcedServer = uuidMap.get(uuid).server; // final for lambda

            if (!event.getOriginalServer().getServerInfo().getName().equals(forcedServer)) {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                player.sendMessage(Component.text("You are not allowed to switch servers."));
            }
        }
    }
}
