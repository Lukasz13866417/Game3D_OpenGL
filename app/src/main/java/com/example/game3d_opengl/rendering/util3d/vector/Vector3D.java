package com.example.game3d_opengl.rendering.util3d.vector;

import static java.lang.Math.sqrt;

import androidx.annotation.NonNull;

import java.util.Locale;

public class Vector3D{
    public final float x, y, z;

    public Vector3D(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3D setX(float newx){
        return V3(newx,y,z);
    }

    public Vector3D setY(float newy){
        return V3(x,newy,z);
    }

    public Vector3D setZ(float newz){
        return V3(x,y,newz);
    }

    public Vector3D addX(float dx) {
        return V3(x+dx,y,z);
    }

    public Vector3D addY(float dy) {
        return V3(x,y+dy,z);
    }

    public Vector3D addZ(float dz) {
        return V3(x,y,z+dz);
    }

    public Vector3D setX(double newx){
        return V3(newx,y,z);
    }

    public Vector3D setY(double newy){
        return V3(x,newy,z);
    }

    public Vector3D setZ(double newz){
        return V3(x,y,newz);
    }

    public Vector3D addX(double dx) {
        return V3(x+dx,y,z);
    }

    public Vector3D addY(double dy) {
        return V3(x,y+dy,z);
    }

    public Vector3D addZ(double dz) {
        return V3(x,y,z+dz);
    }

    public double sqlen() {
        return x * x + y * y + z * z;
    }

    public Vector3D crossProduct(Vector3D other) {
        return new Vector3D(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
    }

    public static Vector3D crossProduct(Vector3D v1, Vector3D v2) {
        return new Vector3D(v1.y * v2.z - v1.z * v2.y, v1.z * v2.x - v1.x * v2.z, v1.x * v2.y - v1.y * v2.x);
    }

    public Vector3D add(Vector3D other) {
        return new Vector3D(x + other.x, y + other.y, z + other.z);
    }

    public static Vector3D add(Vector3D v1, Vector3D v2) {
        return new Vector3D(v1.x + v2.x, v1.y + v2.y, v1.z + v2.z);
    }

    public Vector3D sub(Vector3D other) {
        return new Vector3D(x - other.x, y - other.y, z - other.z);
    }

    public static Vector3D sub(Vector3D v1, Vector3D v2) {
        return new Vector3D(v1.x - v2.x, v1.y - v2.y, v1.z - v2.z);
    }

    public Vector3D add(float x, float y, float z) {
        return new Vector3D(this.x + x, this.y + y, this.z + z);
    }

    public static Vector3D add(Vector3D v, float x, float y, float z) {
        return new Vector3D(v.x + x, v.y + y, v.z + z);
    }

    public Vector3D sub(float x, float y, float z) {
        return new Vector3D(this.x - x, this.y - y, this.z - z);
    }

    public static Vector3D sub(Vector3D v, float x, float y, float z) {
        return new Vector3D(v.x - x, v.y - y, v.z - z);
    }

    public Vector3D div(float k) {
        return new Vector3D(x / k, y / k, z / k);
    }

    public static Vector3D div(Vector3D v, float k) {
        return new Vector3D(v.x / k, v.y / k, v.z / k);
    }

    public Vector3D mult(float k) {
        return new Vector3D(x * k, y * k, z * k);
    }

    public static Vector3D mult(Vector3D v, float k) {
        return new Vector3D(v.x * k, v.y * k, v.z * k);
    }

    public Vector3D normalized() {
        return div((float) sqrt(sqlen()));
    }

    public Vector3D withLen(float len){
        return mult((float)((double)(len) / sqrt(sqlen())));
    }

    public static Vector3D normalized(Vector3D v) {
        return div(v, (float) sqrt(v.sqlen()));
    }

    public float dotProduct(Vector3D other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public static float dotProduct(Vector3D v1, Vector3D v2) {
        return v1.x * v2.x + v1.y * v2.y + v1.z * v2.z;
    }

    public static Vector3D[] V3S(Vector3D... vs){
        return vs;
    }

    public static Vector3D V3(float x, float y, float z){
        return new Vector3D(x,y,z);
    }

    public static Vector3D V3(double x, double y, double z){
        return new Vector3D((float) x, (float) y, (float) z);
    }

    public static Vector3D V3(Vector3D other){
        return new Vector3D(other.x,other.y,other.z);
    }

    public Vector3D multX(float scaleX) {
        return V3(x * scaleX, y, z);
    }

    public static Vector3D multX(Vector3D v, float scaleX) {
        return V3(v.x * scaleX, v.y, v.z);
    }

    public Vector3D multZ(float scaleZ) {
        return V3(x, y, z * scaleZ);
    }

    public static Vector3D multZ(Vector3D v, float scaleZ) {
        return V3(v.x, v.y, v.z * scaleZ);
    }

    public Vector3D multY(float scaleY) {
        return V3(x, y * scaleY, z);
    }

    public static Vector3D multY(Vector3D v, float scaleY) {
        return V3(v.x, v.y * scaleY, v.z);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.ROOT,"{%.5f, %.5f, %.5f}", x, y, z);
    }


    public Vector2D to2D() {
        return new Vector2D(x, y);
    }

    public static Vector2D to2D(Vector3D v) {
        return new Vector2D(v.x, v.y);
    }
}
