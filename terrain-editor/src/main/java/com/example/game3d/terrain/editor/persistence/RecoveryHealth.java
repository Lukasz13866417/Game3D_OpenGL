package com.example.game3d.terrain.editor.persistence;

/** Observable durability state of one tab's crash-recovery record. */
public enum RecoveryHealth {
    NOT_NEEDED,
    PENDING,
    SAVED,
    FAILED
}
