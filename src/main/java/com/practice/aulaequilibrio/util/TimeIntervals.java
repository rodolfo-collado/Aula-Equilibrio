package com.practice.aulaequilibrio.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Comparator;
import java.util.List;

public final class TimeIntervals {
    public static final LocalTime OPENING = LocalTime.of(7, 0);
    public static final LocalTime CLOSING = LocalTime.of(21, 0);
    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);
    private TimeIntervals() {}
    public record Range(LocalTime start, LocalTime end) {}
    public static LocalTime parse(String text) { return LocalTime.parse(text.strip(), FORMAT); }
    public static void validate(LocalTime start, LocalTime end) {
        if (start == null || end == null || !start.isBefore(end)) throw new IllegalArgumentException("El inicio debe ser anterior al fin, dentro del mismo día.");
        if (start.isBefore(OPENING) || end.isAfter(CLOSING))
            throw new IllegalArgumentException("La universidad está abierta de 07:00 a 21:00. El intervalo debe quedar dentro de ese rango.");
        if (start.getSecond() != 0 || end.getSecond() != 0 || start.getNano() != 0 || end.getNano() != 0)
            throw new IllegalArgumentException("Usa horas y minutos en formato HH:mm.");
    }
    public static boolean overlaps(LocalTime start, LocalTime end, LocalTime otherStart, LocalTime otherEnd) {
        return start != null && end != null && otherStart != null && otherEnd != null
                && start.isBefore(otherEnd) && end.isAfter(otherStart);
    }
    public static boolean covered(List<Range> intervals, LocalTime start, LocalTime end) {
        if (start == null || end == null || !start.isBefore(end)) return false;
        LocalTime cursor = start;
        for (Range interval : intervals.stream().filter(r -> r.start() != null && r.end() != null)
                .sorted(Comparator.comparing(Range::start)).toList()) {
            if (!interval.end().isAfter(cursor)) continue;
            if (interval.start().isAfter(cursor)) return false;
            cursor = interval.end();
            if (!cursor.isBefore(end)) return true;
        }
        return false;
    }
}
