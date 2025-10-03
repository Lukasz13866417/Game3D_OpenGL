package com.example.game3d_opengl;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public class OpenGLES20Activity extends Activity {
    private MyGLSurfaceView glSurfaceView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        glSurfaceView = new MyGLSurfaceView(this);
        setContentView(glSurfaceView);
    }


    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.onPause();
        if (glSurfaceView.getRenderer().getCurrentStage() != null) {
            glSurfaceView.getRenderer().getCurrentStage().pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        glSurfaceView.onResume();
        if (glSurfaceView.getRenderer().getCurrentStage() != null) {
            glSurfaceView.getRenderer().getCurrentStage().resume();
        }
    }
}
