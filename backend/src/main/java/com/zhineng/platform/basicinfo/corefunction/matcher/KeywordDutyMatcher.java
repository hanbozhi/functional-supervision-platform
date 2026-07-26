package com.zhineng.platform.basicinfo.corefunction.matcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class KeywordDutyMatcher {
    private static final Set<String> STOP_WORDS = Set.of(
            "负责", "承担", "开展", "组织", "实施", "推进", "管理", "工作",
            "相关", "本部门", "全市", "依法", "以及", "有关", "等", "职责");

    public List<String> splitResponsibilities(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.replaceAll("(?m)^\\s*[一二三四五六七八九十]+、", "")
                .replaceAll("(?m)^\\s*[（(][一二三四五六七八九十]+[）)]", "")
                .replaceAll("(?m)^\\s*\\d+[.、]", "");
        LinkedHashSet<String> items = new LinkedHashSet<>();
        for (String part : normalized.split("[\\r\\n；;。]+")) {
            String value = part.trim().replaceAll("^[-—•·]+", "").trim();
            if (value.length() >= 2) {
                items.add(value);
            }
        }
        return new ArrayList<>(items);
    }

    public List<String> generateKeywords(String dutyContent) {
        if (dutyContent == null || dutyContent.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String part : dutyContent.split("[、，,；;。及和与/\\s]+")) {
            String word = normalize(part);
            for (String stop : STOP_WORDS) {
                word = word.replace(stop, "");
            }
            word = word.trim();
            if (word.length() >= 2 && !STOP_WORDS.contains(word)) {
                result.add(word);
            }
        }
        return new ArrayList<>(result);
    }

    public List<String> parseKeywords(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> words = new LinkedHashSet<>();
        Arrays.stream(value.split("[,，、;；\\s]+"))
                .map(this::normalize)
                .filter(word -> word.length() >= 2)
                .filter(word -> !STOP_WORDS.contains(word))
                .forEach(words::add);
        return new ArrayList<>(words);
    }

    public Match score(List<String> keywords, String rightsText) {
        if (keywords == null || keywords.isEmpty()) {
            return new Match(0, List.of());
        }
        String target = normalize(rightsText);
        List<String> matched = keywords.stream().filter(target::contains).toList();
        double score = Math.round(matched.size() * 10000.0 / keywords.size()) / 100.0;
        return new Match(score, matched);
    }

    public String normalizeDepartment(String value) {
        String normalized = normalize(value)
                .replace("（", "").replace("）", "")
                .replace("(", "").replace(")", "");
        if (normalized.startsWith("克拉玛依市")) {
            normalized = normalized.substring("克拉玛依市".length());
        } else if (normalized.startsWith("市")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]+", "");
    }

    public record Match(double score, List<String> matchedKeywords) {
    }
}
