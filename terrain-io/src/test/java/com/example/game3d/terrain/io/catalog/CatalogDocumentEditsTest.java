package com.example.game3d.terrain.io.catalog;

import com.example.game3d.terrain.io.model.CatalogDocument;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class CatalogDocumentEditsTest {
    @Test public void customEntriesCanBeEditedAndReorderedAfterLockedPrefix() {
        CatalogDocument initial = CatalogDocumentEdits.newGameplayCatalog("main");
        CatalogDocument withEntries = CatalogDocumentEdits.addJsonLevel(
                CatalogDocumentEdits.addJsonLevel(initial,
                        "one", "level-one", true),
                "two", "level-two", true);

        CatalogDocument reordered = CatalogDocumentEdits.reorderCustom(
                withEntries, Arrays.asList("two", "one"));
        CatalogDocument disabled = CatalogDocumentEdits.setEnabled(
                reordered, "two", false);

        int first = GameplayCatalogPolicy.customEntryStartIndex();
        assertEquals("two", disabled.entries().get(first).id());
        assertFalse(disabled.entries().get(first).enabled());
        assertEquals(GameplayCatalogPolicy.requiredBuiltinIds().get(0),
                disabled.entries().get(0).id());
    }

    @Test public void requiredBuiltinCannotBeRemovedOrDisabled() {
        CatalogDocument catalog = CatalogDocumentEdits.newGameplayCatalog("main");
        String builtin = GameplayCatalogPolicy.requiredBuiltinIds().get(0);
        try {
            CatalogDocumentEdits.remove(catalog, builtin);
            fail("Expected locked built-in rejection");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            CatalogDocumentEdits.setEnabled(catalog, builtin, false);
            fail("Expected locked built-in rejection");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    @Test public void customOrderMustNameEveryEntryExactlyOnce() {
        CatalogDocument catalog = CatalogDocumentEdits.addJsonLevel(
                CatalogDocumentEdits.newGameplayCatalog("main"),
                "one", "level-one", true);
        try {
            CatalogDocumentEdits.reorderCustom(catalog, Arrays.asList("one", "one"));
            fail("Expected invalid order rejection");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
