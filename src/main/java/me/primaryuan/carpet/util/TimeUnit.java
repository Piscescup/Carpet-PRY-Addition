package me.primaryuan.carpet.util;

import me.primaryuan.carpet.i18n.ServerI18n;

import java.util.Arrays;
import java.util.Optional;

/**
 * Command tokens, conversion and display for a packet's duration amount/unit pair.
 */
public enum TimeUnit {
    MIN("min", 60_000),
    S("s", 1_000),
    H("h", 3_600_000),
    TICKS("ticks", 50);

    public static final long DEFAULT_AMOUNT = 3;
    public static final TimeUnit DEFAULT = MIN;
    public static final long MAX_MILLIS = Long.MAX_VALUE / 2;
    private static final String PREFIX = "carpetprimaryuan.redpacket.duration.";

    private final String token;
    private final long millisPerUnit;

    TimeUnit(String token, long millisPerUnit) {
        this.token = token;
        this.millisPerUnit = millisPerUnit;
    }

    public String token() {
        return token;
    }

    public long toMillis(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Packet duration must be positive");
        try {
            long millis = Math.multiplyExact(amount, millisPerUnit);
            if (millis > MAX_MILLIS) throw new IllegalArgumentException("Packet duration is too large");
            return millis;
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Packet duration is too large", overflow);
        }
    }

    public String format(long amount) {
        toMillis(amount);
        return ServerI18n.tr(PREFIX + token, amount).getString();
    }

    public static Optional<TimeUnit> fromToken(String token) {
        return Arrays.stream(values()).filter(unit -> unit.token.equalsIgnoreCase(token)).findFirst();
    }
}
