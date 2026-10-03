package com.hashcheck.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * 哈希计算工具类。
 *
 * <p>基于 JDK 内置 {@link MessageDigest} 计算文件的 MD5 / SHA-256 摘要，
 * 输出小写十六进制字符串。文件采用 4096 字节缓冲区分块读取，
 * 大文件不会被一次性载入内存；输入流通过 try-with-resources 自动关闭。</p>
 *
 * <p>本类为无状态静态工具类，不可实例化、不可继承，天然线程安全。</p>
 *
 * @author 本地文件完整性哈希校验系统
 * @version 1.0.0
 */
public final class HashUtil {

    /** 分块读取缓冲区大小（字节），按策划书规定取 4096。 */
    public static final int BUFFER_SIZE = 4096;

    /**
     * 私有构造方法，禁止实例化工具类。
     */
    private HashUtil() {
    }

    /**
     * 计算指定文件的哈希摘要，返回小写十六进制字符串。
     *
     * <p>MD5 返回 32 位字符串，SHA-256 返回 64 位字符串。
     * 计算过程以 4096 字节为单位分块读取文件，内存占用恒定。</p>
     *
     * @param file      目标文件，不能为 null，必须存在且不能是目录
     * @param algorithm 哈希算法名称（如 {@code "MD5"}、{@code "SHA-256"}），不能为 null
     * @return 小写十六进制哈希字符串
     * @throws NullPointerException      当 file 或 algorithm 为 null 时
     * @throws FileNotFoundException     当目标文件不存在时
     * @throws IOException               当目标是目录、无读取权限或发生其他 IO 错误时
     * @throws NoSuchAlgorithmException  当 JDK 不支持指定算法名称时
     */
    public static String getFileHash(File file, String algorithm)
            throws IOException, NoSuchAlgorithmException {
        Objects.requireNonNull(file, "目标文件不能为 null");
        Objects.requireNonNull(algorithm, "算法名称不能为 null");

        if (!file.exists()) {
            throw new FileNotFoundException("文件不存在: " + file.getAbsolutePath());
        }
        if (file.isDirectory()) {
            throw new IOException("目标是目录而非文件，无法计算哈希: " + file.getAbsolutePath());
        }

        MessageDigest digest = MessageDigest.getInstance(algorithm);

        // try-with-resources 保证输入流在正常与异常路径下均被关闭
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int length;
            while ((length = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, length);
            }
        }

        return toLowerHex(digest.digest());
    }

    /**
     * 将摘要字节数组转换为小写十六进制字符串。
     *
     * <p>逐字节按无符号数处理（{@code 0xff & b}），单位数前补 0，
     * 保证输出长度严格为字节数的 2 倍。手写转换以兼容 JDK 8。</p>
     *
     * @param bytes 摘要字节数组
     * @return 小写十六进制字符串
     */
    private static String toLowerHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }
}
