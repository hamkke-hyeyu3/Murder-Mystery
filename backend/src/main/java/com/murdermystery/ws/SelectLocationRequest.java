package com.murdermystery.ws;

public record SelectLocationRequest(String locationId, int roundNumber, int turnIndex) {}
