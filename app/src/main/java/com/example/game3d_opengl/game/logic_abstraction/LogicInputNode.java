package com.example.game3d_opengl.game.logic_abstraction;

public abstract class LogicInputNode<DataClass> extends StateInfoNode<DataClass>{
    public abstract void setData(DataClass what);
}
