package com.example.game3d_opengl.engine.util3d.rect;

public class Rect {
    public final float x1,y1,x2,y2,w,h;
    public Rect(float x1, float y1, float x2, float y2){
        this.x1=x1;
        this.y1=y1;
        this.x2=x2;
        this.y2=y2;
        this.w = x2-x1;
        this.h = y2-y1;
    }
    public boolean containsPoint(float x, float y){
        return x>=x1 && x<=x2 && y>=y1 && y<=y2;
    }
}
