package com.xddcodec.fs.framework.common.utils;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * 文件工具类
 *
 * @Author: xddcode
 * @Date: 2024/6/7 11:12
 */
public class FileUtils {

    private FileUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static String getContentType(String filenameExtension) {
        if (filenameExtension.equalsIgnoreCase(".pdf")) {
            return "application/pdf";
        }
        if (filenameExtension.equalsIgnoreCase(".bmp")) {
            return "image/bmp";
        }
        if (filenameExtension.equalsIgnoreCase(".gif")) {
            return "image/gif";
        }
        if (filenameExtension.equalsIgnoreCase(".jpeg") ||
                filenameExtension.equalsIgnoreCase(".jpg") ||
                filenameExtension.equalsIgnoreCase(".png")) {
            return "image/jpg";
        }
        if (filenameExtension.equalsIgnoreCase(".html")) {
            return "text/html";
        }
        if (filenameExtension.equalsIgnoreCase(".txt")) {
            return "text/plain";
        }
        if (filenameExtension.equalsIgnoreCase(".vsd")) {
            return "application/vnd.visio";
        }
        if (filenameExtension.equalsIgnoreCase(".pptx") ||
                filenameExtension.equalsIgnoreCase(".ppt")) {
            return "application/vnd.ms-powerpoint";
        }
        if (filenameExtension.equalsIgnoreCase(".docx")) {
            return "application/msword";
        }
        if (filenameExtension.equalsIgnoreCase(".xml")) {
            return "text/xml";
        }
        return "image/jpg";
    }


    /**
     * 获取文件后缀名
     *
     * @param fileName
     * @return
     */
    public static String getSuffix(String fileName) {
        return FileUtil.getSuffix(fileName);
    }


    /**
     * 获取文件扩展名
     *
     * @param fileName 文件名
     * @return
     */
    public static String extName(String fileName) {
        return FileUtil.extName(fileName);
    }

    /**
     * 将字节数格式化为便于阅读的文件大小。
     *
     * <p>使用 1024 进制，自动在 B、KB、MB、GB、TB 和 PB 之间转换，
     * 与前端文件列表的显示规则保持一致。</p>
     *
     * @param bytes 字节数
     * @return 格式化后的文件大小；空值或非正数返回 {@code 0 B}
     */
    public static String formatFileSize(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "0 B";
        }

        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB", "PB"};
        int unitIndex = 0;
        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024;
            unitIndex++;
        }
        return String.format(Locale.ROOT, "%.2f %s", value, units[unitIndex]);
    }

    /**
     * 生成对象键
     * 格式: {projectName}/{userId}/{yyyyMMdd}/{fileId}.{suffix}
     * 示例: free-fs/user001/20241226/abc123.pdf
     *
     * @param prefix     前缀
     * @param userId     用户ID
     * @param objectName 对象名称
     * @return
     */
    public static String generateObjectKey(String prefix, String userId, String objectName) {
        StringBuilder objectKey = new StringBuilder();

        objectKey.append(prefix).append("/");

        if (StrUtil.isNotBlank(userId)) {
            objectKey.append(userId).append("/");
        } else {
            objectKey.append("anonymous/");  // 匿名用户
        }

        String dateDir = DateUtil.format(new java.util.Date(), "yyyyMMdd");
        objectKey.append(dateDir).append("/");

        objectKey.append(objectName);
        return objectKey.toString();
    }

    public static void downLoad(String url, String path, HttpServletResponse response) {
        InputStream in = null;
        try {
            URL httpUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) httpUrl.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(100000);
            conn.setReadTimeout(200000);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.connect();
            in = conn.getInputStream();
            byte[] bs = new byte[1024];
            int len = 0;
            response.reset();
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Cache-Control", "no-cache");
            response.setContentType("application/octet-stream");
            String fileName = url.replaceAll(path + "/", "");
            response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));
            ServletOutputStream out = response.getOutputStream();
            while ((len = in.read(bs)) != -1) {
                out.write(bs, 0, len);
            }
            out.flush();
            out.close();
        } catch (Exception e) {
            throw new RuntimeException(url + "下载失败");
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
