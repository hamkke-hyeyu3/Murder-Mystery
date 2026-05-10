package com.murdermystery.session;

public record TutorialAckResponse(int acked, int total, String state) {}
