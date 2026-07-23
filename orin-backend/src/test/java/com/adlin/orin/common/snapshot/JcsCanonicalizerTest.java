package com.adlin.orin.common.snapshot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RFC 8785 (JSON Canonicalization Scheme) 实现回归。
 *
 * <p>采用 RFC 8785 Appendix G 风格的 test vectors；
 * 跨语言（Java vs Python）字节级一致是 ADR-002 §D-2.4.2 的硬要求，
 * 故本测试同时校验：
 * <ul>
 *   <li>key 排序（UTF-16 code unit 升序）；</li>
 *   <li>array 顺序保留（不重排）；</li>
 *   <li>数字 / 布尔 / null 字符层表示；</li>
 *   <li>字符串转义（控制字符、quote、backslash）；</li>
 *   <li>envelope 内嵌 snapshotSchemaVersion 时 digest 稳定；</li>
 * </ul>
 */
@DisplayName("RFC 8785 JCS + SHA-256 回归")
class JcsCanonicalizerTest {

    @Test
    @DisplayName("V1 空对象 → 空对象")
    void emptyObject() {
        assertEquals("{}", JcsCanonicalizer.canonicalize("{}"));
        assertEquals("{}", JcsCanonicalizer.canonicalize("{ }"));
    }

    @Test
    @DisplayName("V2 单键值对")
    void singleKey() {
        assertEquals("{\"a\":1}", JcsCanonicalizer.canonicalize("{\"a\":1}"));
        assertEquals("{\"a\":1}", JcsCanonicalizer.canonicalize("{\"a\": 1 }"));
    }

    @Test
    @DisplayName("V3 键按 UTF-16 升序排序")
    void keysSortedByUtf16() {
        // 输入 b,a → 必须输出 a,b
        assertEquals("{\"a\":1,\"b\":2}",
                JcsCanonicalizer.canonicalize("{\"b\":2,\"a\":1}"));
        // "a" < "aa" < "ab"
        assertEquals("{\"a\":1,\"aa\":2,\"ab\":3}",
                JcsCanonicalizer.canonicalize("{\"ab\":3,\"aa\":2,\"a\":1}"));
    }

    @Test
    @DisplayName("V4 数组顺序保留：JCS 不排序 array")
    void arrayOrderPreserved() {
        assertEquals("{\"y\":[3,2,1]}",
                JcsCanonicalizer.canonicalize("{\"y\":[3,2,1]}"));
        assertEquals("{\"y\":[1,2,3]}",
                JcsCanonicalizer.canonicalize("{\"y\":[1,2,3]}"));
    }

    @Test
    @DisplayName("V5 嵌套对象 + 数组递归")
    void nestedObject() {
        assertEquals("{\"a\":1,\"b\":{\"c\":2,\"d\":3}}",
                JcsCanonicalizer.canonicalize("{\"b\":{\"d\":3,\"c\":2},\"a\":1}"));
    }

    @Test
    @DisplayName("V6 字符串换行符转义为 \\n")
    void stringNewlineEscape() {
        assertEquals("{\"x\":\"hello\\nworld\"}",
                JcsCanonicalizer.canonicalize("{\"x\":\"hello\\nworld\"}"));
    }

    @Test
    @DisplayName("V7 控制字符转义为 \\uXXXX")
    void stringControlCharEscape() {
        assertEquals("{\"x\":\"\\u0001\"}",
                JcsCanonicalizer.canonicalize("{\"x\":\"\\u0001\"}"));
    }

    @Test
    @DisplayName("V8 字符串 quote / backslash 转义")
    void stringQuoteEscape() {
        assertEquals("{\"x\":\"a\\\"b\\\\c\"}",
                JcsCanonicalizer.canonicalize("{\"x\":\"a\\\"b\\\\c\"}"));
    }

    @Test
    @DisplayName("V9 布尔 / null 字面表示")
    void booleansAndNull() {
        // UTF-16: 'f'(0x66) < 'n'(0x6E) < 't'(0x74) → 排序后 f, n, t
        assertEquals("{\"f\":false,\"n\":null,\"t\":true}",
                JcsCanonicalizer.canonicalize("{\"n\":null,\"t\":true,\"f\":false}"));
    }

    @Test
    @DisplayName("V10 数字字面表示：0.0 保留为 '0.0'")
    void numbersLiteral() {
        assertEquals("{\"a\":0.0}",
                JcsCanonicalizer.canonicalize("{\"a\":0.0}"));
        assertEquals("{\"a\":-1.5}",
                JcsCanonicalizer.canonicalize("{\"a\":-1.5}"));
        assertEquals("{\"a\":1234567890}",
                JcsCanonicalizer.canonicalize("{\"a\":1234567890}"));
    }

    @Test
    @DisplayName("V11 Unicode 字符串保持原始字节（不做 NFC/NFKC）")
    void noNfcNormalization() {
        // 用显式 Unicode escape 避免源码文件被 IDE/编译器 NFC normalize 后失去区分度
        // U+00E9 (é 单 code point, NFC)
        // U+0065 + U+0301 (e + combining acute, NFD)
        // 视觉一致但字节不同；RFC 8785 §3.1 不做 normalization，原始字节保留
        String nfc = "{\"x\":\"é\"}";
        String decomposed = "{\"x\":\"é\"}";
        assertEquals("{\"x\":\"é\"}", JcsCanonicalizer.canonicalize(nfc));
        assertEquals("{\"x\":\"é\"}", JcsCanonicalizer.canonicalize(decomposed));
        assertNotEquals(JcsCanonicalizer.canonicalize(nfc), JcsCanonicalizer.canonicalize(decomposed));
    }

    @Test
    @DisplayName("V12 digest envelope 内嵌 snapshotSchemaVersion 时 digest 稳定")
    void envelopeStableAcrossOrdering() {
        String envelopeA = "{\"snapshotSchemaVersion\":1,\"config\":{\"a\":1,\"b\":2},"
                + "\"model\":{},\"tools\":[],\"knowledge\":[],\"workflow\":{},\"secretRefs\":[]}";
        String envelopeB = "{\"secretRefs\":[],\"workflow\":{},"
                + "\"knowledge\":[],\"tools\":[],\"model\":{},\"config\":{\"b\":2,\"a\":1},"
                + "\"snapshotSchemaVersion\":1}";
        // 两个 envelope 字段顺序不同但内容相同 → canonicalize 后字节级一致
        assertEquals(JcsCanonicalizer.canonicalize(envelopeA), JcsCanonicalizer.canonicalize(envelopeB));
        // 对应的 SHA-256 digest 也一致
        assertEquals(Sha256Digest.hex(JcsCanonicalizer.canonicalize(envelopeA)),
                Sha256Digest.hex(JcsCanonicalizer.canonicalize(envelopeB)));
    }

    @Test
    @DisplayName("V13 修改 payload 任意一个字节 → digest 完全不同")
    void anyMutation_changesDigest() {
        String envelope1 = "{\"snapshotSchemaVersion\":1,\"config\":{\"a\":1},\"model\":{},"
                + "\"tools\":[],\"knowledge\":[],\"workflow\":{},\"secretRefs\":[]}";
        String envelope2 = "{\"snapshotSchemaVersion\":1,\"config\":{\"a\":2},\"model\":{},"
                + "\"tools\":[],\"knowledge\":[],\"workflow\":{},\"secretRefs\":[]}";
        assertNotEquals(Sha256Digest.hex(JcsCanonicalizer.canonicalize(envelope1)),
                Sha256Digest.hex(JcsCanonicalizer.canonicalize(envelope2)));
    }

    @Test
    @DisplayName("V14 SHA-256 hex 输出是 64-char lowercase")
    void sha256HexFormat() {
        String d = Sha256Digest.hex("hello");
        assertEquals(64, d.length());
        assertTrue(d.matches("[0-9a-f]{64}"));
        // 已知向量：sha256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", d);
    }

    @Test
    @DisplayName("V15 空输入抛 SNAPSHOT_CANONICALIZE_FAILED")
    void nullInput_throwsBusinessException() {
        try {
            JcsCanonicalizer.canonicalize((String) null);
        } catch (com.adlin.orin.common.exception.BusinessException e) {
            assertEquals(com.adlin.orin.common.exception.ErrorCode.SNAPSHOT_CANONICALIZE_FAILED,
                    e.getErrorCode());
            return;
        }
        throw new AssertionError("expected BusinessException");
    }
}
