package com.virjar.image.magic;

import com.virjar.image.magic.libs.ImageHistogram;
import com.virjar.image.magic.libs.ImagePHash;
import lombok.Getter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class SimilarImageSearcher {
    private static final ImagePHash imagePHash = new ImagePHash();

    private final ArrayList<ImageSample> imageSamples = new ArrayList<>();

    public void addSample(URL url) throws IOException {
        addSample(url, null);
    }

    public void addSample(URL url, String tag) throws IOException {
        imageSamples.add(new ImageSample(ImageIO.read(url), tag));
    }

    public ImageSample findSimilarImage(BufferedImage bufferedImage, String imageHash) {
        if (imageHash == null || imageHash.trim().isEmpty()) {
            imageHash = imagePHash.getHash(bufferedImage);
        }
        List<ImageSample> similarTasks = new ArrayList<>();
        int nowDistance = Integer.MAX_VALUE;
        ImageSample imageSample = null;

        for (ImageSample testSample : imageSamples) {
            int distance = ImagePHash.distance(imageHash, testSample.imgHash);
            if (distance < nowDistance) {
                imageSample = testSample;
                nowDistance = distance;
            }
            if (distance < 20) {
                similarTasks.add(imageSample);
            }
        }

        if (nowDistance <= 6 || similarTasks.size() <= 1) {
            return imageSample;
        }
        // 当距离大于6的时候，证明可能存在误差。这个时候再走一次直方图

        double nowScore = 0;
        for (ImageSample testSample : similarTasks) {
            double score = testSample.imageHistogram.match(bufferedImage);
            if (score > nowScore) {
                nowScore = score;
                imageSample = testSample;
            }
        }

        return imageSample;
    }


    public static class ImageSample {

        @Getter
        private final String imgHash;
        @Getter
        private final BufferedImage image;
        private final ImageHistogram imageHistogram;
        @Getter
        private final String tag;

        ImageSample(BufferedImage image, String tag) {
            this.image = image;
            this.imgHash = imagePHash.getHash(image);
            this.imageHistogram = new ImageHistogram(image);
            if (tag == null) {
                this.tag = imgHash;
            } else {
                this.tag = tag;
            }
        }
    }
}
