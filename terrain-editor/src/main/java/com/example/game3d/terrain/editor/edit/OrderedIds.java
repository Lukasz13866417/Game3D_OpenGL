package com.example.game3d.terrain.editor.edit;

import java.util.ArrayList;
import java.util.List;

/** Small deterministic ordering helper shared by buttons and drag/drop. */
public final class OrderedIds {
    private OrderedIds() {
    }

    public static List<String> moveBy(List<String> orderedIds, String draggedId, int delta) {
        int from = orderedIds.indexOf(draggedId);
        if (from < 0) {
            throw new IllegalArgumentException("Unknown dragged ID " + draggedId);
        }
        int to = Math.max(0, Math.min(orderedIds.size() - 1, from + delta));
        return moveTo(orderedIds, draggedId, to);
    }

    /** Moves an ID to the target's current position. */
    public static List<String> moveOnto(
            List<String> orderedIds, String draggedId, String targetId) {
        int to = orderedIds.indexOf(targetId);
        if (to < 0) {
            throw new IllegalArgumentException("Unknown target ID " + targetId);
        }
        return moveTo(orderedIds, draggedId, to);
    }

    private static List<String> moveTo(List<String> source, String draggedId, int to) {
        List<String> result = new ArrayList<>(source);
        int from = result.indexOf(draggedId);
        if (from < 0) {
            throw new IllegalArgumentException("Unknown dragged ID " + draggedId);
        }
        String value = result.remove(from);
        result.add(Math.max(0, Math.min(result.size(), to)), value);
        return result;
    }
}
