package com.hashcheck.io;

import com.hashcheck.model.FileHashResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 校验报告导出类（数据访问层）。
 *
 * <p>将一次哈希校验的结果写入本地 UTF-8 文本文件。提供两个重载：</p>
 * <ul>
 *   <li>{@link #saveReport(File, Map)}：策划书规定的原始签名，
 *       接收“文件路径 → 哈希值”映射，报告算法列标记为“未知/混合”；</li>
 *   <li>{@link #saveReport(File, List, String)}：工程化增强签名，
 *       完整记录算法名、失败原因与成功/失败统计。</li>
 * </ul>
 *
 * <p>输出统一使用 UTF-8 编码与 try-with-resources 自动关闭资源；
 * 本类为无状态静态工具类，不可实例化、不可继承。</p>
 *
 * @author 本地文件完整性哈希校验系统
 * @version 1.0.0
 */
public final class ReportWriter {

    /** 报告固定标题。 */
    private static final String TITLE = "本地文件完整性哈希校验报告";

    /** 报告分隔线。 */
    private static final String SEPARATOR = "----------------------------------------";

    /** 生成时间格式。 */
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Map 重载在缺失算法上下文时使用的占位描述。 */
    private static final String ALGORITHM_UNKNOWN = "未知/混合";

    /**
     * 私有构造方法，禁止实例化工具类。
     */
    private ReportWriter() {
    }

    /**
     * 策划书原始签名：将“文件路径 → 哈希值”映射写入 txt 报告。
     *
     * <p>该重载不携带算法信息，报告头算法列写“未知/混合”。</p>
     *
     * @param saveFile  报告保存目标文件，不能为 null，不能是已存在的目录
     * @param resultMap 文件路径到哈希值的映射，不能为 null
     * @throws IllegalArgumentException 当 saveFile 或 resultMap 为 null 时
     * @throws IOException              当目标是目录或文件写入失败时
     */
    public static void saveReport(File saveFile, Map<String, String> resultMap)
            throws IOException {
        if (saveFile == null) {
            throw new IllegalArgumentException("报告保存路径不能为 null");
        }
        if (resultMap == null) {
            throw new IllegalArgumentException("结果映射不能为 null");
        }
        ensureWritableTarget(saveFile);

        try (BufferedWriter writer = newReportWriter(saveFile)) {
            writeHeader(writer, ALGORITHM_UNKNOWN);
            int index = 1;
            for (Map.Entry<String, String> entry : resultMap.entrySet()) {
                writer.write("[" + index + "] 文件路径：" + entry.getKey());
                writer.newLine();
                writer.write("    哈希值：" + entry.getValue());
                writer.newLine();
                index++;
            }
            writer.write(SEPARATOR);
            writer.newLine();
            writer.write("记录条数：" + resultMap.size());
            writer.newLine();
        }
    }

    /**
     * 工程化增强签名：将结构化校验结果写入 txt 报告。
     *
     * <p>报告包含：标题、生成时间、算法名、逐条明细（成功记录哈希值，
     * 失败记录错误原因）以及“成功: N 失败: M”统计信息。</p>
     *
     * @param saveFile  报告保存目标文件，不能为 null，不能是已存在的目录
     * @param results   校验结果列表（按展示顺序），不能为 null
     * @param algorithm 本次校验使用的算法名，不能为 null
     * @throws IllegalArgumentException 当任一必填入参为 null 时
     * @throws IOException              当目标是目录或文件写入失败时
     */
    public static void saveReport(File saveFile, List<FileHashResult> results, String algorithm)
            throws IOException {
        if (saveFile == null) {
            throw new IllegalArgumentException("报告保存路径不能为 null");
        }
        if (results == null) {
            throw new IllegalArgumentException("结果列表不能为 null");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("算法名称不能为 null");
        }
        ensureWritableTarget(saveFile);

        int successCount = 0;
        int failureCount = 0;

        try (BufferedWriter writer = newReportWriter(saveFile)) {
            writeHeader(writer, algorithm);

            int index = 1;
            for (FileHashResult result : results) {
                writer.write("[" + index + "] 文件路径：" + result.getFilePath());
                writer.newLine();
                if (result.isSuccess()) {
                    successCount++;
                    writer.write("    哈希值：" + result.getHash());
                    writer.newLine();
                    writer.write("    状态：成功");
                } else {
                    failureCount++;
                    writer.write("    状态：失败");
                    writer.newLine();
                    writer.write("    错误原因：" + result.getErrorMessage());
                }
                writer.newLine();
                index++;
            }

            writer.write(SEPARATOR);
            writer.newLine();
            writer.write("统计信息：成功: " + successCount
                    + " 失败: " + failureCount
                    + " 总计: " + results.size());
            writer.newLine();
        }
    }

    /**
     * 校验保存目标不是目录（目录无法作为文件写入）。
     *
     * @param saveFile 用户指定的保存目标
     * @throws IOException 当目标已存在且是一个目录时
     */
    private static void ensureWritableTarget(File saveFile) throws IOException {
        if (saveFile.isDirectory()) {
            throw new IOException("报告保存路径是一个目录而非文件: " + saveFile.getAbsolutePath());
        }
    }

    /**
     * 创建以 UTF-8 编码写入目标文件的缓冲字符输出流。
     *
     * @param saveFile 目标文件
     * @return 缓冲字符输出流（调用方负责 try-with-resources 关闭）
     * @throws IOException 当文件无法打开或创建时
     */
    private static BufferedWriter newReportWriter(File saveFile) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(saveFile), StandardCharsets.UTF_8));
    }

    /**
     * 写入报告公共头部：标题、生成时间、算法名。
     *
     * @param writer    输出流
     * @param algorithm 算法名（或“未知/混合”）
     * @throws IOException 写入失败时抛出
     */
    private static void writeHeader(BufferedWriter writer, String algorithm) throws IOException {
        writer.write(TITLE);
        writer.newLine();
        writer.write("========================================");
        writer.newLine();
        writer.write("生成时间：" + LocalDateTime.now().format(TIME_FORMATTER));
        writer.newLine();
        writer.write("哈希算法：" + algorithm);
        writer.newLine();
        writer.write(SEPARATOR);
        writer.newLine();
    }
}
