package com.virjar.image.magic.libs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * @desc 相似图片识别（直方图）
 */
public class ImageHistogram {

    private final int redBins;
    private final int greenBins;
    private final int blueBins;

    private final float[] baseData;

    public ImageHistogram(BufferedImage base) {
        redBins = greenBins = blueBins = 4;
        this.baseData = filter(base);
    }

    private float[] filter(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();

        int[] inPixels = new int[width * height];
        float[] histogramData = new float[redBins * greenBins * blueBins];
        getRGB(src, 0, 0, width, height, inPixels);
        int index = 0;
        int redIdx = 0, greenIdx = 0, blueIdx = 0;
        int singleIndex = 0;
        float total = 0;
        for (int row = 0; row < height; row++) {
            int tr = 0, tg = 0, tb = 0;
            for (int col = 0; col < width; col++) {
                index = row * width + col;
                tr = (inPixels[index] >> 16) & 0xff;
                tg = (inPixels[index] >> 8) & 0xff;
                tb = inPixels[index] & 0xff;
                redIdx = (int) getBinIndex(redBins, tr);
                greenIdx = (int) getBinIndex(greenBins, tg);
                blueIdx = (int) getBinIndex(blueBins, tb);
                singleIndex = redIdx + greenIdx * redBins + blueIdx * redBins * greenBins;
                histogramData[singleIndex] += 1;
                total += 1;
            }
        }

        // start to normalize the histogram data
        for (int i = 0; i < histogramData.length; i++) {
            histogramData[i] = histogramData[i] / total;
        }

        return histogramData;
    }

    private float getBinIndex(int binCount, int color) {
        float binIndex = (((float) color) / ((float) 255)) * ((float) binCount);
        if (binIndex >= binCount)
            binIndex = binCount - 1;
        return binIndex;
    }

    private int[] getRGB(BufferedImage image, int x, int y, int width, int height, int[] pixels) {
        int type = image.getType();
        if (type == BufferedImage.TYPE_INT_ARGB || type == BufferedImage.TYPE_INT_RGB)
            return (int[]) image.getRaster().getDataElements(x, y, width, height, pixels);
        return image.getRGB(x, y, width, height, pixels, 0, width);
    }

    /**
     * Bhattacharyya Coefficient
     * http://www.cse.yorku.ca/~kosta/CompVis_Notes/bhattacharyya.pdf
     *
     * @return 返回值大于等于0.8可以简单判断这两张图片内容一致
     * @throws IOException
     */
    public double match(File srcFile, File canFile) throws IOException {
        float[] sourceData = this.filter(ImageIO.read(srcFile));
        float[] candidateData = this.filter(ImageIO.read(canFile));
        return calcSimilarity(sourceData, candidateData);
    }

    /**
     * @return 返回值大于等于0.8可以简单判断这两张图片内容一致
     * @throws IOException
     */
    public double match(URL srcUrl, URL canUrl) throws IOException {
        float[] sourceData = this.filter(ImageIO.read(srcUrl));
        float[] candidateData = this.filter(ImageIO.read(canUrl));
        return calcSimilarity(sourceData, candidateData);
    }

    /**
     * Bhattacharyya Coefficient
     * http://www.cse.yorku.ca/~kosta/CompVis_Notes/bhattacharyya.pdf
     *
     * @return 返回值大于等于0.8可以简单判断这两张图片内容一致
     * @throws IOException
     */
    public double match(BufferedImage target) {
        float[] candidateData = this.filter(target);
        return calcSimilarity(baseData, candidateData);
    }

    private double calcSimilarity(float[] sourceData, float[] candidateData) {
        double[] mixedData = new double[sourceData.length];
        for (int i = 0; i < sourceData.length; i++) {
            mixedData[i] = Math.sqrt(sourceData[i] * candidateData[i]);
        }

        // The values of Bhattacharyya Coefficient ranges from 0 to 1,
        double similarity = 0;
        for (int i = 0; i < mixedData.length; i++) {
            similarity += mixedData[i];
        }

        // The degree of similarity
        return similarity;
    }

}
