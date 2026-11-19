package io.dhia.matcher;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.dhia.matcher.Model.Consultant;
import io.dhia.matcher.Model.Mission;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;

/** REST API + static UI on the JDK's built-in HTTP server. */
public final class App {
    private final List<Consultant> consultants;
    private final List<Mission> missions;
    private final Matcher matcher;

    public App(List<Consultant> consultants, List<Mission> missions) {
        this.consultants = consultants; this.missions = missions; this.matcher = new Matcher(consultants);
    }

    @SuppressWarnings("unchecked")
    public static App fromFiles(Path consultantsFile, Path missionsFile) throws IOException {
        List<Consultant> c = ((List<Object>) Json.parse(Files.readString(consultantsFile))).stream().map(o -> Consultant.from((Map<String, Object>) o)).toList();
        List<Mission> m = ((List<Object>) Json.parse(Files.readString(missionsFile))).stream().map(o -> Mission.from((Map<String, Object>) o)).toList();
        return new App(c, m);
    }

    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", this::api);
        server.createContext("/", this::staticFiles);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        return server;
    }

    @SuppressWarnings("unchecked")
    private void api(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath(); String method = ex.getRequestMethod();
        Map<String, String> q = query(ex.getRequestURI().getRawQuery());
        try {
            if (method.equals("GET") && path.equals("/api/health")) { send(ex, 200, Map.of("status", "ok")); return; }
            if (method.equals("GET") && path.equals("/api/consultants")) { send(ex, 200, consultants.stream().map(Consultant::toJson).toList()); return; }
            if (method.equals("GET") && path.equals("/api/missions")) { send(ex, 200, missions.stream().map(Mission::toJson).toList()); return; }
            if (method.equals("GET") && path.matches("/api/missions/[^/]+/matches")) {
                String id = path.split("/")[3];
                Optional<Mission> m = missions.stream().filter(x -> x.id().equals(id)).findFirst();
                if (m.isEmpty()) { send(ex, 404, Map.of("error", "mission not found: " + id)); return; }
                send(ex, 200, result(m.get(), top(q))); return;
            }
            if (method.equals("POST") && path.equals("/api/match")) {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Object parsed = Json.parse(body);
                if (!(parsed instanceof Map)) { send(ex, 400, Map.of("error", "body must be a JSON object")); return; }
                send(ex, 200, result(Mission.from((Map<String, Object>) parsed), top(q))); return;
            }
            send(ex, 404, Map.of("error", "not found"));
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
            send(ex, 400, Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> result(Mission m, int top) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("mission", m.toJson());
        o.put("matches", matcher.rank(m, top).stream().map(Matcher.Match::toJson).toList());
        return o;
    }

    private static int top(Map<String, String> q) {
        try { return Math.max(1, Math.min(50, Integer.parseInt(q.getOrDefault("top", "5")))); } catch (NumberFormatException e) { return 5; }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) return m;
        for (String p : raw.split("&")) { String[] kv = p.split("=", 2); m.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : ""); }
        return m;
    }

    private void staticFiles(HttpExchange ex) throws IOException {
        String p = ex.getRequestURI().getPath(); if (p.equals("/")) p = "/index.html";
        if (p.contains("..")) { send(ex, 400, Map.of("error", "bad path")); return; }
        try (InputStream in = App.class.getResourceAsStream("/static" + p)) {
            if (in == null) { send(ex, 404, Map.of("error", "not found")); return; }
            byte[] bytes = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", p.endsWith(".html") ? "text/html; charset=utf-8" : p.endsWith(".js") ? "text/javascript" : "text/css");
            ex.sendResponseHeaders(200, bytes.length); ex.getResponseBody().write(bytes); ex.close();
        }
    }

    private static void send(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length); ex.getResponseBody().write(bytes); ex.close();
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        App app = fromFiles(Path.of(args.length > 0 ? args[0] : "data/consultants.json"), Path.of(args.length > 1 ? args[1] : "data/missions.json"));
        app.start(port);
        System.out.println("Mission Matcher running on http://localhost:" + port);
    }
}
