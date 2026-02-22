package com.adlin.orin.modules.knowledge.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 简单的文本切分工具
 * 用于将长文本切分为小段，以便进行向量化
 */
public class SimpleTextSplitter {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP = 50;

    /**
     * 将文本切分为片段
     *
     * @param text 输入文本
     * @return 文本片段列表
     */
    public static List<String> split(String text) {
        return split(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * 将文本切分为片段
     *
     * @param text      输入文本
     * @param chunkSize 片段大小 (字符数)
     * @param overlap   重叠大小 (字符数)
     * @return 文本片段列表
     */
    public static List<String> split(String text, int chunkSize, int overlap) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> chunks = new ArrayList<>();
        int length = text.length();
        int start = 0;

        while (start < length) {
            int end = Math.min(start + chunkSize, length);

            // 尽量在标点符号处切分，避免截断单词或句子
            // 如果不是最后一段，且截断位置不是空白字符，往回找标点
            if (end < length) {
                int lastPunctuation = findLastPunctuation(text, start, end);
                if (lastPunctuation > start + chunkSize / 2) { // 至少保留一半长度，防止切分过细
                    end = lastPunctuation + 1; // 包含标点
                }
            }

            chunks.add(text.substring(start, end).trim());

            // 移动 start 指针，考虑重叠
            // 确保 start 至少向前移动一步，防止死循环
            int nextStart = end - overlap;
            if (nextStart <= start) {
                start = end;
            } else {
                start = nextStart;
            }
        }

        return chunks;
    }

    private static int findLastPunctuation(String text, int start, int end) {
        String punctuations = "。.!?！？\n";
        for (int i = end - 1; i > start; i--) {
            if (punctuations.indexOf(text.charAt(i)) != -1) {
                return i;
            }
        }
        return -1;
    }
}
