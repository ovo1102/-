package com.hashcheck.io;

import com.hashcheck.model.FileHashResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReportWriter} 单元测试（对应验收用例 T8/AC-8）。
 *
 * <p>向临时目录写入报告后以 UTF-8 整体读回，对标题、时间、算法名、
 * 明细记录、失败原因与统计行逐一断言。</p>
 */
class ReportWriterTest {

    @TempDir
    Path tempDir;

    /** 以 UTF-8 读取文件全部内容。 */
    private String readUtf8(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** 构造一条成功结果与一条失败结果组成的混合结果集（含中文错误原因）。 */
    private List<FileHashResult> mixedResults() {
        File okFile = tempDir.resolve("ok.txt").toFile();
        File badFile = tempDir.resolve("missing.bin").toFile();
        return Arrays.asList(
                FileHashResult.success(okFile, "MD5", "900150983cd24fb0d6963f7d28e17f72"),
                FileHashResult.failure(badFile, "MD5", "FileNotFoundException: 文件不存在（无权限访问）")
        );
    }

    /** TR-6.1：List 重载报告必须包含标题、时间行、算法名、明细、失败原因与成功/失败统计。 */
    @Test
    void testListOverloadContainsAllSections() throws Exception {
        File report = tempDir.resolve("report-list.txt").toFile();
        List<FileHashResult> results = mixedResults();

        ReportWriter.saveReport(report, results, "MD5");

        assertTrue(report.exists(), "报告文件应被创建");
        String content = readUtf8(report);

        assertTrue(content.contains("本地文件完整性哈希校验报告"), "缺少报告标题");
        assertTrue(content.matches("(?s).*生成时间：\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.*"),
                "缺少格式正确的生成时间行");
        assertTrue(content.contains("哈希算法：MD5"), "缺少算法名行");
        assertTrue(content.contains("ok.txt"), "缺少成功记录文件路径");
        assertTrue(content.contains("900150983cd24fb0d6963f7d28e17f72"), "缺少成功记录哈希值");
        assertTrue(content.contains("状态：成功"), "缺少成功状态");
        assertTrue(content.contains("missing.bin"), "缺少失败记录文件路径");
        assertTrue(content.contains("状态：失败"), "缺少失败状态");
        assertTrue(content.contains("错误原因：FileNotFoundException: 文件不存在（无权限访问）"),
                "失败项必须包含错误原因，且中文不得乱码");
        assertTrue(content.contains("成功: 1 失败: 1"), "统计行必须包含成功/失败计数");
        assertTrue(content.contains("总计: 2"), "统计行必须包含总计");
    }

    /** 空结果集合也应生成结构完整的报告（统计为 0）。 */
    @Test
    void testListOverloadWithEmptyResults() throws Exception {
        File report = tempDir.resolve("empty-report.txt").toFile();

        ReportWriter.saveReport(report, java.util.Collections.emptyList(), "SHA-256");

        String content = readUtf8(report);
        assertTrue(content.contains("哈希算法：SHA-256"));
        assertTrue(content.contains("成功: 0 失败: 0"));
        assertTrue(content.contains("总计: 0"));
    }

    /** 多条记录必须按输入顺序编号 1..N。 */
    @Test
    void testRecordsNumberedSequentially() throws Exception {
        File report = tempDir.resolve("numbered.txt").toFile();
        File f1 = tempDir.resolve("a.txt").toFile();
        File f2 = tempDir.resolve("b.txt").toFile();
        File f3 = tempDir.resolve("c.txt").toFile();
        List<FileHashResult> results = Arrays.asList(
                FileHashResult.success(f1, "MD5", "11111111111111111111111111111111"),
                FileHashResult.success(f2, "MD5", "22222222222222222222222222222222"),
                FileHashResult.success(f3, "MD5", "33333333333333333333333333333333"));

        ReportWriter.saveReport(report, results, "MD5");

        String content = readUtf8(report);
        int idx1 = content.indexOf("[1]");
        int idx2 = content.indexOf("[2]");
        int idx3 = content.indexOf("[3]");
        assertTrue(idx1 >= 0 && idx2 > idx1 && idx3 > idx2, "记录必须按顺序编号");
        assertTrue(content.contains("成功: 3 失败: 0"));
    }

    /** TR-6.2：Map 重载报告必须包含全部键值，算法头标记为“未知/混合”。 */
    @Test
    void testMapOverloadContainsAllEntries() throws Exception {
        File report = tempDir.resolve("report-map.txt").toFile();
        Map<String, String> map = new LinkedHashMap<>();
        map.put("D:\\data\\a.txt", "900150983cd24fb0d6963f7d28e17f72");
        map.put("D:\\data\\中文目录\\b.txt", "e3b0c44298fc1c149afbf4c8996fb924");

        ReportWriter.saveReport(report, map);

        String content = readUtf8(report);
        assertTrue(content.contains("本地文件完整性哈希校验报告"));
        assertTrue(content.contains("哈希算法：未知/混合"), "Map 重载算法列应为“未知/混合”");
        assertTrue(content.contains("D:\\data\\a.txt"), "缺少 Map 键 1");
        assertTrue(content.contains("900150983cd24fb0d6963f7d28e17f72"), "缺少 Map 值 1");
        assertTrue(content.contains("D:\\data\\中文目录\\b.txt"), "缺少 Map 键 2（中文路径）");
        assertTrue(content.contains("e3b0c44298fc1c149afbf4c8996fb924"), "缺少 Map 值 2");
        assertTrue(content.contains("记录条数：2"), "缺少记录条数统计");
    }

    /** TR-6.2：null 入参必须抛出 IllegalArgumentException（逐参数校验）。 */
    @Test
    void testNullArgumentsThrowIllegalArgument() {
        File file = tempDir.resolve("x.txt").toFile();

        assertThrows(IllegalArgumentException.class,
                () -> ReportWriter.saveReport(null, new LinkedHashMap<String, String>()));
        assertThrows(IllegalArgumentException.class,
                () -> ReportWriter.saveReport(file, (Map<String, String>) null));
        assertThrows(IllegalArgumentException.class,
                () -> ReportWriter.saveReport(null, mixedResults(), "MD5"));
        assertThrows(IllegalArgumentException.class,
                () -> ReportWriter.saveReport(file, (List<FileHashResult>) null, "MD5"));
        assertThrows(IllegalArgumentException.class,
                () -> ReportWriter.saveReport(file, mixedResults(), null));
    }

    /** TR-6.2：保存路径为已存在目录时必须抛出 IOException。 */
    @Test
    void testTargetDirectoryThrowsIOException() {
        File dir = tempDir.toFile();

        assertThrows(IOException.class,
                () -> ReportWriter.saveReport(dir, new LinkedHashMap<String, String>()));
        assertThrows(IOException.class,
                () -> ReportWriter.saveReport(dir, mixedResults(), "MD5"));
    }

    /** 报告重复导出时应完整覆盖旧文件而非追加。 */
    @Test
    void testOverwriteExistingReport() throws Exception {
        File report = tempDir.resolve("overwrite.txt").toFile();
        Files.write(report.toPath(), "旧内容旧内容旧内容".getBytes(StandardCharsets.UTF_8));

        File f = tempDir.resolve("z.txt").toFile();
        ReportWriter.saveReport(report,
                java.util.Collections.singletonList(
                        FileHashResult.success(f, "MD5", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")),
                "MD5");

        String content = readUtf8(report);
        assertTrue(content.startsWith("本地文件完整性哈希校验报告"), "覆盖后应以标题开头");
        assertTrue(!content.contains("旧内容"), "旧内容必须被覆盖");
        assertEquals(1, countOccurrences(content, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    }

    /** 简单子串计数辅助方法。 */
    private static int countOccurrences(String text, String token) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(token, idx)) != -1) {
            count++;
            idx += token.length();
        }
        return count;
    }
}
