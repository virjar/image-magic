package com.virjar.image.magic;


import com.virjar.image.magic.libs.ImagePHash;
import com.virjar.image.magic.libs.ImageUtils;
import lombok.Getter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 山顶坐标计算
 */
@SuppressWarnings("ALL")
public class ImageHilltopV2 {

    /**
     * 计算图像上的前N个物体
     *
     * @param hilltopParamAndResult 入参封装，包括原图、输入图、物体大小、物体个数
     * @return 前N个坐标点
     */
    public static List<Point> topN(HilltopParamAndResult hilltopParamAndResult) {
        hilltopParamAndResult.width = hilltopParamAndResult.challengeImage.getWidth();
        hilltopParamAndResult.height = hilltopParamAndResult.challengeImage.getHeight();

        if (hilltopParamAndResult.backgroundImage.getWidth() != hilltopParamAndResult.width || hilltopParamAndResult.backgroundImage.getHeight() != hilltopParamAndResult.height) {
            hilltopParamAndResult.backgroundImage = ImageUtils.thumb(hilltopParamAndResult.backgroundImage, hilltopParamAndResult.width, hilltopParamAndResult.height);
        }

        long totalDiff = 0;
        int[][] diff = new int[hilltopParamAndResult.width][hilltopParamAndResult.height];

        long[][] calculateDiff = new long[hilltopParamAndResult.width][hilltopParamAndResult.height];

        for (int i = 0; i < hilltopParamAndResult.width; i++) {
            for (int j = 0; j < hilltopParamAndResult.height; j++) {
                int rgbDiff = ImageUtils.rgbDiff(hilltopParamAndResult.backgroundImage.getRGB(i, j), hilltopParamAndResult.challengeImage.getRGB(i, j));
                diff[i][j] = rgbDiff;
                calculateDiff[i][j] = rgbDiff;
                totalDiff += rgbDiff;
            }
        }
        hilltopParamAndResult.diff = diff;
        hilltopParamAndResult.avgDiff = (int) (totalDiff / (hilltopParamAndResult.width * hilltopParamAndResult.height));

        AggregateMountain aggregateMountain = new AggregateMountain(calculateDiff, hilltopParamAndResult.width,
                hilltopParamAndResult.height, hilltopParamAndResult);
        aggregateMountain.genAggregateMountainMapping();
        aggregateMountain.invalidRectangle(0, 0, hilltopParamAndResult.width - 1, hilltopParamAndResult.height - 1);
        aggregateMountain.saveImage();

        List<Point> ret = new ArrayList<>();

        for (int i = 0; i < hilltopParamAndResult.topN; i++) {
            // 最高的一个点，这个点是基于边长5个像素点判定的
            AggregateMountain.XY topXY = aggregateMountain.fetchTopPoint();
            // topPoint没有考虑斑点大小，都是按照5个像素斑点计算的
            // 所以这个topInt需要根据实际的斑点大小进行二次调整
            topXY = adjustCenterPoint(topXY, aggregateMountain, hilltopParamAndResult);

            Point point = new Point();
            point.x = topXY.x;
            point.y = topXY.y;
            point.weight = topXY.weight;
            point.hilltopParamAndResult = hilltopParamAndResult;
            ret.add(point);

            if (i < hilltopParamAndResult.topN - 1) {
                // 抹除当前点的数据，这样从新扫描将会得到下个断点
                tripAggregateMountain(aggregateMountain, topXY, hilltopParamAndResult);
            }
        }

        return ret;
    }

    private static AggregateMountain.XY adjustCenterPoint(AggregateMountain.XY topXY, AggregateMountain aggregateMountain,
                                                          HilltopParamAndResult hilltopParamAndResult) {

        Rectangle candidatePoints = rectangleRange(topXY.x, topXY.y, hilltopParamAndResult.chSize * 2, hilltopParamAndResult.width, hilltopParamAndResult.height);

        // thumbTimes 缩放倍数，使用开方的方式估算，这样可以减少两个平方的时间复杂度
        int thumbTimes = (int) Math.sqrt(hilltopParamAndResult.chSize);
        // 创建缩略图，进行快速定位
        int shortCurtWith = (hilltopParamAndResult.chSize) / thumbTimes;
        shortCurtWith *= 2;

        long[][] shortCurt = new long[shortCurtWith][shortCurtWith];

        for (int i = 0; i < shortCurtWith; i++) {
            for (int j = 0; j < shortCurtWith; j++) {

                int startX = i * thumbTimes + candidatePoints.leftTopX;
                int startY = j * thumbTimes + candidatePoints.leftTopY;
                int endX = Math.min(startX + thumbTimes - 1, hilltopParamAndResult.width - 1);
                int endY = Math.min(startY + thumbTimes - 1, hilltopParamAndResult.height - 1);

                long totalDiff = 0;
                for (int x = startX; x <= endX; x++) {
                    for (int y = startY; y <= endY; y++) {
                        totalDiff += hilltopParamAndResult.diff[x][y];
                    }
                }
                shortCurt[i][j] = totalDiff;
            }
        }

        saveImage(shortCurt, shortCurtWith, shortCurtWith);

        int shortCurtMontainWith = shortCurtWith / 2;

        AggregateMountain.XY shortCurtxy = new AggregateMountain.XY();
        long[][] shortCurtMountain = new long[shortCurtMontainWith][shortCurtMontainWith];
        for (int i = 0; i < shortCurtMontainWith; i++) {
            for (int j = 0; j < shortCurtMontainWith; j++) {

                int shortCurtCenterX = i + shortCurtMontainWith / 2;
                int shortCurtCenterY = j + shortCurtMontainWith / 2;

                Rectangle aggredateRange = rectangleRange(shortCurtCenterX, shortCurtCenterY,
                        shortCurtMontainWith,
                        shortCurtWith, shortCurtWith);
                double aggretateDiff = 0;

                for (int aggredateRangeI = aggredateRange.leftTopX; aggredateRangeI <= aggredateRange.rightBottomX; aggredateRangeI++) {
                    for (int aggredateRangeJ = aggredateRange.leftTopY; aggredateRangeJ <= aggredateRange.rightBottomY; aggredateRangeJ++) {

                        long base = shortCurt[aggredateRangeI][aggredateRangeJ];
                        double distance = Math.sqrt((aggredateRangeI - shortCurtCenterX) * (aggredateRangeI - shortCurtCenterX) + (aggredateRangeJ - shortCurtCenterY) * (aggredateRangeJ - shortCurtCenterY));

                        double distanceRatio = distance / (sqrt2 * (shortCurtMontainWith / 2));
                        if (distanceRatio > 1) {
                            continue;
                        }
                        double ratio = (Math.cos(Math.PI * distanceRatio) + 1) / 2;
                        aggretateDiff += base * base * base * ratio;
                    }
                }
                shortCurtMountain[i][j] = (long) aggretateDiff;
                // System.out.println("shortCurtMountain:(" + i + "," + j + ") = " + shortCurtMountain[i][j]);
                shortCurtxy.update(shortCurtCenterX, shortCurtCenterY, (long) aggretateDiff);
            }
        }

        saveImage(shortCurtMountain, shortCurtMontainWith, shortCurtMontainWith);

        // 在缩略图里面寻找最高点，之后再回放到原图进行

        int realCandidateStartX = shortCurtxy.x * thumbTimes + candidatePoints.leftTopX;
        int realCandidateEndX = shortCurtxy.x * thumbTimes + thumbTimes + candidatePoints.leftTopX;
        int realCandidateStartY = shortCurtxy.y * thumbTimes + candidatePoints.leftTopY;
        int realCandidateEndY = shortCurtxy.y * thumbTimes + thumbTimes + candidatePoints.leftTopY;


        AggregateMountain.XY xy = new AggregateMountain.XY();
        for (int candidateI = realCandidateStartX; candidateI <= realCandidateEndX; candidateI++) {
            for (int candidateJ = realCandidateStartY; candidateJ <= realCandidateEndY; candidateJ++) {
                Rectangle aggredateRange = rectangleRange(candidateI, candidateJ, hilltopParamAndResult.chSize, hilltopParamAndResult.width, hilltopParamAndResult.height);

                double aggretateDiff = 0;
                for (int i = aggredateRange.leftTopX; i <= aggredateRange.rightBottomX; i++) {
                    for (int j = aggredateRange.leftTopY; j <= aggredateRange.rightBottomY; j++) {
                        double distance = Math.sqrt((i - candidateI) * (i - candidateI) + (j - candidateJ) * (j - candidateJ));

                        double distanceRatio = distance / (sqrt2 * (hilltopParamAndResult.chSize / 2));
                        if (distanceRatio > 1) {
                            continue;
                        }
                        double ratio = (Math.cos(Math.PI * distanceRatio) + 1) / 2;
                        aggretateDiff += aggregateMountain.diffData[i][j] * ratio;
                    }
                }
                xy.update(candidateI, candidateJ, (long) aggretateDiff);
            }
        }
        return xy;
    }

    private static void tripAggregateMountain(AggregateMountain aggregateMountain, AggregateMountain.XY topXY,
                                              HilltopParamAndResult hilltopParamAndResult) {
        int stripStartX = Math.max(topXY.x - hilltopParamAndResult.chSize / 2, 0);
        int stripEndX = Math.min(topXY.x + hilltopParamAndResult.chSize / 2, hilltopParamAndResult.width - 1);
        int stripStartY = Math.max(topXY.y - hilltopParamAndResult.chSize / 2, 0);
        int stripEndY = Math.min(topXY.y + hilltopParamAndResult.chSize / 2, hilltopParamAndResult.height - 1);

        long maxDiff = 0;
        for (int i = stripStartX; i <= stripEndX; i++) {
            for (int j = stripStartY; j <= stripEndY; j++) {
                if (aggregateMountain.diffData[i][j] > maxDiff) {
                    maxDiff = aggregateMountain.diffData[i][j];
                }
            }
        }

        for (int i = stripStartX; i <= stripEndX; i++) {
            for (int j = stripStartY; j <= stripEndY; j++) {
                double distance = Math.sqrt((i - topXY.x) * (i - topXY.x) + (j - topXY.y) * (j - topXY.y));

                double distanceRatio = distance / hilltopParamAndResult.chSize;
                if (distanceRatio > 1) {
                    continue;
                }
                // y = 1- x*x / 2.25 权值衰减函数，为2次函数，要求命中坐标: (0,1) (1.5,0)
                // 当距离为0的时候，衰减权重为1，当距离为1.5的时候，衰减权重为0
                // 当距离为1的时候， 衰减权重为：1- 1/2.25 = 0.55
                aggregateMountain.diffData[i][j] -= maxDiff * (1 - distanceRatio * distanceRatio / 2.25);
                if (aggregateMountain.diffData[i][j] < 0) {
                    aggregateMountain.diffData[i][j] = 0;
                }
            }
        }

        saveImage(aggregateMountain.diffData, aggregateMountain.width, aggregateMountain.height);
        aggregateMountain.invalidRectangle(stripStartX, stripStartY, stripEndX, stripEndY);
    }

    private static class AggregateMountain {
        private long[][] diffData;
        private int width;
        private int height;
        private HilltopParamAndResult hilltopParamAndResult;

        private AggregateMountain nextAggregateMountain = null;
        private AggregateMountain preAggregateMountain = null;
        private boolean isLast = false;

        private static class XY {
            private int x;
            private int y;

            private long weight = 0;

            public void update(int x, int y, long weight) {
                if (weight > this.weight) {
                    this.x = x;
                    this.y = y;
                    this.weight = weight;
                }
            }

        }


        public XY fetchTopPoint() {
            if (isLast) {
                XY xy = new XY();
                for (int i = 0; i < width; i++) {
                    for (int j = 0; j < height; j++) {
                        xy.update(i, j, diffData[i][j]);
                    }
                }
                return xy;
            }

            XY nextXy = nextAggregateMountain.fetchTopPoint();
            int startX = nextXy.x * 5;
            int endX = Math.min(nextXy.x * 5 + 4, width - 1);
            int startY = nextXy.y * 5;
            int endY = Math.min(nextXy.y * 5 + 4, height - 1);

            XY xy = new XY();
            for (int i = startX; i <= endX; i++) {
                for (int j = startY; j <= endY; j++) {
                    xy.update(i, j, diffData[i][j]);
                }
            }
            return xy;
        }

        public AggregateMountain(long[][] diffData, int width, int height, HilltopParamAndResult hilltopParamAndResult) {
            this.diffData = diffData;
            this.width = width;
            this.height = height;
            this.hilltopParamAndResult = hilltopParamAndResult;
        }

        private void saveImage() {
            ImageHilltopV2.saveImage(diffData, width, height);
        }


        private void invalidRectangle(int leftTopX, int leftTopY, int rightBottomX, int rightBottomY) {
            if (isLast) {
                saveImage();
                return;
            }
            int nextDiffDataInvalidStartX = leftTopX / 5;
            int nextDiffDataInvalidStartY = leftTopY / 5;

            int nextDiffDataInvalidEndX = (rightBottomX + 4) / 5;
            int nextDiffDataInvalidEndY = (rightBottomY + 4) / 5;

            if (leftTopX % 5 != 0) {
                nextDiffDataInvalidStartX = Math.max(nextDiffDataInvalidStartX - 1, 0);
            }

            if (leftTopY % 5 != 0) {
                nextDiffDataInvalidStartY = Math.max(nextDiffDataInvalidStartY - 1, 0);
            }

            if (rightBottomX % 5 != 0) {
                nextDiffDataInvalidEndX = Math.min(nextDiffDataInvalidEndX + 1, nextAggregateMountain.width - 1);
            }

            if (rightBottomY % 5 != 0) {
                nextDiffDataInvalidEndY = Math.min(nextDiffDataInvalidEndY + 1, nextAggregateMountain.height - 1);
            }
            // fill in next diff data
            for (int i = nextDiffDataInvalidStartX; i <= nextDiffDataInvalidEndX; i++) {
                for (int j = nextDiffDataInvalidStartY; j <= nextDiffDataInvalidEndY; j++) {
                    int scanStartX = i * 5;
                    int scanStartY = j * 5;

                    int scanEndX = Math.min(scanStartX + 4, width - 1);
                    int scanEndY = Math.min(scanStartY + 4, height - 1);
                    int centerX = (scanStartX + scanEndX) / 2;
                    int centerY = (scanStartY + scanEndY) / 2;


                    // long base = diffData[centerX][centerY];

                    long aggretateDiff = 0;
                    for (int nextI = scanStartX; nextI <= scanEndX; nextI++) {
                        for (int nextJ = scanStartY; nextJ <= scanEndY; nextJ++) {
                            aggretateDiff += diffData[nextI][nextJ];
                        }
                    }
                    nextAggregateMountain.diffData[i][j] = aggretateDiff;
                }
            }

            nextAggregateMountain.invalidRectangle(nextDiffDataInvalidStartX, nextDiffDataInvalidStartY, nextDiffDataInvalidEndX, nextDiffDataInvalidEndY);
            saveImage();
        }

        private void genAggregateMountainMapping() {
            if (width < 5 || height < 5) {
                isLast = true;
                return;
            }

            int nextDiffDataWith = (width + 4) / 5;
            int nextDiffDataHeight = (height + 4) / 5;
            long nextDiffData[][] = new long[nextDiffDataWith][nextDiffDataHeight];


            nextAggregateMountain = new AggregateMountain(nextDiffData, nextDiffDataWith, nextDiffDataHeight, hilltopParamAndResult);
            nextAggregateMountain.preAggregateMountain = this;
            nextAggregateMountain.genAggregateMountainMapping();

        }
    }

    private static final double sqrt2 = Math.sqrt(2);
    private static final double sqrt2MULTI2_5 = sqrt2 * 2.5;


    @Getter
    public static class Point {
        private int x;
        private int y;

        /**
         * 权重，越高代表识别越精准
         */
        private long weight;


        private HilltopParamAndResult hilltopParamAndResult;

        /**
         * 获取裁剪图
         *
         * @param backgroundColor 可以指定背景颜色 如 白色：0xFFFFFFFF  黑色：0x00000000
         * @return 在挑战图中的裁剪小图
         */
        public BufferedImage generatedSlice(int backgroundColor) {
            return ImageHilltopV2.generatedSlice(this, backgroundColor);
        }

    }


    private static BufferedImage generatedSlice(Point point, int backgroundColor) {
        int x = point.getX();
        int y = point.getY();
        HilltopParamAndResult hilltopParamAndResult = point.getHilltopParamAndResult();
        int chSize = hilltopParamAndResult.getChSize();

        Rectangle rectangle = rectangleRange(x, y, hilltopParamAndResult.getChSize(),
                hilltopParamAndResult.width, hilltopParamAndResult.height
        );


        int[][] diff = point.getHilltopParamAndResult().getDiff();
        // 当前图片上的最大diff
        long totalDiff = 0;
        int maxDiff = 0;
        for (int i = 0; i < chSize; i++) {
            for (int j = 0; j < chSize; j++) {
                int nowDiff = diff[rectangle.leftTopX + i][rectangle.leftTopY + j];
                totalDiff += nowDiff;
                if (nowDiff > maxDiff) {
                    maxDiff = nowDiff;
                }
            }
        }

        int avgDiff = (int) (totalDiff / (chSize * chSize));

        BufferedImage bufferedImage = new BufferedImage(chSize, chSize, point.getHilltopParamAndResult().getChallengeImage().getType());
        for (int i = 0; i < chSize; i++) {
            for (int j = 0; j < chSize; j++) {
                int rgb = point.getHilltopParamAndResult().getChallengeImage().getRGB(rectangle.leftTopX + i, rectangle.leftTopY + j);
                int pointDiff = diff[rectangle.leftTopX + i][rectangle.leftTopY + j];
                // 由于字母的背景是白色，所以这里，我们直接把背景颜色转化为白色
                if (pointDiff < (avgDiff * 0.1)) {
                    bufferedImage.setRGB(i, j, backgroundColor);
                } else if (pointDiff >= avgDiff) {
                    bufferedImage.setRGB(i, j, rgb);
                } else {
                    // 差异比较大的时候，我们按照比例进行颜色叠加
                    double whiteRatio = ((double) pointDiff) / avgDiff;
                    // y= (x-1) * (x-1)
                    whiteRatio = (whiteRatio - 1) * (whiteRatio - 1);
                    int mergedRGB = ImageUtils.maskMerge(backgroundColor, rgb, whiteRatio);
                    bufferedImage.setRGB(i, j, mergedRGB);
                }
            }
        }
        return bufferedImage;
    }


    @Getter
    public static class HilltopParamAndResult {

        public HilltopParamAndResult(BufferedImage backgroundImage, BufferedImage challengeImage, int chSize, int topN) {
            this.backgroundImage = backgroundImage;
            this.challengeImage = challengeImage;
            this.chSize = chSize;
            this.topN = topN;
        }

        /**
         * 背景原图
         */
        private BufferedImage backgroundImage;

        /**
         * 输入的挑战图
         */
        private BufferedImage challengeImage;


        /**
         * 斑点大小
         */
        private int chSize;


        /**
         * 待计算的斑点数量
         */
        private int topN;


        ///// 以下为输出的信息
        /**
         * 合并图像的宽
         */
        private int width;
        /**
         * 合并图像的高
         */
        private int height;


        /**
         * 图像diff数据
         */
        private int[][] diff;

        /**
         * 整张图的平均diff
         */
        private int avgDiff;

    }

    private static class Rectangle {
        private int leftTopX;
        private int leftTopY;
        private int rightBottomX;
        private int rightBottomY;
    }

    private static Rectangle rectangleRange(int centerX, int centerY, int sliceSize, int totalWidth, int totalHeight) {
        int leftTopX = centerX - sliceSize / 2;
        int leftTopY = centerY - sliceSize / 2;
        int rightBottomX = centerX + sliceSize / 2;
        int rightBottomY = centerY + sliceSize / 2;

        if (leftTopX < 0) {
            leftTopX = 0;
        }
        if (leftTopY < 0) {
            leftTopY = 0;
        }
        if (rightBottomX >= totalWidth) {
            rightBottomX = totalWidth - 1;
        }
        if (rightBottomY >= totalHeight) {
            rightBottomY = totalHeight - 1;
        }
        Rectangle rectangle = new Rectangle();
        rectangle.leftTopX = leftTopX;
        rectangle.leftTopY = leftTopY;
        rectangle.rightBottomX = rightBottomX;
        rectangle.rightBottomY = rightBottomY;

        return rectangle;
    }

    private static boolean saveImageFlag = false;
    private static int saveImageIndex = 1;

    private static void saveImage(long[][] diffData, int width, int height) {
        if (!saveImageFlag) {
            return;
        }
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        long maxDiff = 0;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (maxDiff < diffData[i][j]) {
                    maxDiff = diffData[i][j];
                }
            }
        }
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                int rgb = (int) (diffData[i][j] * 255 / maxDiff);
                int rgbGray = rgb << 24 | rgb << 16 | rgb << 8 | rgb;
                bufferedImage.setRGB(i, j, rgbGray);
            }
        }
        ImagePHash imagePHash = new ImagePHash();
        String hash = imagePHash.getHash(bufferedImage);
        File file = new File("assets/test/" + (saveImageIndex++) + "_" + hash + ".jpg");
        try {
            ImageIO.write(bufferedImage, "jpg", file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
