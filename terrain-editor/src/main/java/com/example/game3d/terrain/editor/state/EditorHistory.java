package com.example.game3d.terrain.editor.state;

import com.example.game3d.terrain.io.model.TerrainSourceDocument;

import java.util.ArrayDeque;
import java.util.Deque;

/** Per-tab undo/redo. One DocumentEdit is always one compound operation. */
public final class EditorHistory {
    private static final int MAX_OPERATIONS = 200;
    private final Deque<TerrainSourceDocument> undo = new ArrayDeque<>();
    private final Deque<TerrainSourceDocument> redo = new ArrayDeque<>();
    private EditorState state;

    public EditorHistory(EditorState initial) { this.state = initial; }
    public EditorState state() { return state; }
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }

    public EditorState apply(DocumentEdit edit) {
        TerrainSourceDocument next = edit.apply(state.document());
        if (next == state.document()) return state;
        undo.addLast(state.document());
        while (undo.size() > MAX_OPERATIONS) undo.removeFirst();
        redo.clear();
        state = state.withDocument(next);
        return state;
    }

    public EditorState undo() {
        if (undo.isEmpty()) return state;
        redo.addLast(state.document());
        state = state.withDocument(undo.removeLast());
        return state;
    }

    public EditorState redo() {
        if (redo.isEmpty()) return state;
        undo.addLast(state.document());
        state = state.withDocument(redo.removeLast());
        return state;
    }

    public void replaceState(EditorState value) { state = value; }

    /** Replaces a document due to explicit reload and starts a fresh undo history. */
    public void reset(EditorState value) {
        undo.clear();
        redo.clear();
        state = value;
    }
}
