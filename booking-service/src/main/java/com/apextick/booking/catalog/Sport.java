package com.apextick.booking.catalog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Sport of a series/event/team. Stored as the enum name; serialized as the lowercase code. */
public enum Sport {
    CRICKET("cricket"),
    FOOTBALL("football");

    private final String code;

    Sport(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static Sport fromCode(String value) {
        for (Sport s : values()) {
            if (s.code.equalsIgnoreCase(value) || s.name().equalsIgnoreCase(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown sport: " + value);
    }
}
