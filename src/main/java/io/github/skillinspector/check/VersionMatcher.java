package io.github.skillinspector.check;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionMatcher {
    private static final Pattern CONSTRAINT = Pattern.compile("^(>=|<=|>|<|=)?\\s*[vV]?([0-9]+(?:\\.[0-9]+)*(?:\\.[xX*])?)$");

    public boolean matches(String required, String actual) {
        if (required == null || required.isBlank() || required.equals("*")) return true;
        Matcher matcher = CONSTRAINT.matcher(required.trim());
        if (!matcher.matches()) throw new IllegalArgumentException("Unsupported version constraint: " + required);
        String operator = matcher.group(1) == null ? "=" : matcher.group(1);
        String expectedText = matcher.group(2);
        boolean wildcard = expectedText.matches(".*\\.[xX*]$");
        if (wildcard) {
            String prefix = expectedText.substring(0, expectedText.lastIndexOf('.'));
            return normalize(actual).equals(prefix) || normalize(actual).startsWith(prefix + ".");
        }
        int comparison = compare(parse(expectedText), parse(normalize(actual)));
        return switch (operator) { case ">" -> comparison < 0; case ">=" -> comparison <= 0; case "<" -> comparison > 0; case "<=" -> comparison >= 0; default -> comparison == 0; };
    }

    public String normalize(String text) {
        Matcher matcher = Pattern.compile("(?<![A-Za-z])([0-9]+(?:\\.[0-9]+){0,3})(?:[-+][A-Za-z0-9._-]+)?").matcher(text == null ? "" : text);
        if (!matcher.find()) throw new IllegalArgumentException("No version found in: " + text);
        return matcher.group(1);
    }

    private List<Integer> parse(String version) {
        List<Integer> parts = new ArrayList<>();
        for (String part : version.split("\\.")) parts.add(Integer.parseInt(part));
        return parts;
    }

    private int compare(List<Integer> left, List<Integer> right) {
        int length = Math.max(left.size(), right.size());
        for (int i = 0; i < length; i++) {
            int cmp = Integer.compare(i < left.size() ? left.get(i) : 0, i < right.size() ? right.get(i) : 0);
            if (cmp != 0) return cmp;
        }
        return 0;
    }
}
