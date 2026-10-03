package com.hashcheck.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FileScaner} 单元测试。
 *
 * <p>覆盖 T4（多级目录递归）、T5（空目录）、单文件输入以及
 * FR-6/FR-7 的异常路径（不存在、null、无目录项遗漏）。</p>
 */
class FileScanerTest {

    @TempDir
    Path tempDir;

    /** T4/AC-4：含多级子目录与多种文件的目录树必须被完整、无重复地扫描，且结果不含目录。 */
    @Test
    void testRecursiveMultiLevelTree() throws Exception {
        // 构造目录树：
        // root/a1.txt  root/a2.log
        // root/sub1/b1.bin  root/sub1/sub2/c1.txt  root/sub1/sub2/c2.md
        // root/emptyDir/（空目录，不应产生元素）
        Path root = tempDir.resolve("tree");
        Files.createDirectories(root.resolve("sub1/sub2"));
        Files.createDirectories(root.resolve("emptyDir"));
        Set<Path> expectedPaths = new HashSet<>();
        expectedPaths.add(Files.write(root.resolve("a1.txt"), "a1".getBytes("UTF-8")));
        expectedPaths.add(Files.write(root.resolve("a2.log"), "a2".getBytes("UTF-8")));
        expectedPaths.add(Files.write(root.resolve("sub1/b1.bin"), new byte[]{1, 2, 3}));
        expectedPaths.add(Files.write(root.resolve("sub1/sub2/c1.txt"), "c1".getBytes("UTF-8")));
        expectedPaths.add(Files.write(root.resolve("sub1/sub2/c2.md"), "c2".getBytes("UTF-8")));
        // 再放一个 0 字节文件，验证空文件同样被收集
        expectedPaths.add(Files.write(root.resolve("sub1/empty.dat"), new byte[0]));

        List<File> scanned = FileScaner.scanFiles(root.toFile());

        // 数量正确：无遗漏
        assertEquals(expectedPaths.size(), scanned.size(), "扫描到的文件数量与预期不符");
        // 全部是普通文件：不含任何目录
        for (File f : scanned) {
            assertTrue(f.isFile(), "扫描结果中不应包含目录: " + f.getAbsolutePath());
        }
        // 集合完全相等且无重复
        Set<Path> scannedPaths = scanned.stream().map(File::toPath).collect(Collectors.toSet());
        assertEquals(expectedPaths, scannedPaths, "扫描文件集合与预期不一致");
        assertEquals(expectedPaths.size(), scannedPaths.size(), "扫描结果存在重复项");
    }

    /** AC-4：单文件输入返回仅含该文件的列表。 */
    @Test
    void testSingleFileReturnsSingleton() throws Exception {
        File file = Files.write(tempDir.resolve("only.txt"), "x".getBytes("UTF-8")).toFile();

        List<File> result = FileScaner.scanFiles(file);

        assertEquals(1, result.size());
        assertEquals(file.getAbsolutePath(), result.get(0).getAbsolutePath());
    }

    /** T5/AC-5：空目录扫描返回空列表且不抛异常。 */
    @Test
    void testEmptyDirectoryReturnsEmptyList() throws Exception {
        Path emptyDir = tempDir.resolve("empty");
        Files.createDirectories(emptyDir);

        List<File> result = FileScaner.scanFiles(emptyDir.toFile());

        assertTrue(result.isEmpty(), "空目录应返回空列表");
    }

    /** 只含空子目录的目录同样返回空列表。 */
    @Test
    void testDirectoryWithOnlyEmptySubDirectories() throws Exception {
        Path root = tempDir.resolve("root-empty");
        Files.createDirectories(root.resolve("a/b/c"));

        List<File> result = FileScaner.scanFiles(root.toFile());

        assertTrue(result.isEmpty());
    }

    /** T6/AC-6：不存在的路径必须抛出 IOException。 */
    @Test
    void testNonExistentPathThrowsIOException() {
        File missing = tempDir.resolve("no-such-dir").toFile();

        assertThrows(IOException.class, () -> FileScaner.scanFiles(missing));
    }

    /** AC-6：null 入参必须抛出 IllegalArgumentException。 */
    @Test
    void testNullThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> FileScaner.scanFiles(null));
    }

    /** 返回类型契约：扫描结果必须是可修改的 ArrayList（下游需要按下标使用）。 */
    @Test
    void testResultIsArrayListAndMutable() throws Exception {
        Files.write(tempDir.resolve("m.txt"), new byte[0]);

        List<File> result = FileScaner.scanFiles(tempDir.toFile());

        assertTrue(result instanceof ArrayList, "约定返回 ArrayList");
        result.add(result.get(0)); // 可修改，不抛 UnsupportedOperationException
        assertEquals(2, result.size());
    }

    /** 与 JDK 流式遍历交叉验证：大目录树下两种实现得到的文件集合一致。 */
    @Test
    void testCrossCheckWithNioWalk() throws Exception {
        Path root = tempDir.resolve("walk");
        Files.createDirectories(root.resolve("d1/d2"));
        for (int i = 0; i < 10; i++) {
            Files.write(root.resolve("f" + i + ".dat"), ("content-" + i).getBytes("UTF-8"));
        }
        Files.write(root.resolve("d1/g.txt"), "g".getBytes("UTF-8"));
        Files.write(root.resolve("d1/d2/h.txt"), "h".getBytes("UTF-8"));

        Set<String> nioSet;
        try (Stream<Path> walk = Files.walk(root)) {
            nioSet = walk.filter(Files::isRegularFile)
                    .map(p -> p.toFile().getAbsolutePath())
                    .collect(Collectors.toSet());
        }

        Set<String> scannerSet = FileScaner.scanFiles(root.toFile()).stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.toSet());

        assertEquals(nioSet, scannerSet);
    }
}
