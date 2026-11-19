package io.dhia.matcher;

import io.dhia.matcher.Model.Consultant;
import io.dhia.matcher.Model.Mission;

import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Ranks consultants for a mission with an explainable score (0–100):
 *   45% required skills covered · 25% text similarity (TF-IDF) · 15% seniority fit
 *   10% location / remote · 5% availability
 */
public final class Matcher {
    public record Match(Consultant consultant, double score, Map<String, Double> breakdown,
                        List<String> matchedSkills, List<String> missingSkills, List<String> reasons) {
        Map<String, Object> toJson() {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("consultant", consultant.toJson()); o.put("score", Math.round(score * 10) / 10.0);
            Map<String, Object> b = new LinkedHashMap<>(); breakdown.forEach((k, v) -> b.put(k, Math.round(v * 1000) / 1000.0));
            o.put("breakdown", b); o.put("matchedSkills", matchedSkills); o.put("missingSkills", missingSkills); o.put("reasons", reasons);
            return o;
        }
    }

    private final List<Consultant> consultants;
    private final TextIndex index = new TextIndex();

    public Matcher(List<Consultant> consultants) {
        this.consultants = List.copyOf(consultants);
        consultants.forEach(c -> index.add(profileText(c)));
    }

    static String profileText(Consultant c) { return c.title() + " " + String.join(" ", c.skills()) + " " + c.summary(); }

    public List<Match> rank(Mission m, int top) {
        Map<String, Double> mv = index.vector(m.title() + " " + m.description() + " " + String.join(" ", m.skills()));
        List<Match> out = new ArrayList<>();
        for (Consultant c : consultants) out.add(score(m, mv, c));
        out.sort(Comparator.comparingDouble(Match::score).reversed());
        return out.subList(0, Math.min(top, out.size()));
    }

    Match score(Mission m, Map<String, Double> missionVec, Consultant c) {
        Set<String> have = new HashSet<>(); c.skills().forEach(s -> have.add(TextIndex.skill(s)));
        Set<String> profileTokens = new HashSet<>(TextIndex.tokens(profileText(c)));
        List<String> matched = new ArrayList<>(), missing = new ArrayList<>();
        for (String s : m.skills()) {
            String k = TextIndex.skill(s);
            if (have.contains(k) || profileTokens.contains(k)) matched.add(s); else missing.add(s);
        }
        double skills = m.skills().isEmpty() ? 0.5 : (double) matched.size() / m.skills().size();
        double text = TextIndex.cosine(missionVec, index.vector(profileText(c)));

        double seniority;
        if (c.years() < m.minYears()) seniority = Math.max(0, 1 - (m.minYears() - c.years()) * 0.35);
        else if (c.years() > m.maxYears()) seniority = Math.max(0.4, 1 - (c.years() - m.maxYears()) * 0.1);
        else seniority = 1;

        boolean sameCity = !m.city().isBlank() && TextIndex.norm(m.city()).equals(TextIndex.norm(c.city()));
        double location = sameCity ? 1 : (m.remoteOk() && c.remote()) ? 0.8 : m.remoteOk() ? 0.5 : 0.1;

        long lateDays = Math.max(0, ChronoUnit.DAYS.between(m.start(), c.availableFrom()));
        double availability = lateDays == 0 ? 1 : Math.max(0, 1 - lateDays / 60.0);

        Map<String, Double> b = new LinkedHashMap<>();
        b.put("skills", skills); b.put("text", text); b.put("seniority", seniority); b.put("location", location); b.put("availability", availability);
        double score = 100 * (0.45 * skills + 0.25 * Math.min(1, text * 1.6) + 0.15 * seniority + 0.10 * location + 0.05 * availability);

        List<String> reasons = new ArrayList<>();
        if (!m.skills().isEmpty()) reasons.add(matched.size() + "/" + m.skills().size() + " compétences requises" + (missing.isEmpty() ? "" : ", manquantes : " + String.join(", ", missing)));
        reasons.add(c.years() + " ans d'expérience" + (seniority < 1 ? (c.years() < m.minYears() ? " (minimum demandé : " + m.minYears() + ")" : " (plus senior que nécessaire)") : " (adapté)"));
        reasons.add(sameCity ? "Basé(e) à " + c.city() : (m.remoteOk() && c.remote() ? "Télétravail possible" : "Basé(e) à " + c.city()));
        reasons.add(lateDays == 0 ? "Disponible à la date de démarrage" : "Disponible " + lateDays + " jours après le démarrage");
        return new Match(c, score, b, matched, missing, reasons);
    }
}
