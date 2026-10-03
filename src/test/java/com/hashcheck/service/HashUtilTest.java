package com.hashcheck.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HashUtil} 单元测试。
 *
 * <p>覆盖验收用例 T1（标准向量）、T2（一致性）、T3（篡改检测）、T5（空文件）
 * 以及 FR-6 规定的异常路径（不存在、目录、null、非法算法名）。</p>
 */
class HashUtilTest {

    /** 空文件 MD5 标准测试向量。 */
    private static final String MD5_EMPTY = "d41d8cd98f00b204e9800998ecf8427e";
    /** 内容 "abc" 的 MD5 标准测试向量。 */
    private static final String MD5_ABC = "900150983cd24fb0d6963f7d28e17f72";
    /** 空文件 SHA-256 标准测试向量。 */
    private static final String SHA256_EMPTY =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    /** 内容 "abc" 的 SHA-256 标准测试向量。 */
    private static final String SHA256_ABC =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @TempDir
    Path tempDir;

    /** 在临时目录中创建写入指定内容的文件。 */
    private File createFile(String name, byte[] content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.write(path, content);
        return path.toFile();
    }

    /** T1/AC-1：空文件与 "abc" 的 MD5 必须等于标准向量，长度 32 且为小写十六进制。 */
    @Test
    void testMd5StandardVectors() throws Exception {
        File emptyFile = createFile("empty.md5.bin", new byte[0]);
        File abcFile = createFile("abc.md5.txt", "abc".getBytes("UTF-8"));

        String emptyHash = HashUtil.getFileHash(emptyFile, "MD5");
        String abcHash = HashUtil.getFileHash(abcFile, "MD5");

        assertEquals(MD5_EMPTY, emptyHash, "空文件 MD5 向量不匹配");
        assertEquals(MD5_ABC, abcHash, "\"abc\" MD5 向量不匹配");
        assertEquals(32, abcHash.length(), "MD5 长度应为 32");
        assertTrue(abcHash.matches("^[0-9a-f]{32}$"), "MD5 必须是 32 位小写十六进制");
    }

    /** AC-2：空文件与 "abc" 的 SHA-256 必须等于标准向量，长度 64 且为小写十六进制。 */
    @Test
    void testSha256StandardVectors() throws Exception {
        File emptyFile = createFile("empty.sha.bin", new byte[0]);
        File abcFile = createFile("abc.sha.txt", "abc".getBytes("UTF-8"));

        String emptyHash = HashUtil.getFileHash(emptyFile, "SHA-256");
        String abcHash = HashUtil.getFileHash(abcFile, "SHA-256");

        assertEquals(SHA256_EMPTY, emptyHash, "空文件 SHA-256 向量不匹配");
        assertEquals(SHA256_ABC, abcHash, "\"abc\" SHA-256 向量不匹配");
        assertEquals(64, abcHash.length(), "SHA-256 长度应为 64");
        assertTrue(abcHash.matches("^[0-9a-f]{64}$"), "SHA-256 必须是 64 位小写十六进制");
    }

    /** T2/AC-3：同一文件同一算法计算两次，结果必须完全一致（两种算法各验证一次）。 */
    @Test
    void testHashConsistency() throws Exception {
        File file = createFile("consistency.txt", "hello-hash-check".getBytes("UTF-8"));

        assertEquals(HashUtil.getFileHash(file, "MD5"),
                HashUtil.getFileHash(file, "MD5"), "MD5 两次计算结果不一致");
        assertEquals(HashUtil.getFileHash(file, "SHA-256"),
                HashUtil.getFileHash(file, "SHA-256"), "SHA-256 两次计算结果不一致");
    }

    /** T3/AC-3：文件追加 1 字节后，MD5 与 SHA-256 哈希均必须改变（篡改检测）。 */
    @Test
    void testTamperDetection() throws Exception {
        File file = createFile("tamper.bin", "original-content".getBytes("UTF-8"));

        String md5Before = HashUtil.getFileHash(file, "MD5");
        String shaBefore = HashUtil.getFileHash(file, "SHA-256");

        Files.write(file.toPath(), "original-contentX".getBytes("UTF-8"));

        String md5After = HashUtil.getFileHash(file, "MD5");
        String shaAfter = HashUtil.getFileHash(file, "SHA-256");

        assertNotEquals(md5Before, md5After, "内容被修改后 MD5 必须改变");
        assertNotEquals(shaBefore, shaAfter, "内容被修改后 SHA-256 必须改变");
    }

    /** T5/AC-5：0 字节文件返回固定空摘要向量且不抛异常。 */
    @Test
    void testEmptyFileReturnsFixedDigest() throws Exception {
        File zeroByteFile = createFile("zero.bin", new byte[0]);

        assertEquals(MD5_EMPTY, HashUtil.getFileHash(zeroByteFile, "MD5"));
        assertEquals(SHA256_EMPTY, HashUtil.getFileHash(zeroByteFile, "SHA-256"));
    }

    /** 分块边界验证：大于 4096 字节的文件结果必须与 JDK 直接对全量字节摘要一致。 */
    @Test
    void testLargerFileMatchesMessageDigestOverFullBytes() throws Exception {
        byte[] content = new byte[HashUtil.BUFFER_SIZE * 3 + 17];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 251);
        }
        File largeFile = createFile("large.bin", content);

        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        String expected = toHex(md.digest(content));

        assertEquals(expected, HashUtil.getFileHash(largeFile, "MD5"),
                "跨多个分块的文件哈希与全量摘要不一致");
    }

    /** T6/AC-6：不存在的文件必须抛出 IOException。 */
    @Test
    void testNonExistentFileThrowsIOException() {
        File missing = tempDir.resolve("does-not-exist.dat").toFile();

        IOException ex = assertThrows(IOException.class,
                () -> HashUtil.getFileHash(missing, "MD5"));
        assertTrue(ex.getMessage().contains("不存在"), "异常信息应说明文件不存在");
    }

    /** T6/AC-6：以目录充当计算目标必须抛出 IOException。 */
    @Test
    void testDirectoryThrowsIOException() {
        File dir = tempDir.toFile();

        assertThrows(IOException.class, () -> HashUtil.getFileHash(dir, "MD5"));
    }

    /** AC-6：null 文件参数必须抛出 NullPointerException。 */
    @Test
    void testNullFileThrowsNpe() {
        assertThrows(NullPointerException.class,
                () -> HashUtil.getFileHash(null, "MD5"));
    }

    /** AC-6：null 算法参数必须抛出 NullPointerException。 */
    @Test
    void testNullAlgorithmThrowsNpe() throws Exception {
        File file = createFile("a.txt", "a".getBytes("UTF-8"));
        assertThrows(NullPointerException.class,
                () -> HashUtil.getFileHash(file, null));
    }

    /** AC-6：非法算法名必须抛出 NoSuchAlgorithmException。 */
    @Test
    void testIllegalAlgorithmThrows() throws Exception {
        File file = createFile("b.txt", "b".getBytes("UTF-8"));

        assertThrows(NoSuchAlgorithmException.class,
                () -> HashUtil.getFileHash(file, "NOT-A-REAL-ALGORITHM"));
    }

    /** 测试辅助：将字节数组转小写十六进制（与生产代码独立的一份参考实现）。 */
    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
