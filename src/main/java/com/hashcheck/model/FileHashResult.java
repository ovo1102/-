package com.hashcheck.model;

import java.io.File;
import java.util.Objects;

/**
 * 文件哈希校验结果模型（不可变对象）。
 *
 * <p>每一个 {@code FileHashResult} 描述“对单个文件以指定算法执行一次校验”的结论：
 * 成功时携带小写十六进制哈希值；失败时携带错误原因。对象一经创建不可修改，
 * 可安全地在多个工作线程与 EDT 之间传递。</p>
 *
 * <p>请通过静态工厂方法 {@link #success(File, String, String)} 与
 * {@link #failure(File, String, String)} 创建实例。</p>
 *
 * @author 本地文件完整性哈希校验系统
 * @version 1.0.0
 */
public final class FileHashResult {

    /** 被校验文件的绝对路径。 */
    private final String filePath;

    /** 哈希算法名称（MD5 / SHA-256）。 */
    private final String algorithm;

    /** 校验成功时的小写十六进制哈希值；失败时为 {@code null}。 */
    private final String hash;

    /** 成功标志：{@code true} 表示成功，{@code false} 表示失败。 */
    private final boolean success;

    /** 失败时的错误原因；成功时为 {@code null}。 */
    private final String errorMessage;

    /**
     * 私有全参构造方法，仅由静态工厂调用。
     *
     * @param filePath     文件绝对路径
     * @param algorithm    算法名称
     * @param hash         哈希值（失败可为 null）
     * @param success      是否成功
     * @param errorMessage 错误原因（成功可为 null）
     */
    private FileHashResult(String filePath, String algorithm, String hash,
                           boolean success, String errorMessage) {
        this.filePath = filePath;
        this.algorithm = algorithm;
        this.hash = hash;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    /**
     * 创建一个“校验成功”结果。
     *
     * @param file      被校验文件，不能为 null
     * @param algorithm 算法名称，不能为 null
     * @param hash      小写十六进制哈希字符串，不能为 null
     * @return 成功结果对象
     * @throws NullPointerException 当任一必填入参为 null 时
     */
    public static FileHashResult success(File file, String algorithm, String hash) {
        Objects.requireNonNull(file, "被校验文件不能为 null");
        Objects.requireNonNull(algorithm, "算法名称不能为 null");
        Objects.requireNonNull(hash, "哈希值不能为 null");
        return new FileHashResult(file.getAbsolutePath(), algorithm, hash, true, null);
    }

    /**
     * 创建一个“校验失败”结果。
     *
     * @param file         被校验文件，不能为 null
     * @param algorithm    算法名称，不能为 null
     * @param errorMessage 失败原因描述，不能为 null
     * @return 失败结果对象
     * @throws NullPointerException 当任一必填入参为 null 时
     */
    public static FileHashResult failure(File file, String algorithm, String errorMessage) {
        Objects.requireNonNull(file, "被校验文件不能为 null");
        Objects.requireNonNull(algorithm, "算法名称不能为 null");
        Objects.requireNonNull(errorMessage, "错误信息不能为 null");
        return new FileHashResult(file.getAbsolutePath(), algorithm, null, false, errorMessage);
    }

    /**
     * 获取被校验文件的绝对路径。
     *
     * @return 文件绝对路径字符串
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * 获取哈希算法名称。
     *
     * @return 算法名称
     */
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * 获取哈希值。
     *
     * @return 小写十六进制哈希字符串；失败结果返回 {@code null}
     */
    public String getHash() {
        return hash;
    }

    /**
     * 判断本次校验是否成功。
     *
     * @return 成功返回 {@code true}，失败返回 {@code false}
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取失败原因。
     *
     * @return 错误原因字符串；成功结果返回 {@code null}
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 返回便于日志阅读的中文摘要文本。
     *
     * @return 描述本结果的字符串
     */
    @Override
    public String toString() {
        if (success) {
            return "校验成功[" + algorithm + "] " + filePath + " -> " + hash;
        }
        return "校验失败[" + algorithm + "] " + filePath + "，原因：" + errorMessage;
    }
}
