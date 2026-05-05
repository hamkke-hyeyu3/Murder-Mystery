package com.murdermystery.session;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SecureRandomInviteCodeGenerator implements InviteCodeGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String next() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
