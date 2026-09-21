package com.builtbyotte.bbbtestservernotify;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

public class BBBTestServerNotify extends JavaPlugin {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (getConfig().getBoolean("notify-on-start", true)) {
            sendWebhookAsync();
        }

        getLogger().info("BBBTestServerNotify has been enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("BBBTestServerNotify has been disabled.");
    }

    private void sendWebhookAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                sendWebhook();
            } catch (Exception e) {
                getLogger().warning("Could not send Discord webhook: " + e.getMessage());
            }
        });
    }

    private void sendWebhook() throws IOException, InterruptedException {
        String webhookUrl = getConfig().getString("webhook-url", "");

        if (webhookUrl.isBlank() || webhookUrl.contains("YOUR_WEBHOOK_ID") || webhookUrl.contains("JOUW_WEBHOOK_ID")) {
            getLogger().warning("No valid webhook-url set in config.yml. Notification skipped.");
            return;
        }

        String serverName = resolveServerName();
        String address = resolveAddress();
        boolean showAddress = getConfig().getBoolean("show-address", true);

        String title = getConfig().getString("message.title", "Test server started");
        String description = getConfig().getString("message.description", "");
        int color = parseColor(getConfig().getString("message.color", "#57F287"));
        String footer = getConfig().getString("message.footer", "BBBTestServerNotify");
        String username = getConfig().getString("message.username", "");
        String avatarUrl = getConfig().getString("message.avatar-url", "");

        StringBuilder fields = new StringBuilder();
        fields.append("{\"name\":\"Server\",\"value\":\"").append(jsonEscape(serverName)).append("\",\"inline\":true}");
        if (showAddress && !address.isBlank()) {
            fields.append(",{\"name\":\"Address\",\"value\":\"").append(jsonEscape(address)).append("\",\"inline\":true}");
        }
        fields.append(",{\"name\":\"Time\",\"value\":\"").append(jsonEscape(Instant.now().toString())).append("\",\"inline\":false}");

        StringBuilder json = new StringBuilder();
        json.append("{");
        if (!username.isBlank()) {
            json.append("\"username\":\"").append(jsonEscape(username)).append("\",");
        }
        if (!avatarUrl.isBlank()) {
            json.append("\"avatar_url\":\"").append(jsonEscape(avatarUrl)).append("\",");
        }
        json.append("\"embeds\":[{")
                .append("\"title\":\"").append(jsonEscape(title)).append("\",")
                .append("\"description\":\"").append(jsonEscape(description)).append("\",")
                .append("\"color\":").append(color).append(",")
                .append("\"fields\":[").append(fields).append("],")
                .append("\"footer\":{\"text\":\"").append(jsonEscape(footer)).append("\"},")
                .append("\"timestamp\":\"").append(Instant.now().toString()).append("\"")
                .append("}]");
        json.append("}");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            getLogger().info("Discord webhook sent (status " + response.statusCode() + ").");
        } else {
            getLogger().warning("Discord webhook returned status " + response.statusCode() + ": " + response.body());
        }
    }

    private String resolveServerName() {
        String configured = getConfig().getString("server-name", "auto");
        if (configured == null || configured.equalsIgnoreCase("auto") || configured.isBlank()) {
            String motd = Bukkit.getMotd();
            return (motd == null || motd.isBlank()) ? "Unknown test server" : ChatColor.stripColor(motd);
        }
        return configured;
    }

    private String resolveAddress() {
        String manual = getConfig().getString("public-address", "");
        if (manual != null && !manual.isBlank()) {
            return manual;
        }
        String ip = Bukkit.getIp();
        int port = Bukkit.getPort();
        if (ip == null || ip.isBlank()) {
            return "port " + port;
        }
        return ip + ":" + port;
    }

    private int parseColor(String hex) {
        try {
            String clean = hex.replace("#", "");
            return Integer.parseInt(clean, 16);
        } catch (Exception e) {
            return 0x57F287;
        }
    }

    private String jsonEscape(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
