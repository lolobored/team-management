package org.lolobored.tm.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    @Test
    void validPassword_hasNoFailures() {
        // 12 chars, upper+lower+digit (3 classes)
        assertThat(PasswordPolicy.validate("Abcdefgh1234")).isEmpty();
        assertThat(PasswordPolicy.isValid("Abcdefgh1234")).isTrue();
    }

    @Test
    void tooShort_failsLength() {
        assertThat(PasswordPolicy.validate("Abc123!")).contains("length");
    }

    @Test
    void tooFewClasses_failsClasses() {
        // 12 chars but only lowercase (1 class)
        assertThat(PasswordPolicy.validate("abcdefghijkl")).contains("classes");
    }

    @Test
    void symbolCountsAsClass() {
        // 12 chars, lower + symbol + digit = 3 classes
        assertThat(PasswordPolicy.validate("abcdefgh12!@")).isEmpty();
    }

    @Test
    void nullTreatedAsEmpty() {
        assertThat(PasswordPolicy.validate(null)).contains("length", "classes");
    }
}
