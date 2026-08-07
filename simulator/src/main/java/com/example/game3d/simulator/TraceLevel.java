package com.example.game3d.simulator;

enum TraceLevel {
    SUMMARY,
    CONTACTS,
    FULL;

    static TraceLevel parse(String value) {
        return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
