package com.hashcheck.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FileHashResult} 不可变模型单元测试。
 */
class FileHashResultTest {

    @TempDir
    Path tempDir;

    /** 成功工厂：字段全部可正确读取，状态为成功且无错误信息。 */
    @Test
    void testSuccessFactoryAndGetters() {
        File file = tempDir.resolve("a.txt").toFile();
        FileHashResult result = FileHashResult.success(file, "MD5", "abc123");

        assertEquals(file.getAbsolutePath(), result.getFilePath());
        assertEquals("MD5", result.getAlgorithm());
        assertEquals("abc123", result.getHash());
        assertTrue(result.isSuccess());
        assertNull(result.getErrorMessage());
    }

    /** 失败工厂：哈希为 null，状态为失败，错误原因可读。 */
    @Test
    void testFailureFactoryAndGetters() {
        File file = tempDir.resolve("missing.bin").toFile();
        FileHashResult result = FileHashResult.failure(file, "SHA-256", "FileNotFoundException: 不存在");

        assertEquals(file.getAbsolutePath(), result.getFilePath());
        assertEquals("SHA-256", result.getAlgorithm());
        assertNull(result.getHash());
        assertFalse(result.isSuccess());
        assertEquals("FileNotFoundException: 不存在", result.getErrorMessage());
    }

    /** toString 在成功与失败两种状态下均包含关键中文信息。 */
    @Test
    void testToStringContainsEssentialInfo() {
        File ok = tempDir.resolve("ok.txt").toFile();
        File bad = tempDir.resolve("bad.txt").toFile();

        String successText = FileHashResult.success(ok, "MD5", "deadbeef").toString();
        String failureText = FileHashResult.failure(bad, "MD5", "IO 错误").toString();

        assertTrue(successText.contains("校验成功"));
        assertTrue(successText.contains("deadbeef"));
        assertTrue(failureText.contains("校验失败"));
        assertTrue(failureText.contains("IO 错误"));
    }

    /** 静态工厂对必填入参为 null 时快速失败。 */
    @Test
    void testFactoriesRejectNulls() {
        File file = tempDir.resolve("x.txt").toFile();

        assertThrows(NullPointerException.class,
                () -> FileHashResult.success(null, "MD5", "hash"));
        assertThrows(NullPointerException.class,
                () -> FileHashResult.success(file, null, "hash"));
        assertThrows(NullPointerException.class,
                () -> FileHashResult.success(file, "MD5", null));
        assertThrows(NullPointerException.class,
                () -> FileHashResult.failure(null, "MD5", "原因"));
        assertThrows(NullPointerException.class,
                () -> FileHashResult.failure(file, null, "原因"));
        assertThrows(NullPointerException.class,
                () -> FileHashResult.failure(file, "MD5", null));
    }
}
