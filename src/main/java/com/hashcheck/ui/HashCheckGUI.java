package com.hashcheck.ui;

import com.hashcheck.io.ReportWriter;
import com.hashcheck.model.FileHashResult;
import com.hashcheck.service.FileScaner;
import com.hashcheck.service.HashCheckService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 本地文件完整性哈希校验工具 Swing 主界面（界面层，程序入口）。
 *
 * <p>界面职责严格限定为：控件装配、按钮事件监听、在 EDT 上渲染结果与弹窗；
 * 文件扫描、哈希计算、并发调度全部委托给业务层 {@link FileScaner} /
 * {@link HashCheckService}，报告导出委托给 {@link ReportWriter}。</p>
 *
 * <p>线程模型：所有控件创建与更新都在事件分派线程（EDT）进行；
 * “开始校验”监听器仅做参数校验与按钮禁用，随后启动守护后台线程
 * {@code hashcheck-batch} 执行扫描与并发计算；业务回调在工作线程触发，
 * 经 {@link SwingUtilities#invokeLater} 切回 EDT 后再更新界面，
 * 因此批量计算期间界面始终保持可响应。</p>
 *
 * @author 本地文件完整性哈希校验系统
 * @version 1.0.0
 */
public class HashCheckGUI extends JFrame {

    private static final long serialVersionUID = 1L;

    /** 支持的哈希算法（顺序即下拉框显示顺序）。 */
    private static final String[] ALGORITHMS = {"MD5", "SHA-256"};

    /** 结果区表头行。 */
    private static final String RESULT_HEADER = "序号 | 文件路径 | 算法 | 哈希值/错误信息";

    /** 业务服务（默认 4 线程固定池）。 */
    private final transient HashCheckService service = new HashCheckService();

    /** 已选择的文件或目录。 */
    private transient File selectedFile;

    /** 最近一次校验结果（导出报告的数据源）。 */
    private transient List<FileHashResult> lastResults;

    /** EDT 侧结果行缓冲：下一个应显示的序号（从 1 开始）。仅允许在 EDT 访问。 */
    private int nextResultIndex = 1;

    /** EDT 侧乱序到达结果的按序号缓冲，保证结果行严格按输入顺序刷出。仅允许在 EDT 访问。 */
    private final transient TreeMap<Integer, FileHashResult> pendingResults = new TreeMap<>();

    // —— 界面控件 ——
    private JTextField tfPath;
    private JButton btnSelectFile;
    private JButton btnSelectDir;
    private JComboBox<String> cbAlgorithm;
    private JButton btnStart;
    private JButton btnExport;
    private JButton btnClear;
    private JTextArea taResult;
    private JProgressBar progressBar;
    private JLabel lblStatus;

    /**
     * 构造主窗口并装配全部界面元素。
     */
    public HashCheckGUI() {
        initUI();
    }

    /**
     * 程序入口：在 EDT 中初始化并显示主窗口。
     *
     * @param args 启动参数（未使用）
     */
    public static void main(String[] args) {
        // 尽量使用系统外观，失败时回退为 Java 默认外观（不影响功能）
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) {
            // 外观设置失败不影响程序运行，保持默认外观即可
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                HashCheckGUI gui = new HashCheckGUI();
                gui.setVisible(true);
            }
        });
    }

    /**
     * 装配窗口、控件布局与事件监听器。
     */
    private void initUI() {
        setTitle("本地文件完整性哈希校验工具 v1.0.0");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(new Dimension(820, 580));
        setMinimumSize(new Dimension(640, 420));
        fitAndCenterOnScreen();

        add(createControlPanel(), BorderLayout.NORTH);
        add(createResultArea(), BorderLayout.CENTER);
        add(createStatusPanel(), BorderLayout.SOUTH);
    }

    /**
     * 将窗口居中显示并夹紧到主屏幕可用区域内。
     *
     * <p>在 Windows 高分屏缩放或任务栏占用等情况下，单纯使用
     * {@code setLocationRelativeTo(null)} 可能使窗口部分越出屏幕；
     * 此方法先居中，再用 {@link GraphicsEnvironment#getMaximumWindowBounds()}
     * （已扣除任务栏）对窗口位置与尺寸进行夹紧，保证标题栏、按钮与状态栏完整可见。</p>
     */
    private void fitAndCenterOnScreen() {
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();

        // 窗口尺寸不超过屏幕可用区域
        int width = Math.min(getWidth(), usable.width);
        int height = Math.min(getHeight(), usable.height);
        setSize(width, height);

        // 先居中
        int x = usable.x + (usable.width - width) / 2;
        int y = usable.y + (usable.height - height) / 2;
        // 再夹紧，确保不越出可用区域边界
        x = Math.max(usable.x, Math.min(x, usable.x + usable.width - width));
        y = Math.max(usable.y, Math.min(y, usable.y + usable.height - height));
        setLocation(new Point(x, y));
    }

    /**
     * 创建北部控制面板：路径选择行与算法/操作行。
     *
     * @return 配置完成的面板
     */
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // 第一行：路径显示 + 文件/文件夹选择
        JPanel pathRow = new JPanel(new BorderLayout(5, 5));
        tfPath = new JTextField();
        tfPath.setEditable(false);
        btnSelectFile = new JButton("选择文件...");
        btnSelectDir = new JButton("选择文件夹...");
        JPanel pathButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        pathButtons.add(btnSelectFile);
        pathButtons.add(btnSelectDir);
        pathRow.add(new JLabel("已选路径："), BorderLayout.WEST);
        pathRow.add(tfPath, BorderLayout.CENTER);
        pathRow.add(pathButtons, BorderLayout.EAST);

        // 第二行：算法选择 + 操作按钮
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        actionRow.add(new JLabel("哈希算法："));
        cbAlgorithm = new JComboBox<>(ALGORITHMS);
        actionRow.add(cbAlgorithm);
        btnStart = new JButton("开始校验");
        btnExport = new JButton("导出报告");
        btnClear = new JButton("清空");
        actionRow.add(btnStart);
        actionRow.add(btnExport);
        actionRow.add(btnClear);

        panel.add(pathRow, BorderLayout.NORTH);
        panel.add(actionRow, BorderLayout.SOUTH);

        // 注册事件监听器（监听器内绝不直接执行哈希计算）
        btnSelectFile.addActionListener(e -> onSelectFile());
        btnSelectDir.addActionListener(e -> onSelectDirectory());
        btnStart.addActionListener(e -> onStartCheck());
        btnExport.addActionListener(e -> onExportReport());
        btnClear.addActionListener(e -> onClear());

        return panel;
    }

    /**
     * 创建中部结果显示区（带滚动条、等宽只读文本域）。
     *
     * @return 滚动面板
     */
    private JScrollPane createResultArea() {
        taResult = new JTextArea();
        taResult.setEditable(false);
        taResult.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        taResult.setLineWrap(false);
        taResult.setText(RESULT_HEADER + System.lineSeparator()
                + "----------------------------------------" + System.lineSeparator());

        JScrollPane scrollPane = new JScrollPane(taResult);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        return scrollPane;
    }

    /**
     * 创建南部状态面板：进度条 + 状态文字。
     *
     * @return 状态面板
     */
    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setString("0/0");

        lblStatus = new JLabel("就绪：请选择文件或文件夹后开始校验。");

        panel.add(progressBar, BorderLayout.CENTER);
        panel.add(lblStatus, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * 选择单个文件（FR-1）。
     */
    private void onSelectFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择需要校验的文件");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile();
            tfPath.setText(selectedFile.getAbsolutePath());
            lblStatus.setText("已选择文件：" + selectedFile.getName());
        }
    }

    /**
     * 选择文件夹（FR-1）。
     */
    private void onSelectDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择需要校验的文件夹");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile();
            tfPath.setText(selectedFile.getAbsolutePath());
            lblStatus.setText("已选择文件夹：" + selectedFile.getName());
        }
    }

    /**
     * 开始校验（FR-2/FR-3）。本方法在 EDT 执行，只做前置校验、状态切换与
     * 后台线程启动；扫描与并发哈希计算全部在 {@code hashcheck-batch} 线程完成。
     */
    private void onStartCheck() {
        if (selectedFile == null) {
            JOptionPane.showMessageDialog(this,
                    "请先通过“选择文件”或“选择文件夹”按钮选择校验目标。",
                    "尚未选择目标", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        final File target = selectedFile;
        final String algorithm = (String) cbAlgorithm.getSelectedItem();

        // 重置界面并禁止重复提交
        lastResults = null;
        nextResultIndex = 1;
        pendingResults.clear();
        taResult.setText(RESULT_HEADER + System.lineSeparator()
                + "----------------------------------------" + System.lineSeparator());
        // 恢复进度条初始模型（0..100），避免沿用上一次批量的文件总数作为最大值
        progressBar.setMaximum(100);
        progressBar.setValue(0);
        progressBar.setString("0/0");
        lblStatus.setText("校验中…");
        setControlsEnabled(false);

        // 守护线程：窗口关闭时即使任务未结束也不会阻止 JVM 退出
        Thread batchThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runBatchInBackground(target, algorithm);
            }
        }, "hashcheck-batch");
        batchThread.setDaemon(true);
        batchThread.start();
    }

    /**
     * 后台批量校验流程：扫描 → 并发计算（回调切 EDT 渲染）→ 收尾。
     *
     * <p>本方法运行在工作线程，任何界面更新都必须经过 invokeLater。</p>
     *
     * @param target    用户选择的文件或目录
     * @param algorithm 算法名称
     */
    private void runBatchInBackground(File target, String algorithm) {
        try {
            List<File> files = FileScaner.scanFiles(target);
            if (files.isEmpty()) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        lblStatus.setText("所选文件夹不包含任何文件。");
                        progressBar.setMaximum(100);
                        progressBar.setValue(0);
                        progressBar.setString("0/0");
                    }
                });
                return;
            }

            final int total = files.size();
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    progressBar.setMaximum(total);
                    progressBar.setValue(0);
                    progressBar.setString("0/" + total);
                }
            });

            // 回调在工作线程触发，这里通过 invokeLater 切回 EDT；
            // 多个工作线程的回调入队先后无保证，EDT 侧再按序号缓冲、连续刷出
            List<FileHashResult> results = service.checkAll(files, algorithm,
                    (completed, totalCount, latest) ->
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    enqueueAndFlushProgress(completed, totalCount, latest);
                                }
                            }));

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    finishBatch(results);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    lblStatus.setText("校验被中断。");
                    showError("批量校验过程被中断。");
                }
            });
        } catch (final Exception e) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    lblStatus.setText("校验失败。");
                    showError("校验过程发生错误：" + e.getMessage());
                }
            });
        } finally {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    setControlsEnabled(true);
                }
            });
        }
    }

    /**
     * 批量正常结束后的 EDT 收尾：缓存结果、统计成功/失败并更新状态栏。
     *
     * @param results 本次批量校验的全部结果（与输入顺序一致）
     */
    private void finishBatch(List<FileHashResult> results) {
        lastResults = results;
        int success = 0;
        int failure = 0;
        for (FileHashResult r : results) {
            if (r.isSuccess()) {
                success++;
            } else {
                failure++;
            }
        }
        lblStatus.setText("校验完成：成功 " + success + " 个，失败 " + failure
                + " 个，共 " + results.size() + " 个文件。可点击“导出报告”保存结果。");
    }

    /**
     * 在 EDT 上接收一个完成事件，并按序号连续刷出结果行与进度。
     *
     * <p>多个工作线程触发回调的先后没有保证（序号 N+1 的事件可能先到），
     * 因此先把事件放入按序号排序的缓冲 {@link #pendingResults}，
     * 再从下一个应显示序号 {@link #nextResultIndex} 起连续输出，
     * 保证结果行严格按 1→N 顺序出现、进度条数值单调不倒退。
     * 本方法必须在 EDT 调用。</p>
     *
     * @param completed 该事件对应的完成序号（1..total）
     * @param total     本次批量文件总数
     * @param latest    该序号对应的校验结果
     */
    private void enqueueAndFlushProgress(int completed, int total, FileHashResult latest) {
        pendingResults.put(completed, latest);
        while (!pendingResults.isEmpty() && pendingResults.firstKey() == nextResultIndex) {
            Map.Entry<Integer, FileHashResult> entry = pendingResults.pollFirstEntry();
            appendResultLine(entry.getKey(), entry.getValue());
            nextResultIndex++;
        }
        int shown = nextResultIndex - 1;
        progressBar.setValue(shown);
        progressBar.setString(shown + "/" + total);
    }

    /**
     * 在结果区追加一条结果行（FR-4：序号、绝对路径、算法、哈希值/错误信息）。
     * 本方法必须在 EDT 调用。
     *
     * @param index 从 1 开始的序号
     * @param result 单文件校验结果
     */
    private void appendResultLine(int index, FileHashResult result) {
        String tail;
        if (result.isSuccess()) {
            tail = result.getHash();
        } else {
            tail = "失败：" + result.getErrorMessage();
        }
        taResult.append("[" + index + "] " + result.getFilePath()
                + " | " + result.getAlgorithm()
                + " | " + tail + System.lineSeparator());
    }

    /**
     * 导出 txt 报告（FR-5）。无结果时提示；已存在文件需用户确认覆盖。
     */
    private void onExportReport() {
        if (lastResults == null || lastResults.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "暂无可导出的结果，请先执行一次校验。",
                    "没有可导出的结果", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导出校验报告（txt）");
        chooser.setSelectedFile(new File("哈希校验报告.txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File saveFile = chooser.getSelectedFile();
        if (saveFile.exists()) {
            int option = JOptionPane.showConfirmDialog(this,
                    "文件“" + saveFile.getName() + "”已存在，是否覆盖？",
                    "确认覆盖", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (option != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            String algorithm = (String) cbAlgorithm.getSelectedItem();
            ReportWriter.saveReport(saveFile, lastResults, algorithm);
            JOptionPane.showMessageDialog(this,
                    "报告已成功导出：" + System.lineSeparator() + saveFile.getAbsolutePath(),
                    "导出成功", JOptionPane.INFORMATION_MESSAGE);
            lblStatus.setText("报告已导出：" + saveFile.getAbsolutePath());
        } catch (IOException ex) {
            showError("报告导出失败：" + ex.getMessage());
        }
    }

    /**
     * 清空结果区、进度条与缓存结果（FR-4）。
     */
    private void onClear() {
        nextResultIndex = 1;
        pendingResults.clear();
        taResult.setText(RESULT_HEADER + System.lineSeparator()
                + "----------------------------------------" + System.lineSeparator());
        lastResults = null;
        progressBar.setMaximum(100);
        progressBar.setValue(0);
        progressBar.setString("0/0");
        lblStatus.setText("结果已清空。");
    }

    /**
     * 批量切换操作控件可用性，防止校验期间重复提交。
     *
     * @param enabled true 表示可操作（空闲），false 表示校验进行中
     */
    private void setControlsEnabled(boolean enabled) {
        btnSelectFile.setEnabled(enabled);
        btnSelectDir.setEnabled(enabled);
        cbAlgorithm.setEnabled(enabled);
        btnStart.setEnabled(enabled);
        btnExport.setEnabled(enabled);
        btnClear.setEnabled(enabled);
    }

    /**
     * 以错误对话框展示友好提示（FR-6）。
     *
     * @param message 错误信息
     */
    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "错误",
                JOptionPane.ERROR_MESSAGE);
    }

    // —— 以下为包级可见的最小访问器，供冒烟检查与代码走查使用，不构成公开 API ——

    /** 获取结果文本区（包级可见，便于冒烟检查）。 */
    JTextArea getResultArea() {
        return taResult;
    }

    /** 获取进度条（包级可见）。 */
    JProgressBar getProgressBar() {
        return progressBar;
    }

    /** 获取状态栏标签（包级可见）。 */
    JLabel getStatusLabel() {
        return lblStatus;
    }

    /** 获取最近一次校验结果（包级可见）。 */
    List<FileHashResult> getLastResults() {
        return lastResults;
    }

    /** 获取当前选择的文件或目录（包级可见）。 */
    File getSelectedFile() {
        return selectedFile;
    }
}
