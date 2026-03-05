package com.hmdp.utils;

public class SystemConstants {
    // 图片上传目录原先是写死的本机绝对路径，现抽成配置项 hmdp.upload-dir
    // （本机真实路径见 application-local.yaml，该文件不入版本库）。
    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;
}
