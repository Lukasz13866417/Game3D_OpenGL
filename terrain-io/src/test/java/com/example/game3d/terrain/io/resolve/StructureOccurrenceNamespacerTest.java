package com.example.game3d.terrain.io.resolve;

import com.example.game3d.terrain.io.model.AddonKind;
import com.example.game3d.terrain.io.model.AddonReservation;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.Placement;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class StructureOccurrenceNamespacerTest {
    @Test public void rewritesEveryLocalReferenceIncludingPortalPairs() {
        String tileId = "10000000-0000-0000-0000-000000000001";
        String entranceId = "20000000-0000-0000-0000-000000000001";
        String exitId = "20000000-0000-0000-0000-000000000002";
        StructureDocument source = new StructureDocument(1, "portals", GridMode.ADVANCED,
                Collections.singletonList(new TileRecord(
                        tileId, true, 0, 0, 0, "NORMAL", 1, 1)),
                Arrays.asList(
                        new AddonReservation(entranceId, AddonKind.PORTAL_ENTRANCE,
                                Placement.normalized(tileId, .5, .5), exitId,
                                Collections.<String, Double>emptyMap()),
                        new AddonReservation(exitId, AddonKind.PORTAL_EXIT,
                                Placement.normalized(tileId, .5, .5), entranceId,
                                Collections.<String, Double>emptyMap())));
        ResolvedStructureOccurrence occurrence = new ResolvedStructureOccurrence(
                Collections.singletonList(
                        "30000000-0000-0000-0000-000000000001"), source);

        StructureDocument namespaced = StructureOccurrenceNamespacer.namespace(occurrence);

        String rewrittenTile = namespaced.tiles().get(0).sourceId();
        String rewrittenEntrance = namespaced.addons().get(0).sourceId();
        String rewrittenExit = namespaced.addons().get(1).sourceId();
        assertNotEquals(tileId, rewrittenTile);
        assertEquals(rewrittenTile,
                namespaced.addons().get(0).placement().segmentSourceId());
        assertEquals(rewrittenTile,
                namespaced.addons().get(1).placement().segmentSourceId());
        assertEquals(rewrittenExit, namespaced.addons().get(0).pairSourceId());
        assertEquals(rewrittenEntrance, namespaced.addons().get(1).pairSourceId());
    }
}
