package com.example.game3d_opengl.rendering.text;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import com.example.game3d_opengl.rendering.GPUResourceOwner;

import java.util.HashMap;
import java.util.Map;

public final class BitmapFont implements GPUResourceOwner {
    public static final String DEFAULT_FONT_ASSET = "ncr.ttf";
    public static final String DEFAULT_CHARSET =
            " !\"#$%&'()*+,-./0123456789:;<=>?@" +
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`" +
            "abcdefghijklmnopqrstuvwxyz{|}~";

    private static BitmapFont shared = null;

    public static BitmapFont loadShared(AssetManager assets, int fontPx) {
        if (shared == null) {
            shared = load(assets, DEFAULT_FONT_ASSET, fontPx, 4, DEFAULT_CHARSET);
        }
        return shared;
    }

    public static BitmapFont load(AssetManager assets,
                                  String fontAssetPath,
                                  int fontPx,
                                  int paddingPx,
                                  String charset) {
        return new BitmapFont(assets, fontAssetPath, fontPx, paddingPx, charset);
    }

    public static final class Glyph {
        public final float u0, v0, u1, v1;
        public final int width, height;
        public final int xOffset, yOffset;
        public final float xAdvance;

        private Glyph(float u0, float v0, float u1, float v1,
                      int width, int height,
                      int xOffset, int yOffset,
                      float xAdvance) {
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.width = width;
            this.height = height;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            this.xAdvance = xAdvance;
        }
    }

    public static final class TextMetrics {
        public final float width;
        public final float height;
        public final int lineCount;

        private TextMetrics(float width, float height, int lineCount) {
            this.width = width;
            this.height = height;
            this.lineCount = lineCount;
        }
    }

    private final Map<Character, Glyph> glyphs = new HashMap<>();
    private final int atlasWidth;
    private final int atlasHeight;
    private final Bitmap atlasBitmap;
    private final float lineHeight;
    private final float baseline;
    private final float spaceAdvance;
    private final Glyph fallbackGlyph;
    private int textureId = 0;

    private BitmapFont(AssetManager assets,
                       String fontAssetPath,
                       int fontPx,
                       int paddingPx,
                       String charset) {
        Typeface typeface = Typeface.createFromAsset(assets, fontAssetPath);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(typeface);
        paint.setTextSize(fontPx);
        paint.setColor(Color.WHITE);
        paint.setSubpixelText(true);

        Paint.FontMetrics fm = paint.getFontMetrics();
        float ascent = -fm.ascent;
        float descent = fm.descent;
        float leading = fm.leading;
        this.baseline = ascent;
        this.lineHeight = Math.max(1f, (float) Math.ceil(ascent + descent + leading));

        Rect bounds = new Rect();
        int maxW = 0;
        int maxH = 0;
        for (int i = 0; i < charset.length(); i++) {
            String s = String.valueOf(charset.charAt(i));
            paint.getTextBounds(s, 0, 1, bounds);
            maxW = Math.max(maxW, bounds.width());
            maxH = Math.max(maxH, bounds.height());
        }
        maxW = Math.max(1, maxW);
        maxH = Math.max(1, maxH);

        int cellW = maxW + paddingPx * 2;
        int cellH = maxH + paddingPx * 2;
        int cols = (int) Math.ceil(Math.sqrt(charset.length()));
        int rows = (int) Math.ceil((double) charset.length() / cols);
        this.atlasWidth = Math.max(1, cols * cellW);
        this.atlasHeight = Math.max(1, rows * cellH);

        this.atlasBitmap = Bitmap.createBitmap(atlasWidth, atlasHeight, Bitmap.Config.ARGB_8888);
        this.atlasBitmap.eraseColor(Color.TRANSPARENT);

        Canvas canvas = new Canvas(atlasBitmap);

        for (int i = 0; i < charset.length(); i++) {
            char ch = charset.charAt(i);
            String s = String.valueOf(ch);
            paint.getTextBounds(s, 0, 1, bounds);

            int col = i % cols;
            int row = i / cols;
            int cellX = col * cellW;
            int cellY = row * cellH;

            float drawX = cellX + paddingPx - bounds.left;
            float drawY = cellY + paddingPx - bounds.top;
            canvas.drawText(s, drawX, drawY, paint);

            int gw = bounds.width();
            int gh = bounds.height();
            float glyphX = cellX + paddingPx;
            float glyphY = cellY + paddingPx;

            float u0 = glyphX / atlasWidth;
            float u1 = (glyphX + gw) / atlasWidth;
            float v0 = glyphY / atlasHeight;
            float v1 = (glyphY + gh) / atlasHeight;

            float advance = paint.measureText(s);
            Glyph g = new Glyph(u0, v0, u1, v1, gw, gh, bounds.left, bounds.top, advance);
            glyphs.put(ch, g);
        }

        Glyph space = glyphs.get(' ');
        this.spaceAdvance = space != null ? space.xAdvance : paint.measureText(" ");
        this.fallbackGlyph = glyphs.get('?');
    }

    public float getLineHeight() {
        return lineHeight;
    }

    public float getBaseline() {
        return baseline;
    }

    public float getSpaceAdvance() {
        return spaceAdvance;
    }

    public Glyph getGlyph(char c) {
        Glyph g = glyphs.get(c);
        if (g == null) {
            return fallbackGlyph;
        }
        return g;
    }

    public TextMetrics measureText(String text, float scale) {
        if (text == null || text.isEmpty()) {
            return new TextMetrics(0f, 0f, 0);
        }
        float maxW = 0f;
        float lineW = 0f;
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                maxW = Math.max(maxW, lineW);
                lineW = 0f;
                lines++;
                continue;
            }
            Glyph g = getGlyph(c);
            float adv = (g != null ? g.xAdvance : spaceAdvance) * scale;
            lineW += adv;
        }
        maxW = Math.max(maxW, lineW);
        float height = lines * lineHeight * scale;
        return new TextMetrics(maxW, height, lines);
    }

    public int getTextureId() {
        return textureId;
    }

    public void ensureTexture() {
        if (textureId != 0) return;
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        textureId = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, atlasBitmap, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        cleanupGPUResourcesRecursively();
        ensureTexture();
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
            textureId = 0;
        }
    }
}
