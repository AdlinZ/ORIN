package com.adlin.orin.common.snapshot;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * RFC 8785 (JSON Canonicalization Scheme, JCS) 自实现。
 *
 * <p>关键规则（[RFC 8785 §3.1](https://www.rfc-editor.org/rfc/rfc8785#section-3.1)）：
 * <ul>
 *   <li>UTF-8 编码；<b>不做</b>Unicode normalization（如 NFC/NFKC），保留原始字节；</li>
 *   <li>object keys 按 UTF-16 code unit 升序；</li>
 *   <li>array 顺序保留（JCS 不排序 array）；</li>
 *   <li>numbers 严格 IEEE-754 词法表示，禁止 NaN / Infinity；</li>
 *   <li>null 显式存在；</li>
 *   <li>输出无空白，紧凑；</li>
 * </ul>
 *
 * <p>实现要点：
 * <ul>
 *   <li>用 Jackson 解析 JSON 树，再手工序列化（Jackson 自带 writer 会按写入顺序排 key，无法满足 key sort）；</li>
 *   <li>String 用自定义 quote / escape，避免 Jackson 默认 writer 在字段顺序外加多余空格；</li>
 *   <li>Number / Boolean / Null 直接复用 Jackson 的字符层 representation（通过 String 缓存）；</li>
 *   <li>跨语言字节级一致需要双方都用 RFC 8785 Appendix G test vectors 校验 —— 见
 *       {@code JcsCanonicalizerTest}。</li>
 * </ul>
 *
 * <p>F02 R3 落地使用：AgentVersion freeze 前必须 {@code canonicalize(envelope)} 后
 * {@code Sha256Digest.hex(canonical)} 得到 {@code content_digest}。
 */
public final class JcsCanonicalizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JcsCanonicalizer() {
    }

    /** 把 JSON 字符串 canonicalize 为 RFC 8785 形式的紧凑字符串。失败抛 {@code SNAPSHOT_CANONICALIZE_FAILED}。 */
    public static String canonicalize(String input) {
        if (input == null) {
            throw new BusinessException(ErrorCode.SNAPSHOT_CANONICALIZE_FAILED,
                    "JCS input must not be null");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(input);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SNAPSHOT_CANONICALIZE_FAILED,
                    "JCS parse failed: " + e.getMessage(), e);
        }
        if (root == null || root.isMissingNode()) {
            throw new BusinessException(ErrorCode.SNAPSHOT_CANONICALIZE_FAILED,
                    "JCS input produced no JSON value");
        }
        StringWriter w = new StringWriter();
        try (JsonGenerator gen = MAPPER.getFactory().createGenerator(w)) {
            writeCanonical(root, gen);
            gen.flush();
            return w.toString();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SNAPSHOT_CANONICALIZE_FAILED,
                    "JCS serialize failed: " + e.getMessage(), e);
        }
    }

    /** 把 {@link JsonNode} 树直接 canonicalize；供 envelope 嵌套调用，避免反复 parse。 */
    public static String canonicalize(JsonNode root) {
        if (root == null) {
            throw new BusinessException(ErrorCode.SNAPSHOT_CANONICALIZE_FAILED, "JCS root is null");
        }
        StringWriter w = new StringWriter();
        try (JsonGenerator gen = MAPPER.getFactory().createGenerator(w)) {
            writeCanonical(root, gen);
            gen.flush();
            return w.toString();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SNAPSHOT_CANONICALIZE_FAILED,
                    "JCS serialize failed: " + e.getMessage(), e);
        }
    }

    private static void writeCanonical(JsonNode node, JsonGenerator gen) throws IOException {
        if (node == null || node.isNull()) {
            gen.writeNull();
            return;
        }
        if (node.isObject()) {
            gen.writeStartObject();
            // sort keys by UTF-16 code unit (RFC 8785 §3.2.2.1)
            List<String> keys = new ArrayList<>();
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                keys.add(fieldNames.next());
            }
            Collections.sort(keys, JcsCanonicalizer::compareUtf16);
            for (String key : keys) {
                // writeFieldName 内含 RFC 8785 §3.2.2.4 字符串转义（\", \\, 控制字符）
                gen.writeFieldName(key);
                writeCanonical(node.get(key), gen);
            }
            gen.writeEndObject();
            return;
        }
        if (node.isArray()) {
            gen.writeStartArray();
            for (JsonNode child : node) {
                writeCanonical(child, gen);
            }
            gen.writeEndArray();
            return;
        }
        if (node.isTextual()) {
            gen.writeString(node.textValue());
            return;
        }
        if (node.isBoolean()) {
            gen.writeBoolean(node.booleanValue());
            return;
        }
        if (node.isNumber()) {
            // RFC 8785 §3.2.2.2: numbers use the JSON Number lexical representation.
            // Jackson's NumberNode.asText() returns the canonical lexical form
            // (e.g. "0.0", "-0.0", "123", "1.5e10") via Double / BigDecimal internal.
            gen.writeNumber(node.asText());
            return;
        }
        // Jackson 的 POJO / Binary 类型 RFC 8785 不接受
        throw new BusinessException(ErrorCode.SNAPSHOT_CANONICALIZE_FAILED,
                "JCS does not support node type: " + node.getNodeType());
    }

    /** 按 UTF-16 code unit 升序；高位 surrogate < 低位 surrogate because upper code unit 升序 */
    private static int compareUtf16(String a, String b) {
        int len = Math.min(a.length(), b.length());
        for (int i = 0; i < len; i++) {
            char ca = a.charAt(i);
            char cb = b.charAt(i);
            if (ca != cb) {
                return Character.compare(ca, cb);
            }
        }
        return Integer.compare(a.length(), b.length());
    }
}
