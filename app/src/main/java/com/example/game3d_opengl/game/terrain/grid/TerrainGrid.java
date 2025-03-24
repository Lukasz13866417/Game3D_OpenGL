package com.example.game3d_opengl.game.terrain.grid;


import com.example.game3d_opengl.game.terrain.grid.symbolic.GridCreator;

public class TerrainGrid {
    private final int nRows, nCols;
    private final GridCreator symbolic;

    public TerrainGrid(int nRows, int nCols, TerrainGrid parent, int rowOffsetInParent) {
        this.nRows = nRows;
        this.nCols = nCols;
        if(parent != null) {
            this.symbolic = new GridCreator(nRows, nCols, parent.symbolic, rowOffsetInParent);
        }else{
            this.symbolic = new GridCreator(nRows, nCols, null, rowOffsetInParent);
        }
    }

    public TerrainGrid(int nRows, int nCols){
        this(nRows,nCols,null,0);
    }


    public void destroy(){
        symbolic.destroy();
    }
}
