package com.virjar.image.magic;

import com.virjar.image.magic.libs.ImageHistogram;
import com.virjar.image.magic.libs.ImagePHash;
import lombok.Getter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片分类，计算为多个相似图
 */
public class ImageCategory {

    /**
     * 对图片进行分类
     *
     * @param sourceDir 原图片内容
     * @param outDir    目标图片内容
     * @throws IOException
     */
    public static void doCategory(File sourceDir, File outDir) throws IOException {
        if (!outDir.exists()) {
            if (!outDir.mkdirs()) {
                throw new IOException("can not create dir: " + outDir.getAbsolutePath());
            }
        }
        List<ImageGroup> imageGroups = doCategory(sourceDir);
        for (ImageGroup imageGroup : imageGroups) {
            File groupDir = new File(outDir, imageGroup.baseFileHash);
            if (!groupDir.exists()) {
                groupDir.mkdirs();
            }
            for (File file : imageGroup.files) {
                File target = new File(groupDir, file.getName());
                if (!file.renameTo(target)) {
                    // 不在一个盘符下，通过copy流的方式
                    FileOutputStream fileOutputStream = new FileOutputStream(target);
                    FileInputStream fileInputStream = new FileInputStream(file);
                    byte[] buf = new byte[1024];
                    int readCount;
                    while ((readCount = fileInputStream.read(buf)) > 0) {
                        fileOutputStream.write(buf, 0, readCount);
                    }
                    fileInputStream.close();
                    fileOutputStream.close();
                    file.delete();
                }
            }

        }
    }


    /**
     * 对图片进行分类
     *
     * @param sourceDir 原图片内容
     * @return 分类后的模型对象
     */
    public static List<ImageGroup> doCategory(File sourceDir) {
        List<ImageGroup> imageGroups = new ArrayList<>();
        File[] imageFiles = sourceDir.listFiles(File::isFile);
        if (imageFiles == null) {
            System.out.println("can not scan file from: " + sourceDir.getAbsolutePath());
            return imageGroups;
        }

        for (File file : imageFiles) {
            BufferedImage bufferedImage;
            try {
                bufferedImage = ImageIO.read(file);
            } catch (Exception e) {
                //ignore
                continue;
            }

            boolean found = false;
            for (ImageGroup imageGroup : imageGroups) {
                if (imageGroup.isSimilar(bufferedImage)) {
                    imageGroup.addFile(file);
                    found = true;
                    break;
                }
            }

            if (!found) {
                imageGroups.add(new ImageGroup(file, bufferedImage));
            }
        }
        return imageGroups;
    }

    private static final ImagePHash sImagePHash = new ImagePHash();

    @Getter
    private static class ImageGroup {
        private final File file;
        private String baseFileHash;
        private final String imagePHash;
        private final BufferedImage bufferedImage;
        private final ImageHistogram imageHistogram;

        private final List<File> files = new ArrayList<>();


        public ImageGroup(File file, BufferedImage bufferedImage) {
            this.file = file;
            this.bufferedImage = bufferedImage;
            this.imagePHash = sImagePHash.getHash(bufferedImage);
            imageHistogram = new ImageHistogram(bufferedImage);
            baseFileHash = fileHash(file);
            addFile(file);
        }

        private void addFile(File file) {
            files.add(file);
        }


        private boolean isSimilar(BufferedImage bufferedImage) {
            int distance = ImagePHash.distance(sImagePHash.getHash(bufferedImage), null);
            if (distance <= 6) {
                return true;
            }
            if (distance > 20) {
                return false;
            }
            double match = imageHistogram.match(bufferedImage);
            return match > 0.8;
        }
    }

    private static String fileHash(File file) {
        try {
            byte[] buffer = new byte[1024];
            FileInputStream fileInputStream = new FileInputStream(file);
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            int numRead;
            while ((numRead = fileInputStream.read(buffer)) > 0) {
                md5.update(buffer, 0, numRead);
            }
            fileInputStream.close();
            byte[] digest = md5.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b1 : digest) {
                sb.append(hexChar[((b1 & 0xF0) >>> 4)]);
                sb.append(hexChar[(b1 & 0xF)]);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static char[] hexChar = {'0', '1', '2', '3',
            '4', '5', '6', '7',
            '8', '9', 'a', 'b',
            'c', 'd', 'e', 'f'};
}
