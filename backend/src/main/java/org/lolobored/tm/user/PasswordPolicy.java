package org.lolobored.tm.user;

import java.util.ArrayList;
import java.util.List;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;

    private PasswordPolicy() {}

    /** Returns the list of failed rule keys ("length", "classes"); empty means valid. */
    public static List<String> validate(String pw) {
        String p = pw == null ? "" : pw;
        List<String> failures = new ArrayList<>();
        if (p.length() < MIN_LENGTH) {
            failures.add("length");
        }
        int classes = 0;
        if (p.chars().anyMatch(Character::isUpperCase)) classes++;
        if (p.chars().anyMatch(Character::isLowerCase)) classes++;
        if (p.chars().anyMatch(Character::isDigit)) classes++;
        if (p.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) classes++;
        if (classes < 3) {
            failures.add("classes");
        }
        return failures;
    }

    public static boolean isValid(String pw) {
        return validate(pw).isEmpty();
    }
}
