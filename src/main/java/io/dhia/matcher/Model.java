package io.dhia.matcher;

import java.time.LocalDate;
import java.util.*;

/** Domain records and their JSON mapping. */
public final class Model {
    private Model() {}

    public record Consultant(String id, String name, String title, List<String> skills, int years,
                             String city, boolean remote, LocalDate availableFrom, String summary) {
        @SuppressWarnings("unchecked")
        static Consultant from(Map<String, Object> m) {
            return new Consultant(str(m, "id"), str(m, "name"), str(m, "title"), (List<String>) (List<?>) m.getOrDefault("skills", List.of()),
                    num(m, "years"), str(m, "city"), Boolean.TRUE.equals(m.get("remote")),
                    LocalDate.parse(str(m, "availableFrom")), str(m, "summary"));
        }
        Map<String, Object> toJson() {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", id); o.put("name", name); o.put("title", title); o.put("skills", skills); o.put("years", years);
            o.put("city", city); o.put("remote", remote); o.put("availableFrom", availableFrom.toString()); o.put("summary", summary);
            return o;
        }
    }

    public record Mission(String id, String client, String title, String description, List<String> skills,
                          String city, boolean remoteOk, LocalDate start, int minYears, int maxYears) {
        @SuppressWarnings("unchecked")
        static Mission from(Map<String, Object> m) {
            if (m.get("title") == null || m.get("description") == null) throw new IllegalArgumentException("title and description are required");
            return new Mission(Objects.toString(m.getOrDefault("id", "adhoc")), Objects.toString(m.getOrDefault("client", "")),
                    str(m, "title"), str(m, "description"), (List<String>) (List<?>) m.getOrDefault("skills", List.of()),
                    Objects.toString(m.getOrDefault("city", "")), Boolean.TRUE.equals(m.get("remoteOk")),
                    m.get("start") == null ? LocalDate.now() : LocalDate.parse(str(m, "start")),
                    m.get("minYears") == null ? 0 : num(m, "minYears"), m.get("maxYears") == null ? 50 : num(m, "maxYears"));
        }
        Map<String, Object> toJson() {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", id); o.put("client", client); o.put("title", title); o.put("description", description); o.put("skills", skills);
            o.put("city", city); o.put("remoteOk", remoteOk); o.put("start", start.toString()); o.put("minYears", minYears); o.put("maxYears", maxYears);
            return o;
        }
    }

    static String str(Map<String, Object> m, String k) {
        Object v = m.get(k); if (v == null) throw new IllegalArgumentException("missing field: " + k); return v.toString();
    }
    static int num(Map<String, Object> m, String k) {
        Object v = m.get(k); if (!(v instanceof Number n)) throw new IllegalArgumentException("field must be a number: " + k); return n.intValue();
    }
}
