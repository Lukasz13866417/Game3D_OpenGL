package com.example.game3d_opengl.game.logic_abstraction;

public abstract class StateInfoNode<DataClass> {

    int nUsers; // only for bookkeeping in StateInfoGraph
    int nReadyUsers; // only for bookkeeping in StateInfoGraph
    int indInOriginalOrdering; // only for bookkeeping in StateInfoGraph

    public abstract DataClass getData();

    public abstract void calc();
}
