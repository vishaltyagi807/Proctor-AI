package dev.varshit.proctor.monitor.dto;

import java.util.List;

public record SystemOverview(int intervalSeconds, SystemSnapshot latest, List<HistoryPoint> history) {
}
