package com.xddcodec.fs.framework.common.enums;

import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件类型枚举（融合预览功能）
 *
 * @Author: xddcode
 * @Date: 2024/12/26
 */
@Getter
public enum FileTypeEnum {

    // ==================== 图片类型 ====================
    IMAGE("image", "图片", FileCategory.IMAGE, Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg",
            "JPG", "JPEG", "PNG", "GIF", "BMP", "WEBP", "SVG"
    )),

    // ==================== 视频类型 ====================
    VIDEO("video", "视频", FileCategory.VIDEO, Arrays.asList(
            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm",
            "MP4", "AVI", "MKV", "MOV", "WMV", "FLV", "WEBM"
    )),

    // ==================== 音频类型 ====================
    AUDIO("audio", "音频", FileCategory.AUDIO, Arrays.asList(
            "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma",
            "MP3", "WAV", "FLAC", "AAC", "OGG", "M4A", "WMA"
    )),

    // ==================== 文档类型 ====================
    PDF("pdf", "PDF文档", FileCategory.DOCUMENT, Arrays.asList(
            "pdf", "PDF"
    )),

    WORD("word", "Word文档", FileCategory.DOCUMENT, Arrays.asList(
            "doc", "docx", "DOC", "DOCX"
    )),

    EXCEL("excel", "Excel表格", FileCategory.DOCUMENT, Arrays.asList(
            "xls", "xlsx", "XLS", "XLSX", "csv", "CSV"
    )),

    PPT("ppt", "PPT演示", FileCategory.DOCUMENT, Arrays.asList(
            "ppt", "pptx", "PPT", "PPTX"
    )),

    // ==================== 文本类型 ====================
    TEXT("text", "文本文件", FileCategory.DOCUMENT, Arrays.asList(
            "txt", "log", "ini", "properties", "yaml", "yml", "conf",
            "TXT", "LOG", "INI", "PROPERTIES", "YAML", "YML", "CONF"
    )),

    // ==================== 代码类型 ====================
    CODE("code", "代码文件", FileCategory.DOCUMENT, Arrays.asList(
            "java", "JAVA",
            "js", "jsx", "ts", "tsx", "JS", "JSX", "TS", "TSX",
            "py", "PY",
            "c", "cpp", "h", "hpp", "cc", "cxx",
            "C", "CPP", "H", "HPP", "CC", "CXX",
            "html", "css", "scss", "sass", "less", "vue",
            "HTML", "CSS", "SCSS", "SASS", "LESS", "VUE",
            "php", "go", "rs", "rb", "swift", "kt", "scala",
            "PHP", "GO", "RS", "RB", "SWIFT", "KT", "SCALA",
            "json", "xml", "sql", "sh", "bash", "bat", "ps1",
            "JSON", "XML", "SQL", "SH", "BASH", "BAT", "PS1",
            "cs", "CS",
            "toml", "TOML"
    )),

    // ==================== Markdown ====================
    MARKDOWN("markdown", "Markdown", FileCategory.DOCUMENT, Arrays.asList(
            "md", "markdown", "MD", "MARKDOWN"
    )),

    // ==================== 压缩包 ====================
    ARCHIVE("archive", "压缩包", FileCategory.OTHER, Arrays.asList(
            "zip", "rar", "7z", "tar", "gzip",
            "ZIP", "RAR", "7Z", "TAR", "GZIP "
    )),

    TIF("tif", "TIF", FileCategory.OTHER, Arrays.asList(
            "tif", "tiff", "TIF", "TIFF"
    )),

    DRAWIO("drawio", "Drawio", FileCategory.OTHER, Arrays.asList(
            "drawio", "Drawio"
    )),

    // ==================== 其他 ====================
    OTHER("other", "其他", FileCategory.OTHER, null);

    /**
     * 类型标识（唯一）
     */
    private final String code;

    /**
     * 类型名称
     */
    private final String name;

    /**
     * 所属大类
     */
    private final FileCategory category;

    /**
     * 支持的文件后缀列表
     */
    private final List<String> suffixes;

    FileTypeEnum(String code, String name, FileCategory category, List<String> suffixes) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.suffixes = suffixes;
    }

    private static final Map<String, FileTypeEnum> EXTENSION_MAP = new HashMap<>();
    private static final Map<FileCategory, List<FileTypeEnum>> CATEGORY_MAP = new EnumMap<>(FileCategory.class);

    static {
        // 构建扩展名映射
        for (FileTypeEnum type : values()) {
            if (type.getSuffixes() != null) {
                for (String ext : type.getSuffixes()) {
                    EXTENSION_MAP.put(ext.toLowerCase(), type);
                }
            }
        }

        // 构建分类映射
        for (FileTypeEnum type : values()) {
            CATEGORY_MAP.computeIfAbsent(type.category, k -> new ArrayList<>()).add(type);
        }
    }

    /**
     * 根据类型标识获取枚举（支持英文code或中文name）
     */
    public static FileTypeEnum fromType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }

        String trimmedType = type.trim();

        for (FileTypeEnum fileType : values()) {
            if (fileType.getCode().equalsIgnoreCase(trimmedType)
                    || fileType.getName().equals(trimmedType)) {
                return fileType;
            }
        }

        return null;
    }

    /**
     * 根据文件后缀判断文件类型
     */
    public static FileTypeEnum fromSuffix(String suffix) {
        if (suffix == null || suffix.trim().isEmpty()) {
            return OTHER;
        }

        String normalizedSuffix = suffix.trim().replace(".", "").toLowerCase();
        return EXTENSION_MAP.getOrDefault(normalizedSuffix, OTHER);
    }

    /**
     * 根据文件名判断文件类型
     */
    public static FileTypeEnum fromFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return OTHER;
        }

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) {
            return OTHER;
        }

        String extension = fileName.substring(dotIndex + 1);
        return fromSuffix(extension);
    }

    /**
     * 根据分类获取所有类型
     */
    public static List<FileTypeEnum> getByCategory(FileCategory category) {
        return CATEGORY_MAP.getOrDefault(category, Collections.emptyList());
    }

    /**
     * 判断文件扩展名是否为媒体文件（视频或音频）
     */
    public static boolean isMediaFile(String extension) {
        if (extension == null || extension.trim().isEmpty()) {
            return false;
        }
        FileTypeEnum fileType = fromSuffix(extension);
        return fileType == VIDEO || fileType == AUDIO;
    }

    /**
     * 判断文件扩展名是否为图片
     */
    public static boolean isImageFile(String extension) {
        if (extension == null || extension.trim().isEmpty()) {
            return false;
        }
        FileTypeEnum fileType = fromSuffix(extension);
        return fileType == IMAGE;
    }

    /**
     * 获取分类下所有后缀（用于前端筛选）
     */
    public static List<String> getSuffixesByCategory(FileCategory category) {
        return getByCategory(category).stream()
                .filter(type -> type.getSuffixes() != null)
                .flatMap(type -> type.getSuffixes().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 获取所有已知的文件后缀
     */
    public static List<String> getAllKnownSuffixes() {
        return Stream.of(values())
                .filter(type -> type != OTHER && type.getSuffixes() != null)
                .flatMap(type -> type.getSuffixes().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 获取除指定分类外的所有已知后缀
     */
    public static List<String> getAllKnownSuffixesExcluding(FileCategory excludeCategory) {
        return Stream.of(values())
                .filter(type -> type != OTHER
                        && type.getCategory() != excludeCategory
                        && type.getSuffixes() != null)
                .flatMap(type -> type.getSuffixes().stream())
                .distinct()
                .collect(Collectors.toList());
    }


    /**
     * 判断是否为其他类型
     */
    public boolean isOther() {
        return this == OTHER;
    }

    /**
     * 判断某个后缀是否属于当前类型
     */
    public boolean containsSuffix(String suffix) {
        if (suffix == null || this.suffixes == null) {
            return false;
        }
        String normalizedSuffix = suffix.trim().replace(".", "").toLowerCase();
        return this.suffixes.stream()
                .anyMatch(s -> s.equalsIgnoreCase(normalizedSuffix));
    }

    /**
     * 判断是否属于指定分类
     */
    public boolean isCategory(FileCategory category) {
        return this.category == category;
    }

    /**
     * 文件大类枚举
     */
    @Getter
    public enum FileCategory {
        IMAGE("image", "图片"),
        VIDEO("video", "视频"),
        AUDIO("audio", "音频"),
        DOCUMENT("document", "文档"),
        OTHER("other", "其他");

        private final String code;
        private final String name;

        FileCategory(String code, String name) {
            this.code = code;
            this.name = name;
        }

        /**
         * 根据code获取分类
         */
        public static FileCategory fromCode(String code) {
            if (code == null) return null;
            for (FileCategory category : values()) {
                if (category.code.equalsIgnoreCase(code)) {
                    return category;
                }
            }
            return null;
        }
    }
}
