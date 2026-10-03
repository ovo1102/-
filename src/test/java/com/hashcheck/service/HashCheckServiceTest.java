package com.hashcheck.service;

import com.hashcheck.model.FileHashResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HashCheckService} 集成测试。
 *
 * <p>覆盖 AC-3/AC-6/AC-7 与 TR-7.1～TR-7.4：正常批量顺序与正确性、
 * 坏路径混合批量的异常收口、空列表不建池、工作线程观测（≥2 个且非 EDT）、
 * EDT 探针在批量期间可被调度、进度回调次数与末次数值。</p>
 */
class HashCheckServiceTest {

    @TempDir
    Path tempDir;

    /** 创建写入指定字符串内容的文件。 */
    private File createFile(String name, String content) throws Exception {
        File file = tempDir.resolve(name).toFile();
        Files.write(file.toPath(), content.getBytes("UTF-8"));
        return file;
    }

    /** 创建指定大小、内容可重复的大文件（用于制造真实并发窗口）。 */
    private File createLargeFile(String name, int sizeBytes) throws Exception {
        File file = tempDir.resolve(name).toFile();
        byte[] data = new byte[sizeBytes];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 251);
        }
        Files.write(file.toPath(), data);
        return file;
    }

    /** TR-7.1：多个正常文件批量结果全部成功，哈希与直接调用 HashUtil 一致，顺序与输入一致。 */
    @Test
    void testNormalBatchOrderAndHashes() throws Exception {
        File f1 = createFile("1.txt", "one");
        File f2 = createFile("2.txt", "two");
        File f3 = createFile("3.txt", "three");
        File f4 = createFile("4.txt", "four");
        List<File> input = new ArrayList<>();
        input.add(f3);
        input.add(f1);
        input.add(f4);
        input.add(f2);

        List<FileHashResult> results = new HashCheckService(4).checkAll(input, "MD5");

        assertEquals(4, results.size());
        for (int i = 0; i < input.size(); i++) {
            FileHashResult r = results.get(i);
            assertTrue(r.isSuccess(), "第 " + i + " 个结果应成功: " + r.getErrorMessage());
            assertEquals(input.get(i).getAbsolutePath(), r.getFilePath(), "结果顺序必须与输入一致");
            assertEquals(HashUtil.getFileHash(input.get(i), "MD5"), r.getHash(),
                    "服务计算哈希应与 HashUtil 直接计算一致");
            assertEquals("MD5", r.getAlgorithm());
        }
    }

    /** TR-7.2：混入不存在路径与目录时整体不抛异常，成功/失败计数正确，失败原因非空。 */
    @Test
    void testMixedBadPathsDoNotInterruptBatch() throws Exception {
        File ok1 = createFile("ok1.txt", "a");
        File missing = tempDir.resolve("gone.bin").toFile();
        File ok2 = createFile("ok2.txt", "bb");
        File dir = tempDir.resolve("a-dir").toFile();
        Files.createDirectories(dir.toPath());

        List<File> input = new ArrayList<>();
        input.add(ok1);
        input.add(missing);   // 不存在
        input.add(ok2);
        input.add(dir);       // 目录不能算哈希
        input.add(null);      // null 元素

        List<FileHashResult> results =
                new HashCheckService().checkAll(input, "SHA-256");

        int success = 0;
        int failure = 0;
        for (FileHashResult r : results) {
            if (r.isSuccess()) {
                success++;
            } else {
                failure++;
                assertFalse(r.getErrorMessage() == null || r.getErrorMessage().trim().isEmpty(),
                        "失败结果必须携带错误原因");
            }
        }
        assertEquals(2, success, "成功计数不符");
        assertEquals(3, failure, "失败计数不符");
        assertTrue(results.get(0).isSuccess());
        assertFalse(results.get(1).isSuccess());
        assertTrue(results.get(2).isSuccess());
        assertFalse(results.get(3).isSuccess());
        assertFalse(results.get(4).isSuccess());
    }

    /** TR-7.3：空列表返回空结果，且不创建/不遗留任何工作线程。 */
    @Test
    void testEmptyListReturnsEmptyAndCreatesNoPool() throws Exception {
        List<FileHashResult> results =
                new HashCheckService().checkAll(new ArrayList<File>(), "MD5");

        assertTrue(results.isEmpty());

        // 给 JVM 一点调度时间后，确认没有 hashcheck-worker 线程存活
        Set<Thread> threads = getAllLiveThreads();
        for (Thread t : threads) {
            assertFalse(t.getName().startsWith("hashcheck-worker-"),
                    "空列表不应创建工作线程，但发现: " + t.getName());
        }
    }

    /** AC-7/TR-7.3：批量中观测到的工作线程名集合 ≥ 2，且均非 EDT；所有结果成功。 */
    @Test
    void testUsesMultipleWorkerThreadsNotEdt() throws Exception {
        // 16 个 2MB 文件：首批 4 个任务必然在不同工作线程上并行执行
        List<File> files = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            files.add(createLargeFile("big-" + i + ".bin", 2 * 1024 * 1024));
        }

        Set<String> workerNames = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
        List<FileHashResult> results = new HashCheckService(4).checkAll(files, "MD5",
                (done, total, latest) ->
                        workerNames.add(Thread.currentThread().getName()));

        assertTrue(workerNames.size() >= 2,
                "应至少观测到 2 个工作线程，实际: " + workerNames);
        for (String name : workerNames) {
            assertFalse(name.startsWith("AWT-EventQueue"),
                    "哈希计算不得在 EDT 上执行，发现 EDT 线程: " + name);
            assertTrue(name.startsWith("hashcheck-worker-"),
                    "工作线程应由命名工厂创建，实际: " + name);
        }
        for (FileHashResult r : results) {
            assertTrue(r.isSuccess(), "全部大文件应计算成功: " + r.getErrorMessage());
        }

        // 批量结束后池必须关闭：不遗留工作线程
        for (Thread t : getAllLiveThreads()) {
            assertFalse(t.getName().startsWith("hashcheck-worker-"),
                    "批量结束后工作线程应已关闭，遗留: " + t.getName());
        }
    }

    /** AC-7：批量计算进行期间，EDT 探针任务必须能在时限内被调度执行（界面不卡死）。 */
    @Test
    void testEdtRemainsResponsiveDuringBatch() throws Exception {
        // headless 环境（如无显示器的 CI）无法真实启动 EDT，跳过本探针用例
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "非 headless 环境才执行 EDT 探针");

        List<File> files = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            files.add(createLargeFile("edt-" + i + ".bin", 2 * 1024 * 1024));
        }

        CountDownLatch firstTaskRunning = new CountDownLatch(1);
        Thread batchThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    new HashCheckService(4).checkAll(files, "MD5", (done, total, latest) -> {
                        firstTaskRunning.countDown();
                        try {
                            // 让工作线程保持忙碌，确保探针与批量计算真实重叠
                            Thread.sleep(30);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "test-batch-driver");
        batchThread.start();

        assertTrue(firstTaskRunning.await(5, TimeUnit.SECONDS), "批量任务未能在时限内启动");

        CountDownLatch edtProbe = new CountDownLatch(1);
        SwingUtilities.invokeLater(edtProbe::countDown);
        assertTrue(edtProbe.await(5, TimeUnit.SECONDS),
                "批量计算期间 EDT 探针未在 5 秒内执行，界面可能被阻塞");

        batchThread.join(10_000);
        assertFalse(batchThread.isAlive(), "批量线程应在 10 秒内结束");
    }

    /** TR-7.4：进度回调被调用次数 = 文件数，回调完成数序列为 1..N，末次数值 = 总数。 */
    @Test
    void testProgressCallbackCountAndFinalValue() throws Exception {
        List<File> files = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            files.add(createFile("p" + i + ".txt", "content-" + i));
        }

        AtomicInteger callCount = new AtomicInteger(0);
        Set<Integer> seenCompleted = new HashSet<>();
        int[] lastDone = new int[1];
        int[] lastTotal = new int[1];

        new HashCheckService(3).checkAll(files, "MD5", (done, total, latest) -> {
            callCount.incrementAndGet();
            seenCompleted.add(done);
            lastDone[0] = done;
            lastTotal[0] = total;
        });

        assertEquals(9, callCount.get(), "每个文件必须恰好回调一次");
        for (int n = 1; n <= 9; n++) {
            assertTrue(seenCompleted.contains(n), "回调完成数序列缺少 " + n);
        }
        assertEquals(9, lastDone[0], "末次回调 completed 必须等于总数");
        assertEquals(9, lastTotal[0], "回调 total 必须等于文件总数");
    }

    /** checkOne 便捷方法：成功与失败两种路径。 */
    @Test
    void testCheckOneSuccessAndFailure() throws Exception {
        File ok = createFile("one.txt", "abc");

        FileHashResult success = new HashCheckService().checkOne(ok, "MD5");
        assertTrue(success.isSuccess());
        assertEquals("900150983cd24fb0d6963f7d28e17f72", success.getHash());

        FileHashResult missing = new HashCheckService()
                .checkOne(tempDir.resolve("nope.dat").toFile(), "MD5");
        assertFalse(missing.isSuccess());
        assertTrue(missing.getErrorMessage().contains("FileNotFoundException"));

        FileHashResult badAlgo = new HashCheckService().checkOne(ok, "BOGUS");
        assertFalse(badAlgo.isSuccess());
        assertTrue(badAlgo.getErrorMessage().contains("NoSuchAlgorithmException"));

        FileHashResult nullFile = new HashCheckService().checkOne(null, "MD5");
        assertFalse(nullFile.isSuccess());
        assertTrue(nullFile.getErrorMessage().contains("null"));
    }

    /** 非法线程数构造必须抛 IllegalArgumentException；默认构造可用。 */
    @Test
    void testInvalidThreadCountRejected() {
        assertThrows(IllegalArgumentException.class, () -> new HashCheckService(0));
        assertThrows(IllegalArgumentException.class, () -> new HashCheckService(-2));
        assertEquals(4, HashCheckService.DEFAULT_THREAD_COUNT);
    }

    /** checkAll 对 null 入参快速失败。 */
    @Test
    void testCheckAllNullArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new HashCheckService().checkAll(null, "MD5"));
        assertThrows(IllegalArgumentException.class,
                () -> new HashCheckService().checkAll(new ArrayList<File>(), null));
    }

    /** 回调自身抛出运行时异常不得影响批量主流程：结果完整、计数正确。 */
    @Test
    void testThrowingCallbackDoesNotBreakBatch() throws Exception {
        List<File> files = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            files.add(createFile("cb" + i + ".txt", "data-" + i));
        }

        AtomicInteger calls = new AtomicInteger();
        List<FileHashResult> results = new HashCheckService(2).checkAll(files, "MD5",
                (done, total, latest) -> {
                    calls.incrementAndGet();
                    if (done == 1) {
                        // 模拟 UI 侧回调实现缺陷抛出异常
                        throw new IllegalStateException("模拟回调异常");
                    }
                });

        assertEquals(5, results.size(), "回调异常不得丢失结果");
        assertEquals(5, calls.get(), "回调异常后其余文件仍应继续回调");
        for (FileHashResult r : results) {
            assertTrue(r.isSuccess());
        }
    }

    /** 获取当前 JVM 全部存活线程的快照集合。 */
    private static Set<Thread> getAllLiveThreads() {
        // 找到根线程组
        ThreadGroup root = Thread.currentThread().getThreadGroup();
        while (root.getParent() != null) {
            root = root.getParent();
        }
        int estimate = Math.max(root.activeCount() * 2, 16);
        Thread[] threads;
        int count;
        do {
            estimate *= 2;
            threads = new Thread[estimate];
            count = root.enumerate(threads);
        } while (count == threads.length);

        Set<Thread> live = new HashSet<>();
        for (int i = 0; i < count; i++) {
            if (threads[i].isAlive()) {
                live.add(threads[i]);
            }
        }
        return live;
    }
}
