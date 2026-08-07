package com.example.game3d_opengl.screenshot;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.MediaStore;
import android.util.Log;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import com.example.game3d_opengl.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Captures the most recently presented {@link SurfaceView} buffer without blocking its GL thread.
 * PNG conversion and storage are kept on a single background worker.
 */
public final class ScreenshotCaptureController implements AutoCloseable {
    private static final String TAG = "GameScreenshot";
    private static final String ALBUM_NAME = "Game3D";
    private static final float BUSY_BUTTON_ALPHA = 0.55f;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService writerExecutor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(() -> {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    runnable.run();
                }, "GameScreenshotWriter");
                thread.setDaemon(true);
                return thread;
            });
    private final CaptureGate captureGate = new CaptureGate();
    private volatile boolean closed;

    public ScreenshotCaptureController(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context == null");
        }
        Context applicationContext = context.getApplicationContext();
        appContext = applicationContext != null ? applicationContext : context;
    }

    /**
     * Starts one capture. This is intended to be called by a Button's onClick callback, which
     * means the action occurs on release and the Button consumes the gesture before GLSurfaceView.
     */
    public void capture(SurfaceView source, View triggerView) {
        if (closed) {
            return;
        }
        if (!captureGate.tryBegin()) {
            showToast(R.string.screenshot_already_in_progress);
            return;
        }

        setTriggerBusy(triggerView, true);
        int width = source != null ? source.getWidth() : 0;
        int height = source != null ? source.getHeight() : 0;
        Surface surface = source != null ? source.getHolder().getSurface() : null;
        if (width <= 0 || height <= 0 || surface == null || !surface.isValid()) {
            finishFailure(triggerView, appContext.getString(
                    R.string.screenshot_surface_unavailable), null);
            return;
        }

        final Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } catch (RuntimeException | OutOfMemoryError error) {
            finishFailure(triggerView, appContext.getString(
                    R.string.screenshot_capture_failed), error);
            return;
        }

        try {
            PixelCopy.request(source, bitmap, result -> {
                if (closed) {
                    bitmap.recycle();
                    captureGate.finish();
                    return;
                }
                if (result != PixelCopy.SUCCESS) {
                    bitmap.recycle();
                    finishFailure(
                            triggerView,
                            appContext.getString(
                                    R.string.screenshot_capture_failed_with_code,
                                    result),
                            null);
                    return;
                }
                saveInBackground(bitmap, triggerView);
            }, mainHandler);
        } catch (RuntimeException error) {
            bitmap.recycle();
            finishFailure(triggerView, appContext.getString(
                    R.string.screenshot_capture_failed), error);
        }
    }

    private void saveInBackground(Bitmap bitmap, View triggerView) {
        try {
            writerExecutor.execute(() -> {
                try {
                    String displayName = buildDisplayName(System.currentTimeMillis());
                    String location = savePng(bitmap, displayName);
                    Log.i(TAG, "Screenshot saved to " + location);
                    finishSuccess(triggerView, location);
                } catch (IOException | RuntimeException error) {
                    finishFailure(
                            triggerView,
                            appContext.getString(R.string.screenshot_save_failed),
                            error);
                } finally {
                    bitmap.recycle();
                }
            });
        } catch (RejectedExecutionException error) {
            bitmap.recycle();
            finishFailure(triggerView, appContext.getString(
                    R.string.screenshot_save_failed), error);
        }
    }

    private String savePng(Bitmap bitmap, String displayName) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveToMediaStore(bitmap, displayName);
        }
        return saveToAppPictures(bitmap, displayName);
    }

    private String saveToMediaStore(Bitmap bitmap, String displayName)
            throws IOException {
        ContentResolver resolver = appContext.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separator + ALBUM_NAME);
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        android.net.Uri uri = resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("MediaStore insert returned null");
        }

        boolean published = false;
        try {
            try (OutputStream output = resolver.openOutputStream(uri, "w")) {
                if (output == null) {
                    throw new IOException("MediaStore output stream is null");
                }
                writePng(bitmap, output);
            }

            ContentValues publish = new ContentValues();
            publish.put(MediaStore.Images.Media.IS_PENDING, 0);
            if (resolver.update(uri, publish, null, null) <= 0) {
                throw new IOException("Could not publish MediaStore image");
            }
            published = true;
            return Environment.DIRECTORY_PICTURES
                    + File.separator + ALBUM_NAME
                    + File.separator + displayName;
        } finally {
            if (!published) {
                try {
                    resolver.delete(uri, null, null);
                } catch (RuntimeException cleanupError) {
                    Log.w(TAG, "Could not remove incomplete screenshot", cleanupError);
                }
            }
        }
    }

    /** No-permission fallback for the one supported pre-scoped-storage API level (API 28). */
    private String saveToAppPictures(Bitmap bitmap, String displayName)
            throws IOException {
        File pictures = appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (pictures == null) {
            pictures = new File(appContext.getFilesDir(), "screenshots");
        }
        File album = new File(pictures, ALBUM_NAME);
        if (!album.isDirectory() && !album.mkdirs() && !album.isDirectory()) {
            throw new IOException("Could not create screenshot directory " + album);
        }

        File destination = uniqueDestination(album, displayName);
        boolean written = false;
        try (OutputStream output = new FileOutputStream(destination)) {
            writePng(bitmap, output);
            written = true;
        } finally {
            if (!written && destination.exists() && !destination.delete()) {
                Log.w(TAG, "Could not remove incomplete screenshot " + destination);
            }
        }
        MediaScannerConnection.scanFile(
                appContext,
                new String[]{destination.getAbsolutePath()},
                new String[]{"image/png"},
                null);
        return destination.getAbsolutePath();
    }

    private static File uniqueDestination(File directory, String displayName) {
        File candidate = new File(directory, displayName);
        if (!candidate.exists()) {
            return candidate;
        }
        String stem = displayName.endsWith(".png")
                ? displayName.substring(0, displayName.length() - 4)
                : displayName;
        for (int suffix = 1; suffix < Integer.MAX_VALUE; suffix++) {
            candidate = new File(directory, stem + "_" + suffix + ".png");
            if (!candidate.exists()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not choose a screenshot filename");
    }

    private static void writePng(Bitmap bitmap, OutputStream output)
            throws IOException {
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
            throw new IOException("Bitmap PNG compression failed");
        }
        output.flush();
    }

    static String buildDisplayName(long epochMillis) {
        SimpleDateFormat timestamp = new SimpleDateFormat(
                "yyyyMMdd_HHmmss_SSS", Locale.US);
        return "Game3D_" + timestamp.format(new Date(epochMillis)) + ".png";
    }

    private void finishSuccess(View triggerView, String location) {
        mainHandler.post(() -> {
            captureGate.finish();
            if (closed) {
                return;
            }
            setTriggerBusy(triggerView, false);
            Toast.makeText(
                    appContext,
                    appContext.getString(R.string.screenshot_saved, location),
                    Toast.LENGTH_LONG).show();
        });
    }

    private void finishFailure(View triggerView, String message, Throwable error) {
        if (error == null) {
            Log.e(TAG, message);
        } else {
            Log.e(TAG, message, error);
        }
        mainHandler.post(() -> {
            captureGate.finish();
            if (closed) {
                return;
            }
            setTriggerBusy(triggerView, false);
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show();
        });
    }

    private void showToast(int messageResource) {
        mainHandler.post(() -> {
            if (!closed) {
                Toast.makeText(
                        appContext, messageResource, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static void setTriggerBusy(View triggerView, boolean busy) {
        if (triggerView == null) {
            return;
        }
        triggerView.setEnabled(!busy);
        triggerView.setAlpha(busy ? BUSY_BUTTON_ALPHA : 1f);
    }

    @Override
    public void close() {
        closed = true;
        writerExecutor.shutdown();
    }

    /** Small independently-testable guard covering both PixelCopy and asynchronous saving. */
    static final class CaptureGate {
        private final AtomicBoolean inFlight = new AtomicBoolean();

        boolean tryBegin() {
            return inFlight.compareAndSet(false, true);
        }

        void finish() {
            inFlight.set(false);
        }

        boolean isInFlight() {
            return inFlight.get();
        }
    }
}
