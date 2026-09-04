package com.example.game3d.terrain.editor.persistence;

/** Explicit authority supplied for a conditional editor save. */
public enum SaveIntent {
    CREATE_NEW,
    SAVE_IF_UNCHANGED,
    OVERWRITE_CONFIRMED
}
