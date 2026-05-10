package com.murdermystery.session;

public class TutorialPhaseRequiredException extends RuntimeException {
    public TutorialPhaseRequiredException() {
        super("tutorial phase required");
    }
}
