package com.example.game3d_opengl.game.util;

import static java.lang.Math.pow;

import java.util.HashMap;
import java.util.Random;

public class GameRandom {

    private static final int seed = new Random().nextInt(1000);

    static{
        System.out.println("SEED: "+seed);
    }
    private static Random RANDOM = new Random(seed);

    /** Returns a float in [0, 1), equivalent to (float) Math.random(). */
    public static float nextFloat() {
        return RANDOM.nextFloat();
    }

    public static int randInt(int l, int r){
        return l+RANDOM.nextInt(r-l+1);
    }

    /**
     * Samples {@code count} distinct grid points uniformly without replacement from the
     * inclusive 1-based rectangle {@code [1..nRows] x [1..nCols]}.
     */
    public static int[][] sampleDistinctGridPoints(int nRows, int nCols, int count) {
        if (nRows <= 0 || nCols <= 0) {
            throw new IllegalArgumentException("Grid dimensions must be > 0");
        }
        long totalLong = (long) nRows * (long) nCols;
        if (totalLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Grid too large");
        }
        int total = (int) totalLong;
        if (count < 0 || count > total) {
            throw new IllegalArgumentException("count must be in [0, nRows * nCols]");
        }
        int[][] out = new int[count][2];
        HashMap<Integer, Integer> remap = new HashMap<>(Math.max(1, count * 2));
        for (int i = 0; i < count; ++i) {
            int remaining = total - i;
            int draw = RANDOM.nextInt(remaining);
            int chosen = remap.containsKey(draw) ? remap.get(draw) : draw;
            int lastIndex = remaining - 1;
            int replacement = remap.containsKey(lastIndex) ? remap.get(lastIndex) : lastIndex;
            if (draw != lastIndex) {
                remap.put(draw, replacement);
            }
            remap.remove(lastIndex);
            out[i][0] = chosen / nCols + 1;
            out[i][1] = chosen % nCols + 1;
        }
        return out;
    }

    public static float randFloat(float min, float max, int decimalDigits) {
        if (min > max || decimalDigits < 0) {
            throw new IllegalArgumentException("Invalid input values");
        }
        if(min==max){
            return min;
        }
        double randomValue = min + (RANDOM.nextDouble() * (max - min));
        double scaleFactor = pow(10, decimalDigits);

        return (float) (Math.round(randomValue * scaleFactor) / scaleFactor);
    }

    public static float randFloatRanges(int decimalDigits, float... args){
        if((args.length & 1) == 1){
            throw new IllegalArgumentException("Odd number of args");
        }
        int n = args.length / 2;
        int ind = randInt(0,n-1);
        float l = args[ind*2], r = args[2*ind+1];
        return randFloat(l,r,decimalDigits);
    }

    public static float choice(float... args){
        return args[randInt(0,args.length-1)];
    }

    public static String choice(String... args){
        return args[randInt(0,args.length-1)];
    }
}
