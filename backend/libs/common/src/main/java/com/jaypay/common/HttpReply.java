package com.jaypay.common;

public record HttpReply<T>(int status, T body) {

    public boolean isSuccessful() {
        return status >= 200 && status < 300;
    }
}
