package io.dhia.matcher;

import io.dhia.matcher.Model.Consultant;
import io.dhia.matcher.Model.Mission;

import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/** Dependency-free test runner: java -cp build/classes io.dhia.matcher.MatcherTest */
public final class MatcherTest {
    private static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  ok   " + name); } else { failed++; System.out.println("  FAIL " + name); }
    }

    public static void main(String[] args) throws Exception {
        // JSON
        Object j = Json.parse("{\"a\": [1, 2.5, \"x\\n\\u00e9\", true, null], \"b\": {}}");
        check("json parse", j instanceof Map<?, ?> m && ((List<?>) m.get("a")).size() == 5);
        check("json roundtrip", Json.write(Json.parse(Json.write(j))).equals(Json.write(j)));
        boolean threw = false; try { Json.parse("{\"a\":}"); } catch (IllegalArgumentException e) { threw = true; }
        check("json rejects invalid input", threw);

        // Text
        check("accents and stop words", TextIndex.tokens("Développeur et Intégration").equals(List.of("developpeur", "integration")));
        check("skill synonyms", TextIndex.skill("Spring Boot").equals("spring") && TextIndex.skill("ReactJS").equals("react"));

        // Matching
        LocalDate d = LocalDate.of(2026, 12, 1);
        List<Consultant> cs = List.of(
            new Consultant("1", "Java Dev", "Développeur Java", List.of("Java", "Spring Boot", "Kafka"), 5, "Nice", true, d, "API Spring pour la banque"),
            new Consultant("2", "Data Sci", "Data Scientist", List.of("Python", "Machine Learning"), 4, "Nice", true, d, "Modèles de fraude"),
            new Consultant("3", "Late Java", "Développeur Java", List.of("Java", "Spring"), 5, "Lille", false, d.plusDays(90), "Applications Java"));
        Matcher matcher = new Matcher(cs);
        Mission javaMission = new Mission("m", "Banque", "Développeur Java Spring", "API Spring Boot et Kafka", List.of("Java", "Spring Boot", "Kafka"), "Nice", false, d, 3, 8);
        List<Matcher.Match> r = matcher.rank(javaMission, 3);
        check("best skills match ranks first", r.get(0).consultant().id().equals("1"));
        check("unavailable & far consultant ranks below", r.get(1).consultant().id().equals("3") && r.get(1).score() < r.get(0).score());
        check("missing skills reported", r.get(2).missingSkills().size() == 3);
        check("score within 0..100", r.stream().allMatch(x -> x.score() >= 0 && x.score() <= 100));
        Mission mlMission = new Mission("m2", "", "Data Scientist", "machine learning fraude", List.of("Python", "ML"), "Nice", true, d, 2, 6);
        check("synonym ML matches Machine Learning", matcher.rank(mlMission, 1).get(0).consultant().id().equals("2"));

        // HTTP API with the sample data
        App app = App.fromFiles(Path.of("data/consultants.json"), Path.of("data/missions.json"));
        var server = app.start(0); int port = server.getAddress().getPort();
        HttpClient http = HttpClient.newHttpClient();
        try {
            var res = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/missions/m01/matches?top=3")).build(), HttpResponse.BodyHandlers.ofString());
            @SuppressWarnings("unchecked") Map<String, Object> body = (Map<String, Object>) Json.parse(res.body());
            check("GET matches 200 with 3 results", res.statusCode() == 200 && ((List<?>) body.get("matches")).size() == 3);
            var notFound = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/missions/zzz/matches")).build(), HttpResponse.BodyHandlers.ofString());
            check("unknown mission 404", notFound.statusCode() == 404);
            var bad = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/match")).POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"x\"}")).build(), HttpResponse.BodyHandlers.ofString());
            check("invalid mission 400", bad.statusCode() == 400);
            var ok = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/match?top=2")).POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"Dev React\",\"description\":\"front React et API Node\",\"skills\":[\"React\",\"Node.js\"],\"city\":\"Nice\"}")).build(), HttpResponse.BodyHandlers.ofString());
            check("POST match returns ranked list", ok.statusCode() == 200 && ok.body().contains("Mehdi Ziani"));
            var ui = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).build(), HttpResponse.BodyHandlers.ofString());
            check("serves UI", ui.statusCode() == 200 && ui.body().contains("Mission Matcher"));
        } finally { server.stop(0); }

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        System.exit(failed > 0 ? 1 : 0); // server thread pool is non-daemon
    }
}
