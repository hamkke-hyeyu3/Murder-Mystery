package com.murdermystery.session;

public class SurveyPhaseRequiredException extends RuntimeException {
    public SurveyPhaseRequiredException() {
        super("Session is not in survey phase");
    }
}
