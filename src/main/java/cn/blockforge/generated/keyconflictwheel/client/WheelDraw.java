package cn.blockforge.generated.keyconflictwheel.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * 圆盘扇区绘制工具：圆环扇形（TRIANGLE_STRIP）与线段（QUADS），位置颜色着色器。
 */
public final class WheelDraw {
    private WheelDraw() {}

    /** 画一段圆环扇形：半径 [r0,r1]，角度 [a0,a1)（弧度，屏幕坐标系，y 向下） */
    public static void ringSector(Matrix4f pose, float cx, float cy, float r0, float r1, float a0, float a1, int argb) {
        if (r1 < r0) {
            float t = r0; r0 = r1; r1 = t;
        }
        int steps = Math.max(2, (int) Math.ceil((a1 - a0) * Math.max(r1, 6.0f) / 2.5f));
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= steps; i++) {
            float t = a0 + (a1 - a0) * i / steps;
            float cos = (float) Math.cos(t);
            float sin = (float) Math.sin(t);
            buf.addVertex(pose, cx + r1 * cos, cy + r1 * sin, 0.0F).setColor(argb);
            buf.addVertex(pose, cx + r0 * cos, cy + r0 * sin, 0.0F).setColor(argb);
        }
        draw(buf);
    }

    /** 画一条线段（带宽度） */
    public static void line(Matrix4f pose, float x1, float y1, float x2, float y2, float width, int argb) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1.0E-4F) return;
        float nx = -dy / len * width * 0.5F;
        float ny = dx / len * width * 0.5F;
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.addVertex(pose, x1 + nx, y1 + ny, 0.0F).setColor(argb);
        buf.addVertex(pose, x2 + nx, y2 + ny, 0.0F).setColor(argb);
        buf.addVertex(pose, x2 - nx, y2 - ny, 0.0F).setColor(argb);
        buf.addVertex(pose, x1 - nx, y1 - ny, 0.0F).setColor(argb);
        draw(buf);
    }

    private static void draw(BufferBuilder buf) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferUploader.drawWithShader(buf.build());
        RenderSystem.disableBlend();
    }
}
