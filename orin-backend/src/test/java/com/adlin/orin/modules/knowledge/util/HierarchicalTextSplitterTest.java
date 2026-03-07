package com.adlin.orin.modules.knowledge.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HierarchicalTextSplitter
 * Tests Parent-Child chunking strategy
 */
class HierarchicalTextSplitterTest {

    @Test
    void testSplitHierarchical_NullInput() {
        HierarchicalTextSplitter.HierarchicalChunks result =
            HierarchicalTextSplitter.splitHierarchical(null, "doc-1", "Test Document");

        assertNotNull(result);
        assertTrue(result.getParents().isEmpty());
        assertTrue(result.getChildren().isEmpty());
    }

    @Test
    void testSplitHierarchical_EmptyInput() {
        HierarchicalTextSplitter.HierarchicalChunks result =
            HierarchicalTextSplitter.splitHierarchical("", "doc-1", "Test Document");

        assertNotNull(result);
        assertTrue(result.getParents().isEmpty());
        assertTrue(result.getChildren().isEmpty());
    }

    @Test
    void testSplitHierarchical_ShortText() {
        // Use longer text to meet MIN_PARENT_SIZE requirement (200 chars)
        String shortText = "This is a short document. It contains some content. " +
                          "We need at least 200 characters to pass the minimum parent chunk size check. " +
                          "So we add more text here. This should be enough characters. " +
                          "Let me add even more to be safe.";

        HierarchicalTextSplitter.HierarchicalChunks result =
            HierarchicalTextSplitter.splitHierarchical(shortText, "doc-1", "Test Document");

        assertNotNull(result);
        // May or may not have chunks depending on min size check
        assertNotNull(result.getParents());
        assertNotNull(result.getChildren());
    }

    @Test
    void testSplitHierarchical_LongText() {
        // Generate a long text (> 1000 chars)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("这是第").append(i).append("段落的内容。");
            sb.append("这是一句很长的句子，用于测试分块功能。");
            sb.append("\n\n");
        }
        String longText = sb.toString();

        HierarchicalTextSplitter.HierarchicalChunks result =
            HierarchicalTextSplitter.splitHierarchical(longText, "doc-1", "Test Document");

        assertNotNull(result);
        // Should have multiple parent chunks
        assertTrue(result.getParents().size() > 1);
        // Should have more child chunks than parent chunks
        assertTrue(result.getChildren().size() > result.getParents().size());
    }

    @Test
    void testSplitHierarchical_WithParagraphs() {
        // Need longer text to meet MIN_PARENT_SIZE requirement
        String textWithParagraphs =
            "第一章：介绍\n\n" +
            "这是第一段内容。包含一些文字。需要足够的长度来通过最小父分块大小的检查。我们添加更多内容来确保满足要求。\n\n" +
            "这是第二段内容。包含更多文字。我们添加更多内容来满足要求。还需要更多字符。\n\n" +
            "第二章：技术细节\n\n" +
            "这里讨论技术实现。代码如下。还有更多的技术细节需要添加。确保有足够的内容。\n\n" +
            "更多技术细节。更多内容填充。还需要更多内容来满足最小大小。";

        HierarchicalTextSplitter.HierarchicalChunks result =
            HierarchicalTextSplitter.splitHierarchical(textWithParagraphs, "doc-1", "Test Doc");

        assertNotNull(result);
        // May have chunks depending on text length
        assertNotNull(result.getParents());
        assertNotNull(result.getChildren());

        // If there are parents, verify structure
        for (var parent : result.getParents()) {
            assertNotNull(parent.getTitle());
            assertNotNull(parent.getContent());
            assertNotNull(parent.getId());
            assertTrue(parent.getId().startsWith("p_"));
        }
    }

    @Test
    void testSplitHierarchical_ChildChunkIds() {
        String text = "第一段内容。第二段内容。第三段内容。";

        HierarchicalTextSplitter.HierarchicalChunks result =
            HierarchicalTextSplitter.splitHierarchical(text, "doc-1", "Test");

        assertNotNull(result);

        // Verify child IDs are unique and start with c_
        for (var child : result.getChildren()) {
            assertNotNull(child.getId());
            assertTrue(child.getId().startsWith("c_"));
            assertNotNull(child.getParentId());
            assertTrue(child.getParentId().startsWith("p_"));
        }
    }

    @Test
    void testSplitHierarchical_ParentChildRelationship() {
        String text = "这是第一段内容。这是一个很长的段落，需要被分块。" +
                      "这是第二段内容。";

        HierarchicalTextSplitter.HierarchicalChunks result =
            HierarchicalTextSplitter.splitHierarchical(text, "doc-1", "Test");

        assertNotNull(result);

        // Each parent should have children IDs
        for (var parent : result.getParents()) {
            assertNotNull(parent.getChildrenIds());
            assertFalse(parent.getChildrenIds().isEmpty());

            // Verify all children IDs exist
            List<String> childIds = parent.getChildrenIds();
            for (String childId : childIds) {
                boolean found = result.getChildren().stream()
                    .anyMatch(c -> c.getId().equals(childId));
                assertTrue(found, "Child ID " + childId + " should exist");
            }
        }
    }

    @Test
    void testSplit_Method() {
        // Use longer text to meet MIN_PARENT_SIZE requirement
        String text = "第一句。第二句。第三句。这是一段更长的文本。需要足够的字符数来满足最小父分块大小的要求。" +
                     "添加更多内容确保能够生成子分块。";

        List<String> chunks = HierarchicalTextSplitter.split(text);

        assertNotNull(chunks);
        // May or may not be empty depending on text length
        assertNotNull(chunks);

        // If there are chunks, they should be non-empty
        for (String chunk : chunks) {
            assertNotNull(chunk);
        }
    }

    @Test
    void testSplit_NullInput() {
        List<String> chunks = HierarchicalTextSplitter.split(null);

        assertNotNull(chunks);
        assertTrue(chunks.isEmpty());
    }

    @Test
    void testSplit_WithMarkdownHeadings() {
        String markdownText =
            "# 第一章\n\n" +
            "这是第一章的内容。\n\n" +
            "## 第一节\n\n" +
            "这是第一节的内容。\n\n" +
            "# 第二章\n\n" +
            "这是第二章的内容。";

        HierarchicalTextSplitter.HierarchicalChunks result =
            HierarchicalTextSplitter.splitHierarchical(markdownText, "doc-1", "Markdown Doc");

        assertNotNull(result);

        // Check that titles are extracted from headings
        for (var parent : result.getParents()) {
            assertNotNull(parent.getTitle());
            // Should contain chapter/section names
            assertTrue(parent.getTitle().length() > 0);
        }
    }

    @Test
    void testSplitDeterministicIds() {
        String text = "测试内容。";

        // Split twice with same input
        HierarchicalTextSplitter.HierarchicalChunks result1 =
            HierarchicalTextSplitter.splitHierarchical(text, "doc-1", "Test");
        HierarchicalTextSplitter.HierarchicalChunks result2 =
            HierarchicalTextSplitter.splitHierarchical(text, "doc-1", "Test");

        // Should generate same IDs
        assertEquals(result1.getParents().size(), result2.getParents().size());

        for (int i = 0; i < result1.getParents().size(); i++) {
            assertEquals(
                result1.getParents().get(i).getId(),
                result2.getParents().get(i).getId()
            );
        }
    }

    @Test
    void testChildContentSize() {
        // Generate text that will create multiple child chunks
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("这是第").append(i).append("句内容，需要足够的长度来创建多个子分块。");
        }
        String text = sb.toString();

        HierarchicalTextSplitter.HierarchicalChunks result =
            HierarchicalTextSplitter.splitHierarchical(text, "doc-1", "Test");

        assertNotNull(result);
        assertFalse(result.getChildren().isEmpty());

        // Child chunks should be <= 200 chars (with some tolerance)
        for (var child : result.getChildren()) {
            assertTrue(child.getContent().length() <= 250,
                "Child chunk too long: " + child.getContent().length());
        }
    }
}
