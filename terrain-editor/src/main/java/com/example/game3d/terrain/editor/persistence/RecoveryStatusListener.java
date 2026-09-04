package com.example.game3d.terrain.editor.persistence;

@FunctionalInterface
public interface RecoveryStatusListener {
    void statusChanged(RecoveryStatus status);
}
