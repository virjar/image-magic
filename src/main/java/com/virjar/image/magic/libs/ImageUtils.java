package com.virjar.image.magic.libs;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;

public class ImageUtils {

    /**
     * 计算两张图片的差异
     *
     * @param rgbLeft  像素1
     * @param rgbRight 像素2
     * @return 差异
     */
    public static int rgbDiff(int rgbLeft, int rgbRight) {
        int redLeft = rgbLeft >> 16 & 255;
        int greenLeft = rgbLeft >> 8 & 255;
        int blueLeft = rgbLeft & 255;

        int redLRight = rgbRight >> 16 & 255;
        int greenRight = rgbRight >> 8 & 255;
        int blueRight = rgbRight & 255;
        return Math.abs(redLeft - redLRight)
                + Math.abs(greenLeft - greenRight)
                + Math.abs(blueLeft - blueRight);
    }

    /**
     * 透明度叠加，两个图像根据指定比例叠加
     *
     * @param rgbLeft   像素1
     * @param rgbRight  像素2
     * @param leftRatio 像素1在合并结果占有的比例
     * @return 输出的像素
     */
    public static int maskMerge(int rgbLeft, int rgbRight, double leftRatio) {
        if (leftRatio < 0 || leftRatio > 1) {
            throw new IllegalStateException("error leftRatio: " + leftRatio);
        }
        int r = (int) (((rgbLeft >>> 24) & 0xff) * leftRatio
                + ((rgbRight >>> 24) & 0xff) * (1 - leftRatio));

        int g = (int) (((rgbLeft >>> 16) & 0xff) * leftRatio
                + ((rgbRight >>> 16) & 0xff) * (1 - leftRatio));

        int b = (int) (((rgbLeft >>> 8) & 0xff) * leftRatio
                + ((rgbRight >>> 8) & 0xff) * (1 - leftRatio));

        int p = (int) (((rgbLeft) & 0xff) * leftRatio
                + ((rgbRight) & 0xff) * (1 - leftRatio));

        return (r << 24) | (g << 16) | (b << 8) | p;
    }

    /**
     * 将目标图像缩放到指定大小
     *
     * @param source 输入图像
     * @param width  目标图像宽度
     * @param height 目标图像高度
     * @return 缩放后的图像
     */
    public static BufferedImage thumb(BufferedImage source, int width, int height) {
        if (width == source.getWidth()
                && height == source.getHeight()) {
            return source;
        }
        int type = source.getType();
        BufferedImage target;
        double sx = (double) width / (double) source.getWidth();
        double sy = (double) height / (double) source.getHeight();

        if (type == 0) {
            ColorModel g = source.getColorModel();
            WritableRaster raster = g.createCompatibleWritableRaster(width, height);
            boolean alphaPremultiplied = g.isAlphaPremultiplied();
            target = new BufferedImage(g, raster, alphaPremultiplied, null);
        } else {
            target = new BufferedImage(width, height, type);
        }

        Graphics2D g1 = target.createGraphics();
        g1.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g1.drawRenderedImage(source, AffineTransform.getScaleInstance(sx, sy));
        g1.dispose();
        return target;
    }
}
