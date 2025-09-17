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
        version = "1.0.1",
        description = "Redirects specific UUIDs to pre-registered Velocity servers and prevents switching",
        authors = {"Owen Osborne"}
)
public class RedirectPlugin {

    private final ProxyServer proxyServer;
    private final Map<UUID, ConfigEntry> redirectMap = new HashMap<>();
    private final Path pluginFolder = Path.of("plugins/UUIDRedirect"); 
    private final Path configPath = pluginFolder.resolve("config.json"); 

    private final Gson gson = new Gson();

    @Inject
    public RedirectPlugin(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;

        try {
            // Ensure plugin folder exists
            if (!Files.exists(pluginFolder)) {
                Files.createDirectories(pluginFolder);
                proxyServer.getConsoleCommandSource().sendMessage(
                        Component.text("Created plugin folder: " + pluginFolder)
                );
            }

            // Create default config.json if it doesn't exist
            if (!Files.exists(configPath)) {
                String defaultConfig = "{\n" +
                        "  \"11111111-1111-1111-1111-111111111111\": {\n" +
                        "    \"username\": \"ExamplePlayer\",\n" +
                        "    \"server\": \"survival\"\n" +
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

    // ConfigEntry stores both username and server
    private static class ConfigEntry {
        String username;
        String server;
    }

    private void loadConfig() {
        try {
            redirectMap.clear();
            String json = Files.readString(configPath);
            Type type = new TypeToken<Map<String, ConfigEntry>>() {}.getType();
            Map<String, ConfigEntry> map = gson.fromJson(json, type);

            map.forEach((k, v) -> {
                try {
                    redirectMap.put(UUID.fromString(k), v);
                } catch (IllegalArgumentException e) {
                    proxyServer.getConsoleCommandSource().sendMessage(
                            Component.text("Invalid UUID in config.json: " + k)
                    );
                }
            });

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
            redirectMap.forEach((uuid, entry) -> mapToSave.put(uuid.toString(), entry));
            String json = gson.toJson(mapToSave);
            Files.writeString(configPath, json);
        } catch (IOException e) {
            proxyServer.getConsoleCommandSource().sendMessage(
                    Component.text("Failed to save config.json: " + e.getMessage())
            );
        }
    }

    // Redirect player on login and update username if changed
    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        String currentName = player.getUsername();

        if (redirectMap.containsKey(playerUUID)) {
            ConfigEntry entry = redirectMap.get(playerUUID);

            // Update username if changed
            if (!currentName.equals(entry.username)) {
                entry.username = currentName;
                saveConfig();
            }

            String serverName = entry.server;
            proxyServer.getServer(serverName).ifPresentOrElse(
                    server -> player.createConnectionRequest(server).connect(),
                    () -> proxyServer.getConsoleCommandSource().sendMessage(
                            Component.text("Server " + serverName + " not found for UUID " + playerUUID)
                    )
            );
        }
    }

    // Prevent player from switching servers if assigned
    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (redirectMap.containsKey(uuid)) {
            String forcedServer = redirectMap.get(uuid).server;

            if (!event.getOriginalServer().getServerInfo().getName().equals(forcedServer)) {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                player.sendMessage(Component.text("You are not allowed to switch servers."));
            }
        }
    }
}
