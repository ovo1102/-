package com.hashcheck.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件扫描类。
 *
 * <p>负责区分输入是“单个文件”还是“文件夹”，并对文件夹递归收集其中的全部普通文件。
 * 对无权限访问的目录（{@link File#listFiles()} 返回 {@code null}）做空值保护并跳过，
 * 保证扫描过程不会因个别目录不可访问而失败。</p>
 *
 * <p>类名 {@code FileScaner} 沿用项目策划书中的拼写（标准拼写应为 Scanner），
 * 以保证与策划书及验收材料一致。</p>
 *
 * <p>本类为无状态静态工具类，不可实例化、不可继承。</p>
 *
 * @author 本地文件完整性哈希校验系统
 * @version 1.0.0
 */
public final class FileScaner {

    /**
     * 私有构造方法，禁止实例化工具类。
     */
    private FileScaner() {
    }

    /**
     * 扫描给定的文件或目录，收集其中的全部普通文件。
     *
     * <p>行为约定：</p>
     * <ul>
     *   <li>{@code root} 是普通文件：返回仅包含该文件的单元素列表；</li>
     *   <li>{@code root} 是目录：递归遍历所有层级的子目录，返回全部普通文件
     *       （结果中不含任何目录）；空子目录不产生任何元素；</li>
     *   <li>遇到无权限目录（{@code listFiles()} 返回 null）时跳过该分支继续扫描；</li>
     *   <li>空目录返回空列表（不返回 null）。</li>
     * </ul>
     *
     * @param root 待扫描的文件或目录，不能为 null，且必须存在
     * @return 非 null 的 {@link ArrayList}，包含扫描到的全部文件
     * @throws IllegalArgumentException 当 root 为 null 时
     * @throws FileNotFoundException    当 root 路径不存在时（IOException 子类）
     * @throws IOException              当扫描过程发生其他 IO 错误时
     */
    public static List<File> scanFiles(File root) throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("扫描根路径不能为 null");
        }
        if (!root.exists()) {
            throw new FileNotFoundException("扫描路径不存在: " + root.getAbsolutePath());
        }

        List<File> result = new ArrayList<>();
        if (root.isFile()) {
            // 单个文件直接返回单元素列表
            result.add(root);
            return result;
        }
        if (root.isDirectory()) {
            scanDirectory(root, result);
        }
        // 既不是文件也不是目录的特殊路径（理论上极少出现）返回空列表
        return result;
    }

    /**
     * 递归扫描单个目录，将其中的普通文件收集到结果列表。
     *
     * @param dir    待扫描目录
     * @param result 收集结果的列表（方法内直接添加元素）
     */
    private static void scanDirectory(File dir, List<File> result) {
        File[] children = dir.listFiles();
        // listFiles() 返回 null 表示目录不可读/无权限或发生 IO 错误：空值保护，跳过该分支
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isFile()) {
                result.add(child);
            } else if (child.isDirectory()) {
                // 目录本身不计入结果，递归收集其子代中的普通文件
                scanDirectory(child, result);
            }
            // 既非文件也非目录的特殊文件（如符号链接异常对象）直接跳过
        }
    }
}
