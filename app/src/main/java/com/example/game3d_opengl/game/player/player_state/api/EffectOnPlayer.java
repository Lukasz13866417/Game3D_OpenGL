package com.example.game3d_opengl.game.player.player_state.api;

/**
 * Abstraction for timed/perma buffs/debuffs and other factors affecting the Player at a given time.
 * It affects the player by producing Info objects that are then processed by InfoVisitors.
 * Still a work in progress - needs generics for the visitor type and info type.
 */
public abstract class EffectOnPlayer<InfoHandler> {
    public abstract void infosOnStart(InfoHandler infoAPI);
    public abstract void infosOnExpire(InfoHandler infoAPI);
    public abstract void infosWhileActive(InfoHandler infoAPI);
    public abstract boolean hasExpired();
}
