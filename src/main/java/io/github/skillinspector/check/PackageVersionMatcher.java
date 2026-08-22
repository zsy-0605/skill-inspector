package io.github.skillinspector.check;

import io.github.skillinspector.model.PackageEcosystem;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PackageVersionMatcher {
    private static final Pattern VERSION = Pattern.compile("^[vV]?([0-9]+(?:\\.[0-9]+){0,5})$");
    private static final Pattern COMPARISON = Pattern.compile("^(==|!=|>=|<=|>|<|=)?\\s*[vV]?([0-9]+(?:\\.[0-9]+)*(?:\\.[xX*])?)$");

    public boolean matches(PackageEcosystem ecosystem, String required, String actual) {
        if (required == null || required.isBlank() || required.equals("*") || required.equalsIgnoreCase("latest")) return true;
        return switch (ecosystem) {
            case PYTHON -> matchesPython(required, actual);
            case NPM -> matchesNpm(required, actual);
            case MAVEN -> matchesMaven(required, actual);
        };
    }

    private boolean matchesPython(String required, String actual) {
        for (String raw : required.split(",")) {
            String clause = raw.strip();
            if (clause.isEmpty()) continue;
            if (clause.startsWith("~=")) {
                List<Integer> lower = parse(clause.substring(2));
                List<Integer> upper = new ArrayList<>(lower);
                int index = lower.size() <= 2 ? 0 : lower.size() - 2;
                upper.set(index, upper.get(index) + 1);
                for (int i = index + 1; i < upper.size(); i++) upper.set(i, 0);
                if (compare(parse(actual), lower) < 0 || compare(parse(actual), upper) >= 0) return false;
            } else if (!matchesComparison(clause, actual)) return false;
        }
        return true;
    }

    private boolean matchesNpm(String required, String actual) {
        String normalized = required.strip();
        for (String alternative : normalized.split("\\s*\\|\\|\\s*")) {
            if (matchesNpmAlternative(alternative.strip(), actual)) return true;
        }
        return false;
    }

    private boolean matchesNpmAlternative(String required, String actual) {
        Matcher range = Pattern.compile("^([vV]?\\d+(?:\\.\\d+){0,2})\\s+-\\s+([vV]?\\d+(?:\\.\\d+){0,2})$").matcher(required);
        if (range.matches()) return compare(parse(actual), parse(range.group(1))) >= 0 && compare(parse(actual), parse(range.group(2))) <= 0;
        if (required.startsWith("^") || required.startsWith("~")) {
            boolean caret = required.charAt(0) == '^';
            List<Integer> lower = padded(parse(required.substring(1)), 3);
            List<Integer> upper = new ArrayList<>(lower);
            int index;
            if (!caret) index = 1;
            else if (lower.get(0) > 0) index = 0;
            else if (lower.get(1) > 0) index = 1;
            else index = 2;
            upper.set(index, upper.get(index) + 1);
            for (int i = index + 1; i < upper.size(); i++) upper.set(i, 0);
            List<Integer> found = parse(actual);
            return compare(found, lower) >= 0 && compare(found, upper) < 0;
        }
        String[] clauses = required.split("\\s+");
        for (String clause : clauses) if (!clause.isBlank() && !matchesComparison(clause, actual)) return false;
        return true;
    }

    private boolean matchesMaven(String required, String actual) {
        String constraint = required.strip();
        if (!(constraint.startsWith("[") || constraint.startsWith("("))) return matchesComparison(constraint, actual);
        if (!(constraint.endsWith("]") || constraint.endsWith(")")) || constraint.indexOf(',') < 0)
            throw new IllegalArgumentException("Unsupported Maven version constraint: " + required);
        boolean includeLower = constraint.startsWith("[");
        boolean includeUpper = constraint.endsWith("]");
        String[] bounds = constraint.substring(1, constraint.length() - 1).split(",", -1);
        if (bounds.length != 2) throw new IllegalArgumentException("Unsupported Maven version constraint: " + required);
        List<Integer> found = parse(actual);
        if (!bounds[0].isBlank()) {
            int comparison = compare(found, parse(bounds[0]));
            if (comparison < 0 || comparison == 0 && !includeLower) return false;
        }
        if (!bounds[1].isBlank()) {
            int comparison = compare(found, parse(bounds[1]));
            if (comparison > 0 || comparison == 0 && !includeUpper) return false;
        }
        return true;
    }

    private boolean matchesComparison(String required, String actual) {
        Matcher matcher = COMPARISON.matcher(required.strip());
        if (!matcher.matches()) throw new IllegalArgumentException("Unsupported package version constraint: " + required);
        String expected = matcher.group(2);
        if (expected.matches(".*\\.[xX*]$")) {
            String prefix = expected.substring(0, expected.lastIndexOf('.'));
            String found = normalized(actual);
            boolean samePrefix = found.equals(prefix) || found.startsWith(prefix + ".");
            return "!=".equals(matcher.group(1)) ? !samePrefix : samePrefix;
        }
        int comparison = compare(parse(actual), parse(expected));
        String operator = matcher.group(1) == null ? "=" : matcher.group(1);
        return switch (operator) {
            case ">" -> comparison > 0;
            case ">=" -> comparison >= 0;
            case "<" -> comparison < 0;
            case "<=" -> comparison <= 0;
            case "!=" -> comparison != 0;
            default -> comparison == 0;
        };
    }

    private String normalized(String value) {
        Matcher matcher = VERSION.matcher(value == null ? "" : value.strip());
        if (!matcher.matches()) throw new IllegalArgumentException("Unsupported package version: " + value);
        return matcher.group(1);
    }

    private List<Integer> parse(String value) {
        List<Integer> parts = new ArrayList<>();
        for (String part : normalized(value).split("\\.")) parts.add(Integer.parseInt(part));
        return parts;
    }

    private List<Integer> padded(List<Integer> input, int length) {
        List<Integer> result = new ArrayList<>(input);
        while (result.size() < length) result.add(0);
        return result;
    }

    private int compare(List<Integer> left, List<Integer> right) {
        int length = Math.max(left.size(), right.size());
        for (int i = 0; i < length; i++) {
            int value = Integer.compare(i < left.size() ? left.get(i) : 0, i < right.size() ? right.get(i) : 0);
            if (value != 0) return value;
        }
        return 0;
    }
}
