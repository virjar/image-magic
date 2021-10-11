package com.virjar.image.magic;

import com.virjar.image.magic.libs.ImageUtils;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * 相似图合并，求相似图的最真图
 */
public class ImageAvgMerger {

    private static class RGBP {
        long r = 0;
        long g = 0;
        long b = 0;
        long p = 0;

        int totalRecord = 0;

        void setRGBP(int rgbp) {
            totalRecord++;
            r += (rgbp >>> 24 & 0xff);
            g += (rgbp >>> 16 & 0xff);
            b += (rgbp >>> 8 & 0xff);
            p += (rgbp & 0xff);
        }

        int avgRGB() {
            return shift(r, 24, totalRecord)
                    | shift(g, 16, totalRecord)
                    | shift(b, 8, totalRecord)
                    | shift(p, 0, totalRecord);
        }


        private int shift(long val, int shiftSize, int totalRecord) {
            return ((int) (val / totalRecord)) << shiftSize;
        }
    }


    public static BufferedImage avg(List<BufferedImage> input) {
        if (input.size() == 0) {
            throw new IllegalStateException("input image can not be empty!!");
        }
        long widthTotal = 0, heightTotal = 0;
        for (BufferedImage bufferedImage : input) {
            widthTotal += bufferedImage.getWidth();
            heightTotal += bufferedImage.getHeight();
        }
        int width = (int) (widthTotal / input.size());
        int height = (int) (heightTotal / input.size());

        RGBP[][] points = new RGBP[width][height];
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                points[i][j] = new RGBP();
            }
        }

        // 大规模计算的时候发现，BuffedImage内部有比较大的计算量,所以这里直接变成数组
        ArrayList<int[][]> thumbedImages = new ArrayList<>();

        for (BufferedImage bufferedImage : input) {
            if (bufferedImage.getWidth() != width
                    || bufferedImage.getHeight() != height
            ) {
                bufferedImage = ImageUtils.thumb(bufferedImage, width, height);
            }
            int[][] imgData = new int[width][height];
            thumbedImages.add(imgData);
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    int rgb = bufferedImage.getRGB(i, j);
                    imgData[i][j] = rgb;
                    points[i][j].setRGBP(rgb);
                }
            }
        }

        int[][] firstAvgImg = new int[width][height];

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                firstAvgImg[i][j] = points[i][j].avgRGB();
            }
        }

        BufferedImage out = new BufferedImage(width, height, input.get(0).getType());
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                TreeMap<Long, Integer> topPoint = new TreeMap<>();
                int index = 0;
                for (int[][] bufferedImage : thumbedImages) {
                    int rgbDiff = ImageUtils.rgbDiff(bufferedImage[i][j],
                            firstAvgImg[i][j]);
                    topPoint.put((((long) rgbDiff) << 32) + index, bufferedImage[i][j]);
                    index++;
                }
                // 只保留 3/4的图像，去除尾部，认为尾部为差异内容
                int avgPointSize = (int) (thumbedImages.size() * 0.85);
                RGBP rgbp = new RGBP();
                int avgPointIndex = 0;
                for (long key : topPoint.keySet()) {
                    rgbp.setRGBP(topPoint.get(key));
                    avgPointIndex++;
                    if (avgPointIndex >= avgPointSize) {
                        break;
                    }
                }
                out.setRGB(i, j, rgbp.avgRGB());
            }
        }
        return out;
    }

}
