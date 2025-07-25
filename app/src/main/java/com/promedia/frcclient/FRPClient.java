// app/src/main/java/com/promedia/frcclient/FRPClient.java
package com.promedia.frcclient;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FRPClient {

    private static final String TAG = "FRPClient_Core";
    private static final int SOCKET_TIMEOUT_MS = 10000; // 10 seconds

    private String serverAddr;
    private int serverPort;
    private String token;
    private Map<String, ProxyConfig> proxyConfigs = new HashMap<>();

    private Socket socket;
    private BufferedWriter writer;
    private BufferedReader reader;
    private volatile boolean isConnected = false; // volatile for thread visibility
    private FRPClientListener listener;
    private ScheduledExecutorService pingScheduler;
    private Thread connectionThread; // To manage the connection lifecycle

    public interface FRPClientListener {
        void onConnected();
        void onDisconnected(String reason);
        void onError(String error);
        void onLog(String message);
    }

    public FRPClient(String configContent, FRPClientListener listener) throws IllegalArgumentException {
        this.listener = listener;
        parseConfig(configContent);
        if (serverAddr == null || serverAddr.isEmpty() || serverPort == 0) {
            throw new IllegalArgumentException("Invalid FRP configuration: server_addr or server_port missing/invalid.");
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void connect() {
        if (isConnected || (connectionThread != null && connectionThread.isAlive())) {
            listener.onLog("Connection attempt already in progress or already connected.");
            return;
        }

        connectionThread = new Thread(() -> {
            try {
                listener.onLog("Connecting to " + serverAddr + ":" + serverPort + "...");
                socket = new Socket();
                socket.connect(new InetSocketAddress(serverAddr, serverPort), SOCKET_TIMEOUT_MS);
                socket.setSoTimeout(SOCKET_TIMEOUT_MS); // Set read timeout

                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                isConnected = true;
                listener.onLog("Connection established.");

                // Send login message
                sendLoginMessage();

                // Start ping mechanism
                startPing();

                // Keep reading from server (blocking call)
                String line;
                while (isConnected && socket != null && socket.isConnected() && (line = reader.readLine()) != null) {
                    listener.onLog("Received from server: " + line);
                    handleServerMessage(line);
                }

            } catch (IOException e) {
                if (isConnected) { // Only report error if we were previously connected or trying to connect
                    listener.onError("Connection error: " + e.getMessage());
                    disconnect("Connection lost: " + e.getMessage());
                } else {
                    // This is likely a failed initial connection attempt, handled by the service's retry logic
                    listener.onLog("Initial connection failed: " + e.getMessage());
                    disconnect("Initial connection failed: " + e.getMessage());
                }
            } finally {
                closeResources();
            }
        });
        connectionThread.start();
    }

    public void disconnect(String reason) {
        if (!isConnected && (socket == null || socket.isClosed())) {
            // Already disconnected or not connected
            return;
        }
        listener.onLog("Disconnecting FRP client: " + reason);
        isConnected = false; // Set flag to stop loops
        stopPing();
        closeResources();
        listener.onDisconnected(reason);
    }

    private void closeResources() {
        try {
            if (writer != null) {
                writer.close();
            }
            if (reader != null) {
                reader.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing resources: " + e.getMessage());
        } finally {
            writer = null;
            reader = null;
            socket = null;
        }
    }

    private void parseConfig(String configContent) {
        Map<String, Map<String, String>> sections = new HashMap<>();
        String currentSection = null;

        Pattern sectionPattern = Pattern.compile("^\\[([a-zA-Z0-9_]+)\\]$");
        Pattern keyValuePattern = Pattern.compile("^([a-zA-Z0-9_]+)\\s*=\\s*(.*)$");

        String[] lines = configContent.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                continue; // Skip empty lines and comments
            }

            Matcher sectionMatcher = sectionPattern.matcher(line);
            if (sectionMatcher.matches()) {
                currentSection = sectionMatcher.group(1);
                sections.put(currentSection, new HashMap<>());
                continue;
            }

            Matcher keyValueMatcher = keyValuePattern.matcher(line);
            if (keyValueMatcher.matches() && currentSection != null) {
                String key = keyValueMatcher.group(1);
                String value = keyValueMatcher.group(2);
                sections.get(currentSection).put(key, value);
            }
        }

        // Process common section
        Map<String, String> common = sections.get("common");
        if (common != null) {
            serverAddr = common.get("server_addr");
            try {
                serverPort = Integer.parseInt(common.get("server_port"));
            } catch (NumberFormatException e) {
                serverPort = 0; // Invalid port
                listener.onError("Invalid server_port in common section: " + common.get("server_port"));
            } catch (NullPointerException e) {
                listener.onError("server_port is missing in common section.");
            }
            token = common.get("token");
        }

        // Process proxy sections (e.g., [ssh], [vnc])
        for (Map.Entry<String, Map<String, String>> entry : sections.entrySet()) {
            String sectionName = entry.getKey();
            Map<String, String> sectionData = entry.getValue();

            if (!"common".equals(sectionName)) {
                String type = sectionData.get("type");
                if ("tcp".equals(type)) {
                    try {
                        ProxyConfig proxy = new ProxyConfig();
                        proxy.name = sectionName;
                        proxy.type = type;
                        proxy.localIp = sectionData.get("local_ip");
                        proxy.localPort = Integer.parseInt(sectionData.get("local_port"));
                        proxy.remotePort = Integer.parseInt(sectionData.get("remote_port"));
                        proxyConfigs.put(sectionName, proxy);
                        listener.onLog("Parsed TCP proxy: " + sectionName + " -> " + proxy.localIp + ":" + proxy.localPort + " to remote port " + proxy.remotePort);
                    } catch (NumberFormatException e) {
                        listener.onError("Invalid port number in proxy [" + sectionName + "]: " + e.getMessage());
                    } catch (NullPointerException e) {
                        listener.onError("Missing required field in proxy [" + sectionName + "]: " + e.getMessage());
                    }
                }
                // Add other proxy types if needed (e.g., udp, http, https)
            }
        }
    }

    private void sendLoginMessage() throws IOException {
        try {
            JSONObject loginMsg = new JSONObject();
            loginMsg.put("type", "Login");
            JSONObject content = new JSONObject();
            content.put("version", "0.1"); // Simplified version
            content.put("user", "android_client"); // Arbitrary user
            content.put("timestamp", System.currentTimeMillis() / 1000);
            content.put("token", token != null ? token : "");
            loginMsg.put("content", content);

            String message = loginMsg.toString();
            writer.write(message);
            writer.newLine();
            writer.flush();
            listener.onLog("Sent Login message.");
            // Call onConnected after successful login message sent
            listener.onConnected();
        } catch (JSONException e) {
            listener.onError("Failed to create login JSON: " + e.getMessage());
            disconnect("JSON error during login.");
        }
    }

    private void startPing() {
        stopPing(); // Ensure no duplicate schedulers
        pingScheduler = Executors.newSingleThreadScheduledExecutor();
        pingScheduler.scheduleAtFixedRate(() -> {
            if (isConnected && socket != null && socket.isConnected()) {
                try {
                    JSONObject pingMsg = new JSONObject();
                    pingMsg.put("type", "Ping");
                    JSONObject content = new JSONObject();
                    content.put("timestamp", System.currentTimeMillis() / 1000);
                    pingMsg.put("content", content);

                    String message = pingMsg.toString();
                    writer.write(message);
                    writer.newLine();
                    writer.flush();
                    listener.onLog("Sent Ping message.");
                } catch (IOException e) {
                    listener.onError("Ping failed: " + e.getMessage());
                    disconnect("Ping failed: " + e.getMessage());
                } catch (JSONException e) {
                    listener.onError("Failed to create ping JSON: " + e.getMessage());
                }
            } else {
                listener.onLog("Ping skipped, not connected.");
                stopPing(); // Stop ping if disconnected
            }
        }, 0, 30, TimeUnit.SECONDS); // Ping every 30 seconds
    }

    private void stopPing() {
        if (pingScheduler != null) {
            pingScheduler.shutdownNow();
            pingScheduler = null;
        }
    }

    private void handleServerMessage(String message) {
        try {
            JSONObject jsonMsg = new JSONObject(message);
            String type = jsonMsg.optString("type");

            switch (type) {
                case "Pong":
                    listener.onLog("Received Pong from server.");
                    // Reset timeout or confirm liveness
                    break;
                case "NewWork":
                    // This is where the server tells the client to establish a new connection for a proxy
                    // For this basic implementation, we will just log it.
                    listener.onLog("Server requested new work for proxy: " + jsonMsg.optJSONObject("content").optString("proxy_name"));
                    // A full implementation would now set up a new connection to the local_ip:local_port
                    // and forward data through the main FRP connection. This is out of scope for this basic client.
                    break;
                case "AuthFailed":
                    listener.onError("Authentication failed: " + jsonMsg.optJSONObject("content").optString("error"));
                    disconnect("Authentication failed.");
                    break;
                case "Error":
                    listener.onError("Server error: " + jsonMsg.optJSONObject("content").optString("error"));
                    break;
                default:
                    listener.onLog("Unknown message type from server: " + type);
                    break;
            }
        } catch (JSONException e) {
            listener.onError("Failed to parse server message JSON: " + e.getMessage() + " - Message: " + message);
        }
    }

    // Simple class to hold proxy configuration
    private static class ProxyConfig {
        String name;
        String type;
        String localIp;
        int localPort;
        int remotePort;
    }
}
