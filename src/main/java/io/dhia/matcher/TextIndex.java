package io.dhia.matcher;

import java.text.Normalizer;
import java.util.*;

/** Tokenisation (FR/EN), skill normalisation and TF-IDF cosine similarity. */
public final class TextIndex {
    private static final Set<String> STOP = Set.of(
        "le","la","les","un","une","des","de","du","et","ou","en","au","aux","pour","par","sur","avec","dans","que","qui","est","sont",
        "the","a","an","and","or","of","to","in","on","for","with","is","are","be","as","at","by","we","our","you","your","nous","vous","sera","son","ses","ce","cette");

    /** Synonyms → canonical skill name. */
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
        Map.entry("js", "javascript"), Map.entry("ecmascript", "javascript"), Map.entry("ts", "typescript"),
        Map.entry("reactjs", "react"), Map.entry("react.js", "react"), Map.entry("nodejs", "node"), Map.entry("node.js", "node"),
        Map.entry("spring boot", "spring"), Map.entry("springboot", "spring"), Map.entry("spring-boot", "spring"),
        Map.entry("angularjs", "angular"), Map.entry("vuejs", "vue"), Map.entry("vue.js", "vue"),
        Map.entry("postgres", "postgresql"), Map.entry("mongo", "mongodb"), Map.entry("k8s", "kubernetes"),
        Map.entry("ml", "machine learning"), Map.entry("apprentissage automatique", "machine learning"),
        Map.entry("ia", "ai"), Map.entry("intelligence artificielle", "ai"), Map.entry("llms", "llm"),
        Map.entry("ci/cd", "cicd"), Map.entry("ci-cd", "cicd"), Map.entry("aws cloud", "aws"), Map.entry("gcp", "google cloud"));

    public static String norm(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.trim();
    }

    public static String skill(String s) { String n = norm(s); return SYNONYMS.getOrDefault(n, n); }

    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        for (String t : norm(text).split("[^a-z0-9+#.]+")) {
            t = t.replaceAll("^\\.+|\\.+$", "");
            if (t.length() < 2 || STOP.contains(t)) continue;
            out.add(SYNONYMS.getOrDefault(t, t));
        }
        return out;
    }

    private final Map<String, Integer> df = new HashMap<>();
    private int docs = 0;

    public void add(String text) { docs++; new HashSet<>(tokens(text)).forEach(t -> df.merge(t, 1, Integer::sum)); }

    public Map<String, Double> vector(String text) {
        Map<String, Integer> tf = new HashMap<>();
        tokens(text).forEach(t -> tf.merge(t, 1, Integer::sum));
        Map<String, Double> v = new HashMap<>();
        tf.forEach((t, c) -> v.put(t, (1 + Math.log(c)) * Math.log(1.0 + (docs + 1.0) / (df.getOrDefault(t, 0) + 1.0))));
        return v;
    }

    public static double cosine(Map<String, Double> a, Map<String, Double> b) {
        double dot = 0, na = 0, nb = 0;
        for (var e : a.entrySet()) { dot += e.getValue() * b.getOrDefault(e.getKey(), 0.0); na += e.getValue() * e.getValue(); }
        for (double x : b.values()) nb += x * x;
        return (na == 0 || nb == 0) ? 0 : dot / Math.sqrt(na * nb);
    }
}
