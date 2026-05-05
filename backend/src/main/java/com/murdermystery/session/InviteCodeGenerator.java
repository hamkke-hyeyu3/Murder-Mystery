package com.murdermystery.session;

@FunctionalInterface
public interface InviteCodeGenerator {
    String next();
}
