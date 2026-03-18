package com.example.game3d_opengl.rendering.text;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.ScreenInfo;
import com.example.game3d_opengl.rendering.util3d.FColor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public final class TextRenderer implements GPUResourceOwner {
    private static final int FLOATS_PER_VERTEX = 8; // pos(2) + uv(2) + color(4)
    private static final int BYTES_PER_FLOAT = 4;
    private static final FColor DEFAULT_COLOR = FColor.CLR(1f, 1f, 1f, 1f);

    public enum Anchor {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        CENTER_LEFT, CENTER, CENTER_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    public final class TextLabel {
        private String text;
        private float xPx;
        private float yPx;
        private float scale = 1f;
        private FColor color = DEFAULT_COLOR;
        private Anchor anchor = Anchor.TOP_LEFT;
        private boolean visible = true;

        private TextLabel(String text, float xPx, float yPx, FColor color) {
            this.text = text;
            this.xPx = xPx;
            this.yPx = yPx;
            if (color != null) this.color = color;
        }

        public String getText() { return text; }
        public float getX() { return xPx; }
        public float getY() { return yPx; }
        public float getScale() { return scale; }
        public FColor getColor() { return color; }
        public Anchor getAnchor() { return anchor; }
        public boolean isVisible() { return visible; }

        public TextLabel setText(String text) {
            if (text == null) text = "";
            if (!text.equals(this.text)) {
                this.text = text;
                markDirty();
            }
            return this;
        }

        public TextLabel setPosition(float xPx, float yPx) {
            if (this.xPx != xPx || this.yPx != yPx) {
                this.xPx = xPx;
                this.yPx = yPx;
                markDirty();
            }
            return this;
        }

        public TextLabel setScale(float scale) {
            if (this.scale != scale) {
                this.scale = scale;
                markDirty();
            }
            return this;
        }

        public TextLabel setColor(FColor color) {
            if (color != null && this.color != color) {
                this.color = color;
                markDirty();
            }
            return this;
        }

        public TextLabel setAnchor(Anchor anchor) {
            if (anchor != null && this.anchor != anchor) {
                this.anchor = anchor;
                markDirty();
            }
            return this;
        }

        public TextLabel setVisible(boolean visible) {
            if (this.visible != visible) {
                this.visible = visible;
                markDirty();
            }
            return this;
        }
    }

    private final BitmapFont font;
    private final TextShaderPair shader;
    private final TextShaderArgs.VS vsArgs = new TextShaderArgs.VS();
    private final TextShaderArgs.FS fsArgs = new TextShaderArgs.FS();
    private final List<TextLabel> labels = new ArrayList<>();

    private boolean dirty = true;
    private int vboId = 0;
    private FloatBuffer vertexBuffer;
    private int vertexCapacityFloats = 0;
    private int vertexCount = 0;

    public TextRenderer(BitmapFont font) {
        this.font = font;
        this.shader = TextShaderPair.getSharedShader();
    }

    public TextLabel createLabel(String text, float xPx, float yPx, FColor color) {
        TextLabel label = new TextLabel(text, xPx, yPx, color);
        labels.add(label);
        markDirty();
        return label;
    }

    public void removeLabel(TextLabel label) {
        if (labels.remove(label)) {
            markDirty();
        }
    }

    public void clear() {
        labels.clear();
        markDirty();
    }

    public void markDirty() {
        dirty = true;
    }

    public void draw() {
        if (labels.isEmpty()) return;
        if (dirty) rebuildVertexBuffer();
        if (vertexCount == 0) return;

        font.ensureTexture();
        ensureVbo();
        uploadBuffer();

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        shader.setAsCurrentProgram();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        shader.enableAndPointVertexAttribs();

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, font.getTextureId());
        fsArgs.textureUnit = 0;
        shader.setArgs(vsArgs, fsArgs);
        shader.transferUniformArgsToGPU();

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

        shader.disableVertexAttribs();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    private void rebuildVertexBuffer() {
        int estimatedGlyphs = 0;
        for (TextLabel label : labels) {
            if (label == null || !label.visible || label.text == null) continue;
            estimatedGlyphs += label.text.length();
        }
        int floatsNeeded = estimatedGlyphs * 6 * FLOATS_PER_VERTEX;
        ensureVertexBuffer(floatsNeeded);

        float screenW = Math.max(1f, (float) ScreenInfo.getScreenW());
        float screenH = Math.max(1f, (float) ScreenInfo.getScreenH());

        vertexCount = 0;
        vertexBuffer.clear();

        for (TextLabel label : labels) {
            if (label == null || !label.visible || label.text == null || label.text.isEmpty()) {
                continue;
            }
            float scale = label.scale;
            BitmapFont.TextMetrics metrics = font.measureText(label.text, scale);

            float startX = label.xPx;
            float startY = label.yPx;
            float width = metrics.width;
            float height = metrics.height;

            switch (label.anchor) {
                case TOP_CENTER: startX -= width * 0.5f; break;
                case TOP_RIGHT: startX -= width; break;
                case CENTER_LEFT: startY -= height * 0.5f; break;
                case CENTER: startX -= width * 0.5f; startY -= height * 0.5f; break;
                case CENTER_RIGHT: startX -= width; startY -= height * 0.5f; break;
                case BOTTOM_LEFT: startY -= height; break;
                case BOTTOM_CENTER: startX -= width * 0.5f; startY -= height; break;
                case BOTTOM_RIGHT: startX -= width; startY -= height; break;
                case TOP_LEFT:
                default:
                    break;
            }

            float cursorX = startX;
            float cursorY = startY;
            float baseline = cursorY + font.getBaseline() * scale;
            float lineHeight = font.getLineHeight() * scale;
            float[] rgba = label.color != null ? label.color.rgba : DEFAULT_COLOR.rgba;

            for (int i = 0; i < label.text.length(); i++) {
                char c = label.text.charAt(i);
                if (c == '\n') {
                    cursorX = startX;
                    cursorY += lineHeight;
                    baseline = cursorY + font.getBaseline() * scale;
                    continue;
                }

                BitmapFont.Glyph g = font.getGlyph(c);
                float advance = (g != null ? g.xAdvance : font.getSpaceAdvance()) * scale;
                if (g == null || g.width <= 0 || g.height <= 0) {
                    cursorX += advance;
                    continue;
                }

                float gx = cursorX + g.xOffset * scale;
                float gy = baseline + g.yOffset * scale;
                float gw = g.width * scale;
                float gh = g.height * scale;

                float x0 = gx;
                float y0 = gy;
                float x1 = gx + gw;
                float y1 = gy + gh;

                float ndcX0 = (x0 / screenW) * 2f - 1f;
                float ndcY0 = 1f - (y0 / screenH) * 2f;
                float ndcX1 = (x1 / screenW) * 2f - 1f;
                float ndcY1 = 1f - (y1 / screenH) * 2f;

                float u0 = g.u0;
                float v0 = g.v0;
                float u1 = g.u1;
                float v1 = g.v1;

                putVertex(ndcX0, ndcY0, u0, v0, rgba);
                putVertex(ndcX1, ndcY0, u1, v0, rgba);
                putVertex(ndcX1, ndcY1, u1, v1, rgba);

                putVertex(ndcX0, ndcY0, u0, v0, rgba);
                putVertex(ndcX1, ndcY1, u1, v1, rgba);
                putVertex(ndcX0, ndcY1, u0, v1, rgba);

                cursorX += advance;
            }
        }

        vertexBuffer.position(0);
        dirty = false;
    }

    private void putVertex(float x, float y, float u, float v, float[] rgba) {
        vertexBuffer.put(x).put(y);
        vertexBuffer.put(u).put(v);
        vertexBuffer.put(rgba[0]).put(rgba[1]).put(rgba[2]).put(rgba[3]);
        vertexCount += 1;
    }

    private void ensureVertexBuffer(int floatsNeeded) {
        if (floatsNeeded <= vertexCapacityFloats && vertexBuffer != null) return;
        vertexCapacityFloats = Math.max(floatsNeeded, 256);
        ByteBuffer bb = ByteBuffer.allocateDirect(vertexCapacityFloats * BYTES_PER_FLOAT);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
    }

    private void ensureVbo() {
        if (vboId != 0) return;
        int[] ids = new int[1];
        GLES20.glGenBuffers(1, ids, 0);
        vboId = ids[0];
    }

    private void uploadBuffer() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER,
                vertexCount * FLOATS_PER_VERTEX * BYTES_PER_FLOAT,
                vertexBuffer,
                GLES20.GL_DYNAMIC_DRAW
        );
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        vboId = 0;
        dirty = true;
        font.reloadGPUResourcesRecursivelyOnContextLoss();
        shader.reloadGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        if (vboId != 0) {
            GLES20.glDeleteBuffers(1, new int[]{vboId}, 0);
            vboId = 0;
        }
        font.cleanupGPUResourcesRecursively();
        shader.cleanupGPUResourcesRecursively();
    }
}
