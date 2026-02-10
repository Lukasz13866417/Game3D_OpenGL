package com.example.game3d_opengl.rendering;

public class ScreenInfo {
    private static int screenW = -1, screenH = -1;
    public static void setScreenSize(int w, int h){
        assert w > 0;
        assert h > 0;
        screenW = w;
        screenH = h;
    }

    public static int getScreenW(){
        assert screenW != -1 && screenH != -1;
        return screenW;
    }

    public static int getScreenH(){
        assert screenW != -1 && screenH != -1;
        return screenH;
    }
}
