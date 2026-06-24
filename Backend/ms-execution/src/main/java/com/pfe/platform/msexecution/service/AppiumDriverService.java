package com.pfe.platform.msexecution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
public class AppiumDriverService {

    private static final Logger log = LoggerFactory.getLogger(AppiumDriverService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String CONTAINER_NAME = "testauto-android";
    private static final String DOCKER_IMAGE = "budtmo/docker-android:emulator_14.0";
    private static final int APPIUM_PORT = 4725;

    private final String appiumServerUrl = "http://127.0.0.1:" + APPIUM_PORT;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private String sessionId;
    private int screenWidth = 1080;
    private int screenHeight = 2340;

    public int getScreenWidth() { return screenWidth; }
    public int getScreenHeight() { return screenHeight; }

    public void ensureEmulatorRunning() throws Exception {
        if (isContainerRunning()) {
            log.info("[APPIUM] Android emulator container already running");
        } else if (isContainerExists()) {
            log.info("[APPIUM] Starting existing container {}", CONTAINER_NAME);
            exec("docker", "start", CONTAINER_NAME);
        } else {
            log.info("[APPIUM] Pulling and starting Android emulator container...");
            exec("docker", "run", "-d",
                    "--name", CONTAINER_NAME,
                    "--privileged",
                    "--device", "/dev/kvm",
                    "--group-add", kvmGroupId(),
                    "-p", APPIUM_PORT + ":4723",
                    "-p", "6090:6080",
                    "-e", "EMULATOR_DEVICE=Samsung Galaxy S10",
                    "-e", "WEB_VNC=true",
                    "-e", "APPIUM=true",
                    DOCKER_IMAGE);
        }

        waitForAppiumReady(300);
        waitForDeviceBoot(180);
    }

    public void copyApkToContainer(String hostApkPath) throws Exception {
        String containerPath = "/tmp/" + Path.of(hostApkPath).getFileName().toString();
        exec("docker", "cp", hostApkPath, CONTAINER_NAME + ":" + containerPath);
        log.info("[APPIUM] APK copied to container: {}", containerPath);
    }

    public String getContainerApkPath(String hostApkPath) {
        return "/tmp/" + Path.of(hostApkPath).getFileName().toString();
    }

    public String createSession(String apkPath) throws Exception {
        log.info("[APPIUM] Creating session with APK: {}", apkPath);

        ObjectNode capabilities = mapper.createObjectNode();
        ObjectNode alwaysMatch = mapper.createObjectNode();
        alwaysMatch.put("platformName", "Android");
        alwaysMatch.put("appium:automationName", "UiAutomator2");
        alwaysMatch.put("appium:app", apkPath);
        alwaysMatch.put("appium:newCommandTimeout", 300);
        alwaysMatch.put("appium:autoGrantPermissions", true);
        alwaysMatch.put("appium:noReset", false);
        alwaysMatch.put("appium:fullReset", false);
        capabilities.set("alwaysMatch", alwaysMatch);

        ObjectNode body = mapper.createObjectNode();
        body.set("capabilities", capabilities);

        String response = post("/session", body.toString());
        JsonNode root = mapper.readTree(response);
        sessionId = root.path("value").path("sessionId").asText();

        JsonNode windowRect = root.path("value").path("capabilities");
        if (windowRect.has("deviceScreenSize")) {
            String size = windowRect.get("deviceScreenSize").asText();
            String[] parts = size.split("x");
            if (parts.length == 2) {
                screenWidth = Integer.parseInt(parts[0]);
                screenHeight = Integer.parseInt(parts[1]);
            }
        }

        log.info("[APPIUM] Session created: {} (screen {}x{})", sessionId, screenWidth, screenHeight);
        return sessionId;
    }

    public void deleteSession() {
        if (sessionId == null) return;
        try {
            delete("/session/" + sessionId);
            log.info("[APPIUM] Session deleted: {}", sessionId);
        } catch (Exception e) {
            log.warn("[APPIUM] Failed to delete session: {}", e.getMessage());
        }
        sessionId = null;
    }

    public byte[] takeScreenshot() throws Exception {
        String response = get("/session/" + sessionId + "/screenshot");
        JsonNode root = mapper.readTree(response);
        String base64 = root.path("value").asText();
        return Base64.getDecoder().decode(base64);
    }

    public String getPageSource() throws Exception {
        String response = get("/session/" + sessionId + "/source");
        JsonNode root = mapper.readTree(response);
        return root.path("value").asText();
    }

    /** A tappable UI element with its label and exact center coordinates (device space). */
    public static class UiElement {
        public final String label;
        public final int centerX;
        public final int centerY;
        public final boolean editable;

        public UiElement(String label, int centerX, int centerY, boolean editable) {
            this.label = label;
            this.centerX = centerX;
            this.centerY = centerY;
            this.editable = editable;
        }
    }

    private static final java.util.regex.Pattern BOUNDS_PATTERN =
            java.util.regex.Pattern.compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");

    /**
     * Parse the Appium page source and return interactive elements (clickable, or carrying
     * text / content-desc) with their EXACT center coordinates in device space.
     * No coordinate scaling needed — these come straight from the accessibility tree.
     */
    public java.util.List<UiElement> getInteractiveElements() throws Exception {
        String xml = getPageSource();
        java.util.List<UiElement> elements = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();

        try {
            javax.xml.parsers.DocumentBuilderFactory factory =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(
                    new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

            org.w3c.dom.NodeList all = doc.getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                org.w3c.dom.Node node = all.item(i);
                if (!(node instanceof org.w3c.dom.Element el)) continue;

                String text = attr(el, "text");
                String desc = attr(el, "content-desc");
                String clickable = attr(el, "clickable");
                String className = attr(el, "class");
                String bounds = attr(el, "bounds");

                boolean isEditable = className != null && className.toLowerCase().contains("edittext");
                boolean hasLabel = (text != null && !text.isBlank()) || (desc != null && !desc.isBlank());
                boolean isClickable = "true".equals(clickable);

                if (!hasLabel && !isClickable && !isEditable) continue;
                if (bounds == null) continue;

                java.util.regex.Matcher m = BOUNDS_PATTERN.matcher(bounds);
                if (!m.find()) continue;
                int x1 = Integer.parseInt(m.group(1));
                int y1 = Integer.parseInt(m.group(2));
                int x2 = Integer.parseInt(m.group(3));
                int y2 = Integer.parseInt(m.group(4));
                if (x2 <= x1 || y2 <= y1) continue;
                // Skip the full-screen root containers
                if (x1 == 0 && y1 == 0 && x2 >= screenWidth && y2 >= screenHeight) continue;

                int cx = (x1 + x2) / 2;
                int cy = (y1 + y2) / 2;

                String label = (text != null && !text.isBlank()) ? text.trim()
                        : (desc != null && !desc.isBlank()) ? desc.trim()
                        : (isEditable ? "[champ de saisie]" : "[zone cliquable]");
                if (label.length() > 60) label = label.substring(0, 60);

                String key = label + "@" + cx + "," + cy;
                if (seen.add(key)) {
                    elements.add(new UiElement(label, cx, cy, isEditable));
                }
            }
        } catch (Exception e) {
            log.warn("[APPIUM] Failed to parse interactive elements: {}", e.getMessage());
        }
        return elements;
    }

    private String attr(org.w3c.dom.Element el, String name) {
        String v = el.getAttribute(name);
        return v == null || v.isEmpty() ? null : v;
    }

    public String getCurrentActivity() throws Exception {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("script", "mobile: getCurrentActivity");
            body.set("args", mapper.createArrayNode());
            String response = post("/session/" + sessionId + "/execute", body.toString());
            JsonNode root = mapper.readTree(response);
            return root.path("value").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    public void tap(int x, int y) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode actions = mapper.createArrayNode();

        ObjectNode pointer = mapper.createObjectNode();
        pointer.put("type", "pointer");
        pointer.put("id", "finger1");
        ObjectNode params = mapper.createObjectNode();
        params.put("pointerType", "touch");
        pointer.set("parameters", params);

        ArrayNode pointerActions = mapper.createArrayNode();

        ObjectNode move = mapper.createObjectNode();
        move.put("type", "pointerMove");
        move.put("duration", 0);
        move.put("x", x);
        move.put("y", y);
        move.put("origin", "viewport");
        pointerActions.add(move);

        ObjectNode down = mapper.createObjectNode();
        down.put("type", "pointerDown");
        down.put("button", 0);
        pointerActions.add(down);

        ObjectNode pause = mapper.createObjectNode();
        pause.put("type", "pause");
        pause.put("duration", 100);
        pointerActions.add(pause);

        ObjectNode up = mapper.createObjectNode();
        up.put("type", "pointerUp");
        up.put("button", 0);
        pointerActions.add(up);

        pointer.set("actions", pointerActions);
        actions.add(pointer);
        body.set("actions", actions);

        post("/session/" + sessionId + "/actions", body.toString());
    }

    public void swipeUp() throws Exception {
        swipe(screenWidth / 2, (int) (screenHeight * 0.7),
              screenWidth / 2, (int) (screenHeight * 0.3), 600);
    }

    public void swipeDown() throws Exception {
        swipe(screenWidth / 2, (int) (screenHeight * 0.3),
              screenWidth / 2, (int) (screenHeight * 0.7), 600);
    }

    public void pressBack() throws Exception {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode actions = mapper.createArrayNode();

        ObjectNode key = mapper.createObjectNode();
        key.put("type", "key");
        key.put("id", "keyboard");

        ArrayNode keyActions = mapper.createArrayNode();

        ObjectNode keyDown = mapper.createObjectNode();
        keyDown.put("type", "keyDown");
        keyDown.put("value", "");
        keyActions.add(keyDown);

        ObjectNode keyUp = mapper.createObjectNode();
        keyUp.put("type", "keyUp");
        keyUp.put("value", "");
        keyActions.add(keyUp);

        key.set("actions", keyActions);
        actions.add(key);
        body.set("actions", actions);

        try {
            post("/session/" + sessionId + "/actions", body.toString());
        } catch (Exception e) {
            ObjectNode backBody = mapper.createObjectNode();
            backBody.put("script", "mobile: pressKey");
            ArrayNode args = mapper.createArrayNode();
            ObjectNode arg = mapper.createObjectNode();
            arg.put("keycode", 4);
            args.add(arg);
            backBody.set("args", args);
            post("/session/" + sessionId + "/execute", backBody.toString());
        }
    }

    public void typeText(String text) throws Exception {
        String activeEl = get("/session/" + sessionId + "/element/active");
        JsonNode root = mapper.readTree(activeEl);
        String elementId = null;
        JsonNode valueNode = root.path("value");
        if (valueNode.fields().hasNext()) {
            elementId = valueNode.fields().next().getValue().asText();
        }

        if (elementId != null) {
            ObjectNode body = mapper.createObjectNode();
            body.put("text", text);
            post("/session/" + sessionId + "/element/" + elementId + "/value", body.toString());
        }
    }

    // ── Private helpers ──

    private void swipe(int startX, int startY, int endX, int endY, int durationMs) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode actions = mapper.createArrayNode();

        ObjectNode pointer = mapper.createObjectNode();
        pointer.put("type", "pointer");
        pointer.put("id", "finger1");
        ObjectNode params = mapper.createObjectNode();
        params.put("pointerType", "touch");
        pointer.set("parameters", params);

        ArrayNode pointerActions = mapper.createArrayNode();

        ObjectNode move1 = mapper.createObjectNode();
        move1.put("type", "pointerMove");
        move1.put("duration", 0);
        move1.put("x", startX);
        move1.put("y", startY);
        move1.put("origin", "viewport");
        pointerActions.add(move1);

        ObjectNode down = mapper.createObjectNode();
        down.put("type", "pointerDown");
        down.put("button", 0);
        pointerActions.add(down);

        ObjectNode move2 = mapper.createObjectNode();
        move2.put("type", "pointerMove");
        move2.put("duration", durationMs);
        move2.put("x", endX);
        move2.put("y", endY);
        move2.put("origin", "viewport");
        pointerActions.add(move2);

        ObjectNode up = mapper.createObjectNode();
        up.put("type", "pointerUp");
        up.put("button", 0);
        pointerActions.add(up);

        pointer.set("actions", pointerActions);
        actions.add(pointer);
        body.set("actions", actions);

        post("/session/" + sessionId + "/actions", body.toString());
    }

    private String get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(appiumServerUrl + path))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Appium GET " + path + " failed (" + response.statusCode() + "): " + response.body());
        }
        return response.body();
    }

    private String post(String path, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(appiumServerUrl + path))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Appium POST " + path + " failed (" + response.statusCode() + "): "
                    + truncate(response.body(), 500));
        }
        return response.body();
    }

    private void delete(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(appiumServerUrl + path))
                .timeout(Duration.ofSeconds(30))
                .DELETE()
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }

    // ── Docker management ──

    /**
     * GID propriétaire de /dev/kvm. L'utilisateur du conteneur (androidusr) doit
     * appartenir à ce groupe pour démarrer l'émulateur, sinon Appium ne trouve
     * aucun device. Détecté dynamiquement, repli sur 109 (valeur usuelle).
     */
    private String kvmGroupId() {
        try {
            String gid = execOutput("docker", "run", "--rm", "--device", "/dev/kvm",
                    "--entrypoint", "stat", DOCKER_IMAGE, "-c", "%g", "/dev/kvm").trim();
            if (gid.matches("\\d+")) {
                log.info("[APPIUM] /dev/kvm group id detected: {}", gid);
                return gid;
            }
        } catch (Exception e) {
            log.warn("[APPIUM] Could not detect /dev/kvm gid, falling back to 109: {}", e.getMessage());
        }
        return "109";
    }

    private boolean isContainerRunning() {
        try {
            String output = execOutput("docker", "inspect", "-f", "{{.State.Running}}", CONTAINER_NAME);
            return "true".equals(output.trim());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isContainerExists() {
        try {
            execOutput("docker", "inspect", CONTAINER_NAME);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void waitForDeviceBoot(int timeoutSeconds) throws Exception {
        log.info("[APPIUM] Waiting for Android device to fully boot (max {}s)...", timeoutSeconds);
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                String output = execOutput("docker", "exec", CONTAINER_NAME,
                        "adb", "shell", "getprop", "sys.boot_completed");
                String anim = "";
                try {
                    anim = execOutput("docker", "exec", CONTAINER_NAME,
                            "adb", "shell", "getprop", "init.svc.bootanim").trim();
                } catch (Exception ignored) {}
                // boot_completed=1 ET animation terminée → l'UI (et l'app Appium Settings) est prête
                if ("1".equals(output.trim()) && "stopped".equals(anim)) {
                    log.info("[APPIUM] Android device fully booted (bootanim stopped)");
                    Thread.sleep(5000);
                    return;
                }
            } catch (Exception ignored) {}
            Thread.sleep(5000);
        }
        throw new RuntimeException("Android device not fully booted after " + timeoutSeconds + "s");
    }

    private void waitForAppiumReady(int timeoutSeconds) throws Exception {
        log.info("[APPIUM] Waiting for emulator + Appium to be ready (max {}s)...", timeoutSeconds);
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + APPIUM_PORT + "/status"))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
                HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (resp.body().contains("\"ready\":true")) {
                    log.info("[APPIUM] Appium server ready inside container");
                    return;
                }
            } catch (Exception ignored) {}
            Thread.sleep(5000);
        }
        throw new RuntimeException("Android emulator/Appium not ready after " + timeoutSeconds + "s");
    }

    private void exec(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                log.debug("[DOCKER] {}", line);
            }
        }
        if (!p.waitFor(120, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new RuntimeException("Docker command timed out: " + String.join(" ", cmd));
        }
        if (p.exitValue() != 0) {
            throw new RuntimeException("Docker command failed (exit " + p.exitValue() + "): " + String.join(" ", cmd));
        }
    }

    private String execOutput(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new RuntimeException("Command timed out");
        }
        if (p.exitValue() != 0) {
            throw new RuntimeException("Command failed: " + sb);
        }
        return sb.toString();
    }
}
