package com.example.redirect;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Plugin(
        id = "uuidredirect",
        name = "UUID Redirect",
        version = "1.0.0",
        description = "Redirects specific UUIDs to pre-registered Velocity servers and prevents switching",
        authors = {"Owen Osborne"}
)
public class RedirectPlugin {

    private final ProxyServer proxyServer;
    private final Map<UUID, String> redirectMap = new HashMap<>();
    private final Path pluginFolder = Path.of("plugins/UUIDRedirect"); // Plugin folder
    private final Path configPath = pluginFolder.resolve("config.json"); // Config file path

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

            // Create default config.json only if it doesn’t already exist
            if (!Files.exists(configPath)) {
                String defaultConfig = "{\n" +
                        "  \"11111111-1111-1111-1111-111111111111\": \"survival\"\n" +
                        "}";
                Files.writeString(configPath, defaultConfig);
                proxyServer.getConsoleCommandSource().sendMessage(
                        Component.text("Created default config.json in " + configPath)
                );
            }

            // Load UUID → ServerName mappings from config.json
            loadConfig();

        } catch (IOException e) {
            proxyServer.getConsoleCommandSource().sendMessage(
                    Component.text("Failed to set up config.json: " + e.getMessage())
            );
        }
    }

    private void loadConfig() {
        try {
            redirectMap.clear();
            String json = Files.readString(configPath);
            Gson gson = new Gson();
            Map<String, String> map = gson.fromJson(json, Map.class);

            // Convert string keys to UUID
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

    // Redirect player on login
    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (redirectMap.containsKey(playerUUID)) {
            String serverName = redirectMap.get(playerUUID);

            proxyServer.getServer(serverName).ifPresentOrElse(
                    server -> player.createConnectionRequest(server).connect(),
                    () -> proxyServer.getConsoleCommandSource().sendMessage(
                            Component.text("Server " + serverName + " not found for UUID " + playerUUID)
                    )
            );
        }
    }

    // Prevent player from switching servers if they are assigned
    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (redirectMap.containsKey(uuid)) {
            String forcedServer = redirectMap.get(uuid);

            // If they are trying to connect to a server that isn't their assigned one
            if (!event.getOriginalServer().getServerInfo().getName().equals(forcedServer)) {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                player.sendMessage(Component.text("You are not allowed to switch servers."));
            }
        }
    }
}
