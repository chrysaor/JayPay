package com.jaypay.remittance.domain;

import java.util.regex.Pattern;

public record RequestId(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    public RequestId {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("A request id is 1 to 64 letters, digits, '-' or '_'");
        }
    }
}
