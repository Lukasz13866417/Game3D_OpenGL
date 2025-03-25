package com.example.game3d_opengl.engine.util3d;

import static com.example.game3d_opengl.engine.util3d.GameMath.INF;
import static java.lang.Math.max;
import static java.lang.Math.min;

import com.example.game3d_opengl.engine.util3d.vector.Vector2D;
import com.example.game3d_opengl.engine.util3d.vector.Vector3D;


public class GameMiscUtil {
    public static float minAll(float... arr){
        float ans = INF;
        for(float x : arr){
            ans = min(ans,x);
        }
        return ans;
    }
    public static float maxAll(float... arr){
        float ans = -INF;
        for(float x : arr){
            ans = max(ans,x);
        }
        return ans;
    }
    public static float minX(Vector3D... arr){
        float ans = INF;
        for(Vector3D v : arr){
            ans = min(ans,v.x);
        }
        return ans;
    }
    public static float maxX(Vector3D... arr){
        float ans = -INF;
        for(Vector3D v : arr){
            ans = max(ans,v.x);
        }
        return ans;
    }
    public static float minY(Vector3D... arr){
        float ans = INF;
        for(Vector3D v : arr){
            ans = min(ans,v.y);
        }
        return ans;
    }
    public static float maxY(Vector3D... arr){
        float ans = -INF;
        for(Vector3D v : arr){
            ans = max(ans,v.y);
        }
        return ans;
    }
    public static float minZ(Vector3D... arr){
        float ans = INF;
        for(Vector3D v : arr){
            ans = min(ans,v.y);
        }
        return ans;
    }
    public static float maxZ(Vector3D... arr){
        float ans = -INF;
        for(Vector3D v : arr){
            ans = max(ans,v.y);
        }
        return ans;
    }
    public static float minX(Vector2D... arr){
        float ans = INF;
        for(Vector2D v : arr){
            ans = min(ans,v.x);
        }
        return ans;
    }
    public static float maxX(Vector2D... arr){
        float ans = -INF;
        for(Vector2D v : arr){
            ans = max(ans,v.x);
        }
        return ans;
    }
    public static float minY(Vector2D... arr){
        float ans = INF;
        for(Vector2D v : arr){
            ans = min(ans,v.y);
        }
        return ans;
    }
    public static float maxY(Vector2D... arr){
        float ans = -INF;
        for(Vector2D v : arr){
            ans = max(ans,v.y);
        }
        return ans;
    }
    public static int[] mergeArrays(int[]... arrays){
        int n=0;
        for(int[] arr : arrays){
            n+=arr.length;
        }
        int[] res = new int[n];
        int ind=0;
        for(int[] arr : arrays){
            for(int el : arr){
                res[ind++] = el;
            }
        }
        return res;
    }
}
