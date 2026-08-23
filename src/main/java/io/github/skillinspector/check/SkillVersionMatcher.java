package io.github.skillinspector.check;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SkillVersionMatcher {
    public enum Result { MATCH, MISMATCH, UNKNOWN }
    private static final Pattern CONSTRAINT = Pattern.compile("^(>=|<=|>|<|=)?([0-9]+(?:\\.[0-9]+){0,2})$");
    private static final Pattern WILDCARD = Pattern.compile("^([0-9]+)\\.(?:x|\\*)$");
    private static final Pattern VERSION = Pattern.compile("^[0-9]+(?:\\.[0-9]+){0,2}$");

    public Result matches(String actual, String required) {
        String constraint = required == null || required.isBlank() ? "*" : required.strip();
        if ("*".equals(constraint)) return Result.MATCH;
        if (actual == null || actual.isBlank() || !VERSION.matcher(actual.strip()).matches()) return Result.UNKNOWN;
        Matcher wildcard = WILDCARD.matcher(constraint);
        if (wildcard.matches()) return parts(actual).getFirst().equals(new BigInteger(wildcard.group(1)))
                ? Result.MATCH : Result.MISMATCH;
        Matcher matcher = CONSTRAINT.matcher(constraint);
        if (!matcher.matches()) return Result.UNKNOWN;
        int comparison = compare(parts(actual), parts(matcher.group(2)));
        String operator = matcher.group(1) == null ? "=" : matcher.group(1);
        boolean match = switch (operator) {
            case "=" -> comparison == 0;
            case ">" -> comparison > 0;
            case ">=" -> comparison >= 0;
            case "<" -> comparison < 0;
            case "<=" -> comparison <= 0;
            default -> false;
        };
        return match ? Result.MATCH : Result.MISMATCH;
    }

    private List<BigInteger> parts(String value) {
        List<BigInteger> result = new ArrayList<>();
        for (String part : value.strip().split("\\.")) result.add(new BigInteger(part));
        while (result.size() < 3) result.add(BigInteger.ZERO);
        return result;
    }

    private int compare(List<BigInteger> left, List<BigInteger> right) {
        for (int i = 0; i < 3; i++) {
            int result = left.get(i).compareTo(right.get(i));
            if (result != 0) return result;
        }
        return 0;
    }
}
