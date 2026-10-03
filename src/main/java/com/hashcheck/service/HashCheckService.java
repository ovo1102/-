package com.hashcheck.service;

import com.hashcheck.model.FileHashResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件哈希批量校验服务（业务层并发编排者）。
 *
 * <p>本类把策划书中原先放在界面层的“固定线程池并发调度、单任务异常收口、
 * 进度回调”等业务逻辑下沉到业务层，使 {@code HashCheckGUI} 只负责事件与渲染。
 * 服务本身不依赖 Swing，可直接进行自动化测试。</p>
 *
 * <p>核心约定：</p>
 * <ul>
 *   <li>使用固定大小线程池（默认 {@value #DEFAULT_THREAD_COUNT} 线程，可构造注入）；</li>
 *   <li>每个文件的任何异常都在任务内部收口为 {@link FileHashResult#failure}，
 *       单文件失败不影响其他文件，批量整体不因业务异常中断；</li>
 *   <li>结果严格按输入顺序返回，与任务完成先后无关；</li>
 *   <li>{@link ProgressCallback} 在工作线程触发，UI 需自行切回 EDT；</li>
 *   <li>线程池每次批量结束后关闭；空输入直接返回，不创建线程池。</li>
 * </ul>
 *
 * @author 本地文件完整性哈希校验系统
 * @version 1.0.0
 */
public class HashCheckService {

    /** 默认固定线程池大小（策划书规定为 4）。 */
    public static final int DEFAULT_THREAD_COUNT = 4;

    /** null 列表元素在结果路径中的占位描述。 */
    private static final File NULL_FILE_PLACEHOLDER = new File("(null 文件)");

    /** 本实例批量任务使用的线程数。 */
    private final int threadCount;

    /**
     * 进度回调接口。
     *
     * <p>每完成一个文件（无论成功或失败）回调一次；回调方法运行在工作线程中，
     * 实现方若需更新 Swing 界面必须自行通过
     * {@code SwingUtilities.invokeLater} 切回事件分派线程（EDT）。</p>
     */
    @FunctionalInterface
    public interface ProgressCallback {

        /**
         * 进度通知。
         *
         * @param completed 已完成文件数（从 1 递增到 total）
         * @param total     本次批量文件总数
         * @param latest    刚完成的那个文件的校验结果
         */
        void onProgress(int completed, int total, FileHashResult latest);
    }

    /**
     * 使用默认 4 线程构造服务。
     */
    public HashCheckService() {
        this(DEFAULT_THREAD_COUNT);
    }

    /**
     * 使用指定线程数构造服务（便于测试与扩展）。
     *
     * @param threadCount 固定线程池大小，必须大于等于 1
     * @throws IllegalArgumentException 当 threadCount 小于 1 时
     */
    public HashCheckService(int threadCount) {
        if (threadCount < 1) {
            throw new IllegalArgumentException("线程数必须大于等于 1，实际为: " + threadCount);
        }
        this.threadCount = threadCount;
    }

    /**
     * 校验单个文件。
     *
     * <p>本方法不向调用方抛出业务异常：文件不存在、是目录、无权限、
     * 非法算法名等所有错误均转换为失败结果返回。</p>
     *
     * @param file      目标文件；为 null 时返回失败结果
     * @param algorithm 算法名称；为 null 或非法时返回失败结果
     * @return 成功或失败的校验结果，不会返回 null
     */
    public FileHashResult checkOne(File file, String algorithm) {
        if (file == null) {
            return FileHashResult.failure(NULL_FILE_PLACEHOLDER,
                    algorithm == null ? "null" : algorithm, "文件参数为 null");
        }
        try {
            String hash = HashUtil.getFileHash(file, algorithm);
            return FileHashResult.success(file, algorithm, hash);
        } catch (Exception e) {
            // 收口全部异常（IOException / NoSuchAlgorithmException / RuntimeException）
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            return FileHashResult.failure(file,
                    algorithm == null ? "null" : algorithm, reason);
        }
    }

    /**
     * 批量校验（无进度回调重载）。
     *
     * @param files     待校验文件列表，不能为 null；元素为 null 计为失败项
     * @param algorithm 算法名称，不能为 null
     * @return 与输入顺序一致的校验结果列表；空列表返回空列表
     * @throws IllegalArgumentException 当 files 或 algorithm 为 null 时
     * @throws InterruptedException     当等待批量任务完成时线程被中断
     */
    public List<FileHashResult> checkAll(List<File> files, String algorithm)
            throws InterruptedException {
        return checkAll(files, algorithm, null);
    }

    /**
     * 批量校验核心方法：提交全部文件到固定线程池并发计算。
     *
     * @param files     待校验文件列表，不能为 null；元素为 null 计为失败项
     * @param algorithm 算法名称，不能为 null
     * @param callback  进度回调，可为 null；回调在工作线程触发
     * @return 与输入顺序一致的校验结果列表；空列表返回空列表（且不创建线程池）
     * @throws IllegalArgumentException 当 files 或 algorithm 为 null 时
     * @throws InterruptedException     当等待批量任务完成时线程被中断
     */
    public List<FileHashResult> checkAll(List<File> files, String algorithm,
                                         ProgressCallback callback) throws InterruptedException {
        if (files == null) {
            throw new IllegalArgumentException("文件列表不能为 null");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("算法名称不能为 null");
        }
        if (files.isEmpty()) {
            // 空输入短路：不创建线程池，保证无线程泄漏
            return new ArrayList<>();
        }

        final int total = files.size();
        // 按下标占位，保证返回顺序与输入一致，与完成先后无关
        final FileHashResult[] results = new FileHashResult[total];
        final AtomicInteger completed = new AtomicInteger(0);
        final ExecutorService pool =
                Executors.newFixedThreadPool(threadCount, new NamedWorkerThreadFactory());

        try {
            List<Future<?>> futures = new ArrayList<>(total);
            for (int i = 0; i < total; i++) {
                final int index = i;
                final File file = files.get(i);
                futures.add(pool.submit(new Runnable() {
                    @Override
                    public void run() {
                        // checkOne 已保证不抛出业务异常
                        FileHashResult result = checkOne(file, algorithm);
                        results[index] = result;
                        int done = completed.incrementAndGet();
                        if (callback != null) {
                            invokeCallbackSafely(callback, done, total, result);
                        }
                    }
                }));
            }

            // 等待每个任务结束。任务内部已收口全部业务异常，
            // 此处 ExecutionException 仅作为“不可达兜底”转换为非受检异常。
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    throw new RuntimeException("批量校验发生未预期的内部错误", e.getCause());
                }
            }
        } finally {
            // 任何退出路径（正常/中断/兜底异常）都必须关闭线程池
            pool.shutdown();
            if (!pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
                pool.shutdownNow();
            }
        }

        return new ArrayList<>(Arrays.asList(results));
    }

    /**
     * 安全执行进度回调：回调实现（通常是 UI 侧代码）抛出的运行时异常
     * 不允许影响批量主流程与结果完整性。
     *
     * @param callback 回调对象
     * @param done     已完成数
     * @param total    总数
     * @param result   刚完成的结果
     */
    private static void invokeCallbackSafely(ProgressCallback callback, int done, int total,
                                             FileHashResult result) {
        try {
            callback.onProgress(done, total, result);
        } catch (RuntimeException e) {
            // 回调异常属于调用方问题：忽略但保留可追溯性，不中断其余任务
            System.err.println("进度回调执行异常，已忽略: " + e);
        }
    }

    /**
     * 工作线程工厂：统一命名 {@code hashcheck-worker-N} 并设为守护线程，
     * 便于并发测试观测，且不会阻止 JVM 正常退出。
     */
    private static final class NamedWorkerThreadFactory implements ThreadFactory {

        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "hashcheck-worker-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
