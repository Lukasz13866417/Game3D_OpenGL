package com.example.game3d_opengl.game.util;

import static java.lang.Math.pow;

import java.util.Random;

public class GameRandom {

    private static final int seed = new Random().nextInt(1000);

    static{
        System.out.println("SEED: "+seed);
    }
    private static Random RANDOM = new Random(seed);

    public static int randInt(int l, int r){
        return l+RANDOM.nextInt(r-l+1);
    }

    public static float randFloat(float min, float max, int decimalDigits) {
        if (min > max || decimalDigits < 0) {
            throw new IllegalArgumentException("Invalid input values");
        }
        if(min==max){
            return min;
        }
        Random random = new Random();
        double randomValue = min + (random.nextDouble() * (max - min));
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
