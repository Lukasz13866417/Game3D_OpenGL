package com.example.game3d.terrain.io.publish;

import com.example.game3d.authoring.BaseTerrainStructure;

import java.util.List;

/** Handwritten source provider. Every call must return fresh one-shot structures. */
public interface JavaLevelProvider {
    String id();
    List<BaseTerrainStructure<?>> createStructures();
}
