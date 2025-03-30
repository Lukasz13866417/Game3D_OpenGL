package com.example.game3d_opengl.game.terrain.main;

import static com.example.game3d_opengl.game.terrain.main.AddonsCommandsExecutor.CMD_FINISH_STRUCTURE_ADDONS;
import static com.example.game3d_opengl.game.terrain.main.AddonsCommandsExecutor.CMD_RESERVE_HORIZONTAL;
import static com.example.game3d_opengl.game.terrain.main.AddonsCommandsExecutor.CMD_RESERVE_RANDOM_HORIZONTAL;
import static com.example.game3d_opengl.game.terrain.main.AddonsCommandsExecutor.CMD_RESERVE_RANDOM_VERTICAL;
import static com.example.game3d_opengl.game.terrain.main.AddonsCommandsExecutor.CMD_RESERVE_VERTICAL;
import static com.example.game3d_opengl.game.terrain.main.AddonsCommandsExecutor.CMD_START_STRUCTURE_ADDONS;
import static com.example.game3d_opengl.game.terrain.main.LandscapeCommandsExecutor.CMD_ADD_H_ANG;
import static com.example.game3d_opengl.game.terrain.main.LandscapeCommandsExecutor.CMD_ADD_SEG;
import static com.example.game3d_opengl.game.terrain.main.LandscapeCommandsExecutor.CMD_ADD_V_ANG;
import static com.example.game3d_opengl.game.terrain.main.LandscapeCommandsExecutor.CMD_FINISH_STRUCTURE_LANDSCAPE;
import static com.example.game3d_opengl.game.terrain.main.LandscapeCommandsExecutor.CMD_SET_H_ANG;
import static com.example.game3d_opengl.game.terrain.main.LandscapeCommandsExecutor.CMD_SET_V_ANG;
import static com.example.game3d_opengl.game.terrain.main.LandscapeCommandsExecutor.CMD_START_STRUCTURE_LANDSCAPE;

class Util {
    static void printCommand(float[] buffer, int offset) {
        int code = (int)(buffer[offset]);
        if (code == CMD_SET_H_ANG) {
            System.out.println("SET H ANG " + buffer[offset + 2]);
        } else if (code == CMD_SET_V_ANG) {
            System.out.println("SET V ANG " + buffer[offset + 2]);
        } else if (code == CMD_ADD_V_ANG) {
            System.out.println("ADD V ANG " + buffer[offset + 2]);
        } else if (code == CMD_ADD_H_ANG) {
            System.out.println("ADD H ANG " + buffer[offset + 2]);
        } else if (code == CMD_ADD_SEG) {
            System.out.println("ADD SEG");
        } else if (code == CMD_START_STRUCTURE_LANDSCAPE) {
            System.out.println("START STRUCTURE LANDSCAPE");
        } else if (code == CMD_FINISH_STRUCTURE_LANDSCAPE) {
            System.out.println("FINISH STRUCTURE LANDSCAPE");
        } else if (code == CMD_RESERVE_VERTICAL) {
            int row = (int) buffer[offset + 2];
            int col = (int) buffer[offset + 3];
            int segLength = (int) buffer[offset + 4];
            System.out.println("RESERVE VERTICAL" + row + "," + col + "," + segLength);
        } else if (code == CMD_RESERVE_HORIZONTAL) {
            int row = (int) buffer[offset + 2];
            int col = (int) buffer[offset + 3];
            int segLength = (int) buffer[offset + 4];
            System.out.println("RESERVE HORIZONTAL " + row + "," + col + "," + segLength);
        } else if (code == CMD_RESERVE_RANDOM_VERTICAL) {
            System.out.println("RESERVE RANDOM VERTICAL " + buffer[offset + 2]);
        } else if (code == CMD_RESERVE_RANDOM_HORIZONTAL) {
            System.out.println("RESERVE RANDOM HORIZONTAL " + buffer[offset + 2]);
        } else if (code == CMD_FINISH_STRUCTURE_ADDONS) {
            System.out.println("FINISH STRUCTURE ADDONS");
        } else if (code == CMD_START_STRUCTURE_ADDONS) {
            System.out.println("START STRUCTURE ADDONS");
        } else {
            throw new IllegalArgumentException("Unknown command code: " + code);
        }
    }
}
