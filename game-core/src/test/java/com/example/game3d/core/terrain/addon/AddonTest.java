package com.example.game3d.core.terrain.addon;

import com.example.game3d.core.math.Aabb;
import com.example.game3d.core.math.Vec3;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AddonTest {
    @Test
    public void placementSealsIdentityExactlyOnce() {
        Potion potion = new Potion(new Vec3(1.0, 2.0, 3.0), 0.25, "POTION");
        assertFalse(potion.isSealed());

        potion.place(7L, 4L, AddonFootprint.around(
                potion.center, 0.25, 0.25, 0.25));

        assertTrue(potion.isSealed());
        assertEquals(7L, potion.id());
        assertEquals(4L, potion.ownerSegmentId());
        try {
            potion.place(8L, 4L, AddonFootprint.around(
                    potion.center, 0.25, 0.25, 0.25));
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Repeated placement must fail");
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroAddonIdIsRejected() {
        Potion potion = new Potion(Vec3.ZERO, 0.25, "POTION");
        potion.place(0L, 0L, AddonFootprint.around(
                Vec3.ZERO, 0.25, 0.25, 0.25));
    }

    @Test(expected = IllegalArgumentException.class)
    public void degenerateQuadrilateralIsRejected() {
        AddonFootprint.quadrilateral(
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
    }

    @Test
    public void spikeAndPotionOwnTheirContactRules() {
        DeathSpike spike = spike(1L, 0L, Vec3.ZERO, 0.4, 1.0);
        Potion potion = new Potion(new Vec3(4.0, 0.5, 0.0), 0.25, "POTION");
        potion.place(2L, 0L, AddonFootprint.around(
                potion.center, 0.25, 0.25, 0.25));
        RecordingSink sink = new RecordingSink();

        AddonContactContext spikeHit = new AddonContactContext(
                new Vec3(0.0, 0.5, 0.0), new Vec3(1.0, 0.0, 0.0), 0.5, 0.25,
                new Aabb(new Vec3(-0.5, 0.0, -0.5),
                        new Vec3(0.5, 1.0, 0.5)));
        spike.evaluateContact(spikeHit, sink);
        assertEquals(1L, sink.hazardId);

        AddonContactContext pickupHit = new AddonContactContext(
                new Vec3(4.0, 0.5, 0.0), new Vec3(1.0, 0.0, 0.0), 0.5, 0.25,
                Aabb.around(new Vec3(4.0, 0.5, 0.0), 0.5, 0.5, 0.5));
        potion.evaluateContact(pickupHit, sink);
        assertEquals(2L, sink.pickupId);
        assertEquals(1, sink.charges);
    }

    @Test
    public void portalRolesHaveStableTagsAndRemainVisualOnly() {
        Portal entrance = new Portal(
                3L, Portal.Role.ENTRANCE, Vec3.ZERO,
                new Vec3(0.0, 0.0, -1.0), Vec3.UP,
                1.0, 2.0, "BEACON");
        entrance.place(1L, 0L, AddonFootprint.around(
                Vec3.ZERO, 0.5, 1.0, 0.5));

        assertEquals(0, Portal.Role.ENTRANCE.stableCode);
        assertEquals(1, Portal.Role.EXIT.stableCode);
        assertEquals(Addon.ContactPhase.NONE, entrance.contactPhase());
    }

    private static DeathSpike spike(
            long id, long owner, Vec3 center, double radius, double height) {
        Vec3 nearLeft = new Vec3(center.x - radius, center.y, center.z + radius);
        Vec3 nearRight = new Vec3(center.x + radius, center.y, center.z + radius);
        Vec3 farLeft = new Vec3(center.x - radius, center.y, center.z - radius);
        Vec3 farRight = new Vec3(center.x + radius, center.y, center.z - radius);
        DeathSpike spike = new DeathSpike(
                nearLeft, nearRight, farLeft, farRight,
                center.add(Vec3.UP.multiply(height)), Vec3.UP,
                0.0, center, radius, height);
        spike.place(id, owner, AddonFootprint.quadrilateral(
                nearLeft, nearRight, farLeft, farRight));
        return spike;
    }

    private static final class RecordingSink implements AddonEffectSink {
        long hazardId = -1L;
        long pickupId = -1L;
        int charges;

        @Override
        public void hitHazard(long addonId) {
            hazardId = addonId;
        }

        @Override
        public void grantAirJump(long addonId, int charges) {
            pickupId = addonId;
            this.charges = charges;
        }
    }
}
