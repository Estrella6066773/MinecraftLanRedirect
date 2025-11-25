package org.est.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AppConfig {
    private Remote remote;
    private Local local;
    private Lan lan;
    private Security security;
    private Credentials credentials;
    private Logging logging;

    public AppConfig() {
    }

    private AppConfig(Remote remote,
                      Local local,
                      Lan lan,
                      Security security,
                      Credentials credentials,
                      Logging logging) {
        this.remote = remote;
        this.local = local;
        this.lan = lan;
        this.security = security;
        this.credentials = credentials;
        this.logging = logging;
    }

    public static AppConfig load(Path configPath) {
        Objects.requireNonNull(configPath, "configPath");
        try (InputStream in = Files.newInputStream(configPath)) {
            return load(in);
        } catch (Exception e) {
            throw new IllegalStateException("无法读取配置文件: " + configPath, e);
        }
    }

    public static AppConfig load(InputStream inputStream) {
        Objects.requireNonNull(inputStream, "inputStream");
        Yaml yaml = new Yaml();
        Object raw = yaml.load(inputStream);
        return asConfig(raw).withDefaults();
    }

    @SuppressWarnings("unchecked")
    private static AppConfig asConfig(Object raw) {
        if (!(raw instanceof Map)) {
            return new AppConfig();
        }
        Map<String, Object> root = (Map<String, Object>) raw;
        Remote remote = parseRemote(root.get("remote"));
        Local local = parseLocal(root.get("local"));
        Lan lan = parseLan(root.get("lan"));
        Security security = parseSecurity(root.get("security"));
        Credentials credentials = parseCredentials(root.get("credentials"));
        Logging logging = parseLogging(root.get("logging"));
        return new AppConfig(remote, local, lan, security, credentials, logging);
    }

    private static Remote parseRemote(Object value) {
        Map<String, Object> map = asMap(value);
        if (map == null) {
            return null;
        }
        String host = asString(map.get("host"));
        Integer port = asInteger(map.get("port"));
        if (host == null && port == null) {
            return null;
        }
        return new Remote(host != null ? host : "localhost", port != null ? port : 25565);
    }

    private static Local parseLocal(Object value) {
        Map<String, Object> map = asMap(value);
        if (map == null) {
            return null;
        }
        Integer listen = asInteger(map.get("listenPort"));
        if (listen == null) {
            return null;
        }
        return new Local(listen);
    }

    private static Lan parseLan(Object value) {
        Map<String, Object> map = asMap(value);
        if (map == null) {
            return null;
        }
        String motd = asString(map.get("motd"));
        String version = asString(map.get("version"));
        Integer maxPlayers = asInteger(map.get("maxPlayers"));
        Long interval = asLong(map.get("announceIntervalMs"));
        Integer broadcastPort = asInteger(map.get("broadcastPort"));
        String broadcastAddress = asString(map.get("broadcastAddress"));
        return new Lan(
                motd != null ? motd : "Minecraft Proxy",
                version != null ? version : "1.20.x",
                maxPlayers != null ? maxPlayers : 20,
                interval != null ? interval : 1000L,
                broadcastPort != null ? broadcastPort : 4445,
                broadcastAddress != null ? broadcastAddress : "255.255.255.255"
        );
    }

    private static Security parseSecurity(Object value) {
        Map<String, Object> map = asMap(value);
        if (map == null) {
            return null;
        }
        List<String> whitelist = asStringList(map.get("whitelist"));
        return new Security(whitelist != null ? whitelist : new ArrayList<String>());
    }

    private static Credentials parseCredentials(Object value) {
        Map<String, Object> map = asMap(value);
        if (map == null) {
            return null;
        }
        Boolean enabled = asBoolean(map.get("enabled"));
        String token = asString(map.get("token"));
        return new Credentials(enabled != null && enabled, token != null ? token : "");
    }

    private static Logging parseLogging(Object value) {
        Map<String, Object> map = asMap(value);
        if (map == null) {
            return null;
        }
        String level = asString(map.get("level"));
        return new Logging(level != null ? level : "INFO");
    }

    public AppConfig withDefaults() {
        Remote resolvedRemote = remote != null ? remote : new Remote("localhost", 25565);
        Local resolvedLocal = local != null ? local : new Local(25565);
        if (lan != null) {
            lan.ensureDefaults();
        }
        Lan resolvedLan = lan != null ? lan : new Lan("Minecraft Proxy", "1.20.x", 20, 1000L, 4445, "255.255.255.255");
        resolvedLan.ensureDefaults();
        Security resolvedSecurity = security != null ? security : new Security(new ArrayList<String>());
        Credentials resolvedCredentials = credentials != null ? credentials : new Credentials(false, "");
        Logging resolvedLogging = logging != null ? logging : new Logging("INFO");
        return new AppConfig(resolvedRemote, resolvedLocal, resolvedLan, resolvedSecurity, resolvedCredentials, resolvedLogging);
    }

    public Remote remote() {
        return remote;
    }

    public Local local() {
        return local;
    }

    public Lan lan() {
        return lan;
    }

    public Security security() {
        return security;
    }

    public Credentials credentials() {
        return credentials;
    }

    public Logging logging() {
        return logging;
    }

    public static final class Remote {
        private String host;
        private int port;

        public Remote() {
        }

        public Remote(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public String host() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int port() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    public static final class Local {
        private int listenPort;

        public Local() {
        }

        public Local(int listenPort) {
            this.listenPort = listenPort;
        }

        public int listenPort() {
            return listenPort;
        }

        public void setListenPort(int listenPort) {
            this.listenPort = listenPort;
        }
    }

    public static final class Lan {
        private String motd;
        private String version;
        private int maxPlayers;
        private Long announceIntervalMs;
        private int broadcastPort;
        private String broadcastAddress;

        public Lan() {
        }

        public Lan(String motd,
                   String version,
                   int maxPlayers,
                   Long announceIntervalMs,
                   int broadcastPort,
                   String broadcastAddress) {
            this.motd = motd;
            this.version = version;
            this.maxPlayers = maxPlayers;
            this.announceIntervalMs = announceIntervalMs;
            this.broadcastPort = broadcastPort;
            this.broadcastAddress = broadcastAddress;
            ensureDefaults();
        }

        public String motd() {
            return motd;
        }

        public void setMotd(String motd) {
            this.motd = motd;
        }

        public String version() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public int maxPlayers() {
            return maxPlayers;
        }

        public void setMaxPlayers(int maxPlayers) {
            this.maxPlayers = maxPlayers;
        }

        public Long announceIntervalMs() {
            return announceIntervalMs;
        }

        public void setAnnounceIntervalMs(Long announceIntervalMs) {
            this.announceIntervalMs = announceIntervalMs;
        }

        public int broadcastPort() {
            return broadcastPort;
        }

        public void setBroadcastPort(int broadcastPort) {
            this.broadcastPort = broadcastPort;
        }

        public String broadcastAddress() {
            return broadcastAddress;
        }

        public void setBroadcastAddress(String broadcastAddress) {
            this.broadcastAddress = broadcastAddress;
        }

        public long announceIntervalMillis() {
            if (announceIntervalMs == null || announceIntervalMs <= 0) {
                return 1000L;
            }
            return announceIntervalMs;
        }

        public void ensureDefaults() {
            if (announceIntervalMs == null || announceIntervalMs <= 0) {
                announceIntervalMs = 1000L;
            }
            if (broadcastAddress == null || broadcastAddress.isBlank()) {
                broadcastAddress = "255.255.255.255";
            }
        }

        private void applyDefaults() {
            ensureDefaults();
        }
    }

    public static final class Security {
        private List<String> whitelist;

        public Security() {
        }

        public Security(List<String> whitelist) {
            this.whitelist = whitelist;
        }

        public List<String> whitelist() {
            return whitelist != null ? whitelist : Collections.<String>emptyList();
        }

        public void setWhitelist(List<String> whitelist) {
            this.whitelist = whitelist;
        }
    }

    public static final class Credentials {
        private boolean enabled;
        private String token;

        public Credentials() {
        }

        public Credentials(boolean enabled, String token) {
            this.enabled = enabled;
            this.token = token;
        }

        public boolean enabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String token() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }

    public static final class Logging {
        private String level;

        public Logging() {
        }

        public Logging(String level) {
            this.level = level;
        }

        public String level() {
            return level != null ? level : "INFO";
        }

        public void setLevel(String level) {
            this.level = level;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return String.valueOf(value);
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static Long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean(((String) value).trim());
        }
        return null;
    }

    private static List<String> asStringList(Object value) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                String str = asString(item);
                if (str != null && !str.trim().isEmpty()) {
                    result.add(str.trim());
                }
            }
            return result;
        }
        if (value instanceof String) {
            String str = ((String) value).trim();
            if (!str.isEmpty()) {
                List<String> single = new ArrayList<>();
                single.add(str);
                return single;
            }
        }
        return null;
    }
}

