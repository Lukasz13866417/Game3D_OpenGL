package com.example.game3d_opengl.engine.util.vector;

public class Vector2D {
    public final float x, y;

    public Vector2D(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public static Vector2D V2(float x, float y){
        return new Vector2D(x, y);
    }

    public static Vector2D V2(double x, double y){
        return new Vector2D((float) x, (float) y);
    }

    public Vector2D add(Vector2D other) {
        return V2(x + other.x, y + other.y);
    }

    public static Vector2D add(Vector2D v1, Vector2D v2) {
        return V2(v1.x + v2.x, v1.y + v2.y);
    }

    public Vector2D sub(Vector2D other) {
        return V2(x - other.x, y - other.y);
    }

    public static Vector2D sub(Vector2D v1, Vector2D v2) {
        return V2(v1.x - v2.x, v1.y - v2.y);
    }

    public Vector2D mult(float k) {
        return V2(x * k, y * k);
    }

    public static Vector2D mult(Vector2D v, float k) {
        return V2(v.x * k, v.y * k);
    }

    public Vector2D div(float k) {
        return V2(x / k, y / k);
    }

    public static Vector2D div(Vector2D v, float k) {
        return V2(v.x / k, v.y / k);
    }

    public double sqlen() {
        return x * x + y * y;
    }

    public static double sqlen(Vector2D v) {
        return v.x * v.x + v.y * v.y;
    }

    @Override
    public String toString() {
        return "{" + x + "," + y + "}";
    }

    public Vector2D multX(float scaleX) {
        return V2(x * scaleX, y);
    }

    public static Vector2D multX(Vector2D v, float scaleX) {
        return V2(v.x * scaleX, v.y);
    }

    public Vector2D multY(float scaleY) {
        return V2(x, y * scaleY);
    }

    public static Vector2D multY(Vector2D v, float scaleY) {
        return V2(v.x, v.y * scaleY);
    }

    public Vector2D add(float x, float y) {
        return V2(this.x + x, this.y + y);
    }

    public static Vector2D add(Vector2D v, float x, float y) {
        return V2(v.x + x, v.y + y);
    }

    public Vector2D sub(float x, float y) {
        return V2(this.x - x, this.y - y);
    }

    public static Vector2D sub(Vector2D v, float x, float y) {
        return V2(v.x - x, v.y - y);
    }

    public static Vector2D XZ(Vector3D v){
        return V2(v.x,v.z);
    }
    public static Vector2D XY(Vector3D v){
        return V2(v.x,v.y);
    }
    public static Vector2D YZ(Vector3D v){
        return V2(v.y,v.z);
    }

}
