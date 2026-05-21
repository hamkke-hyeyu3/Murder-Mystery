package com.murdermystery.session;

public class MissionPhaseRequiredException extends RuntimeException {
    public MissionPhaseRequiredException() {
        super("mission phase required");
    }
}
