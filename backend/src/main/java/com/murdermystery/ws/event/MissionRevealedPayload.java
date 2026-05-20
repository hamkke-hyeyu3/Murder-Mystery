package com.murdermystery.ws.event;

import com.murdermystery.scenario.ScenarioCharacter.Mission;
import java.util.List;

public record MissionRevealedPayload(List<Mission> missions) {}
