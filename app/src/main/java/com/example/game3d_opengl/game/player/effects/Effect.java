package com.example.game3d_opengl.game.player.effects;

import com.example.game3d_opengl.game.player.Player;

/**
 * Abstraction for timed/perma buffs/debuffs and other factors affecting the Player at a given time
 */
public abstract class Effect {

    // These fields are used to group effects together and order by importance.
    private final EffectCategory category;

    // Effect with lowest value out of its category will be executed first in its category.
    private final int orderWithinCategory;

    public Effect(EffectCategory category, int orderWithinCategory){
        this.category = category;
        this.orderWithinCategory = orderWithinCategory;
    }

    public abstract void onStart(Player.PlayerEffectsAPI playerEffectsAPI);
    public abstract void onExpire(Player.PlayerEffectsAPI playerEffectsAPI);
    public abstract void applyWhileActive(Player.PlayerEffectsAPI playerEffectsAPI);
    public abstract boolean hasExpired();



}
