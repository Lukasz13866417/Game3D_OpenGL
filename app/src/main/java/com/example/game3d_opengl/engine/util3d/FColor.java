package com.example.game3d_opengl.engine.util3d;

public class FColor {
    public final float[] rgba;
    public FColor(float[] rgba){
        this.rgba = rgba;
    }
    public FColor(float r, float g, float b, float a){
        this.rgba = new float[]{
                r,g,b,a
        };
    }
    public FColor(float r, float g, float b){
        this.rgba = new float[]{
                r,g,b,1.0f
        };
    }

    public float r(){
        return rgba[0];
    }
    public float g(){
        return rgba[1];
    }
    public float b(){
        return rgba[2];
    }
    public float a(){
        return rgba[3];
    }

    public static FColor CLR(float r, float g, float b, float a){
        return new FColor(r,g,b,a);
    }
    public static FColor CLR(float r, float g, float b){
        return new FColor(r,g,b);
    }


}
