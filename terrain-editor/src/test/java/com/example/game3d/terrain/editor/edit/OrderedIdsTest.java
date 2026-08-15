package com.example.game3d.terrain.editor.edit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderedIdsTest {
    @Test void buttonAndDragMovesRetainEveryItem() {
        assertEquals(List.of("b", "a", "c"),
                OrderedIds.moveBy(List.of("a", "b", "c"), "a", 1));
        assertEquals(List.of("c", "a", "b"),
                OrderedIds.moveOnto(List.of("a", "b", "c"), "c", "a"));
        assertEquals(List.of("b", "c", "a"),
                OrderedIds.moveOnto(List.of("a", "b", "c"), "a", "c"));
    }
}
