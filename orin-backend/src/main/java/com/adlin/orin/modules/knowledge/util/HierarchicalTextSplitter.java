package com.adlin.orin.modules.knowledge.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parent-Child Hierarchical Text Splitter
 *
 * Parent chunks: ~1000 tokens, represent full semantic sections
 * Child chunks: ~200 tokens, optimized for precise vector retrieval
 *
 * Chunking Process:
 * 1. Split document into parent chunks using semantic separators
 * 2. Split each parent into child chunks
 * 3. Only child chunks are embedded and stored in vector DB
 * 4. Parent chunks stored in document store (DB)
 */
public class HierarchicalTextSplitter {

    // Parent chunk: ~1000 characters (roughly 1000 tokens in Chinese)
    private static final int PARENT_CHUNK_SIZE = 1000;

    // Child chunk: ~200 characters (roughly 200 tokens)
    private static final int CHILD_CHUNK_SIZE = 200;

    // Minimum parent chunk size to avoid too small chunks
    private static final int MIN_PARENT_SIZE = 200;

    // Default configurable parameters
    private static final int DEFAULT_PARENT_SIZE = 800;
    private static final int DEFAULT_CHILD_SIZE = 500;
    private static final int DEFAULT_OVERLAP = 50;

    // Semantic separators (priority from high to low)
    private static final String[] SEPARATORS = {
        "\n\n",   // Double newline (paragraph)
        "\n",     // Single newline
        "。",      // Chinese period
        ".",      // English period
        " ",      // Space
        ""        // Fallback: char by char
    };

    /**
     * Result containing both parent and child chunks
     */
    public static class HierarchicalChunks {
        private final List<ParentChunk> parents;
        private final List<ChildChunk> children;

        public HierarchicalChunks(List<ParentChunk> parents, List<ChildChunk> children) {
            this.parents = parents;
            this.children = children;
        }

        public List<ParentChunk> getParents() {
            return parents;
        }

        public List<ChildChunk> getChildren() {
            return children;
        }
    }

    /**
     * Parent chunk representing a semantic section
     */
    public static class ParentChunk {
        private final String id;
        private final String content;
        private final int position;
        private final String title;
        private final List<String> childrenIds;

        public ParentChunk(String id, String content, int position, String title, List<String> childrenIds) {
            this.id = id;
            this.content = content;
            this.position = position;
            this.title = title;
            this.childrenIds = childrenIds;
        }

        public String getId() { return id; }
        public String getContent() { return content; }
        public int getPosition() { return position; }
        public String getTitle() { return title; }
        public List<String> getChildrenIds() { return childrenIds; }
    }

    /**
     * Child chunk for vector embedding
     */
    public static class ChildChunk {
        private final String id;
        private final String parentId;
        private final String content;
        private final int position;
        private final String source;

        public ChildChunk(String id, String parentId, String content, int position, String source) {
            this.id = id;
            this.parentId = parentId;
            this.content = content;
            this.position = position;
            this.source = source;
        }

        public String getId() { return id; }
        public String getParentId() { return parentId; }
        public String getContent() { return content; }
        public int getPosition() { return position; }
        public String getSource() { return source; }
    }

    /**
     * Split document using hierarchical chunking strategy
     *
     * @param text   Input document text
     * @param docId  Document ID for generating deterministic IDs
     * @param title  Document title (used in metadata)
     * @return HierarchicalChunks containing parents and children
     */
    public static HierarchicalChunks splitHierarchical(String text, String docId, String title) {
        if (text == null || text.isEmpty()) {
            return new HierarchicalChunks(new ArrayList<>(), new ArrayList<>());
        }

        // Step 1: Split into parent chunks
        List<String> parentContents = splitIntoParentChunks(text);

        List<ParentChunk> parents = new ArrayList<>();
        List<ChildChunk> children = new ArrayList<>();

        int parentPosition = 0;
        for (String parentContent : parentContents) {
            // Generate deterministic parent ID
            String parentId = generateParentId(docId, parentPosition);

            // Extract title from first line (if it's a heading)
            String chunkTitle = extractTitle(parentContent, title);

            // Step 2: Split parent into child chunks
            List<String> childContents = splitIntoChildChunks(parentContent);

            List<String> childrenIds = new ArrayList<>();
            for (int i = 0; i < childContents.size(); i++) {
                String childId = generateChildId(parentId, i);
                childrenIds.add(childId);

                children.add(new ChildChunk(
                    childId,
                    parentId,
                    childContents.get(i).trim(),
                    i,
                    title
                ));
            }

            parents.add(new ParentChunk(
                parentId,
                parentContent,
                parentPosition,
                chunkTitle,
                childrenIds
            ));

            parentPosition++;
        }

        return new HierarchicalChunks(parents, children);
    }

    /**
     * Split text into parent chunks using semantic separators
     */
    private static List<String> splitIntoParentChunks(String text) {
        List<String> chunks = new ArrayList<>();
        String[] parts = splitBySeparators(text);

        StringBuilder currentChunk = new StringBuilder();

        for (String part : parts) {
            if (currentChunk.length() + part.length() <= PARENT_CHUNK_SIZE) {
                currentChunk.append(part);
            } else {
                // Current chunk is full, save it and start new
                if (currentChunk.length() > 0) {
                    String chunk = currentChunk.toString().trim();
                    if (chunk.length() >= MIN_PARENT_SIZE) {
                        chunks.add(chunk);
                    } else if (!chunks.isEmpty()) {
                        // Merge small chunk with previous
                        int lastIdx = chunks.size() - 1;
                        chunks.set(lastIdx, chunks.get(lastIdx) + "\n" + chunk);
                    }
                    currentChunk = new StringBuilder();
                }

                // If single part is larger than parent size, split further
                if (part.length() > PARENT_CHUNK_SIZE) {
                    chunks.addAll(splitLargeChunk(part, PARENT_CHUNK_SIZE));
                } else {
                    currentChunk.append(part);
                }
            }
        }

        // Add remaining chunk
        if (currentChunk.length() > 0) {
            String chunk = currentChunk.toString().trim();
            if (chunk.length() >= MIN_PARENT_SIZE) {
                chunks.add(chunk);
            } else if (!chunks.isEmpty()) {
                // Merge with previous
                int lastIdx = chunks.size() - 1;
                chunks.set(lastIdx, chunks.get(lastIdx) + "\n" + chunk);
            }
        }

        return chunks;
    }

    /**
     * Split parent chunk into child chunks
     */
    private static List<String> splitIntoChildChunks(String text) {
        List<String> children = new ArrayList<>();
        String[] parts = splitBySeparators(text);

        StringBuilder currentChild = new StringBuilder();

        for (String part : parts) {
            if (currentChild.length() + part.length() <= CHILD_CHUNK_SIZE) {
                currentChild.append(part);
            } else {
                if (currentChild.length() > 0) {
                    String child = currentChild.toString().trim();
                    if (!child.isEmpty()) {
                        children.add(child);
                    }
                    currentChild = new StringBuilder();
                }

                if (part.length() > CHILD_CHUNK_SIZE) {
                    // Split large parts by characters
                    children.addAll(splitLargeChunk(part, CHILD_CHUNK_SIZE));
                } else {
                    currentChild.append(part);
                }
            }
        }

        if (currentChild.length() > 0) {
            String child = currentChild.toString().trim();
            if (!child.isEmpty()) {
                children.add(child);
            }
        }

        return children;
    }

    /**
     * Split text by semantic separators (priority order)
     */
    private static String[] splitBySeparators(String text) {
        // Try each separator in priority order
        for (String separator : SEPARATORS) {
            if (separator.isEmpty()) {
                // Last resort: split by character
                return text.split("");
            }

            if (text.contains(separator)) {
                String[] parts = text.split(Pattern.quote(separator), -1);
                if (parts.length > 1) {
                    // Reconstruct with separator
                    List<String> result = new ArrayList<>();
                    for (int i = 0; i < parts.length; i++) {
                        if (!parts[i].isEmpty()) {
                            result.add(parts[i]);
                        }
                        if (i < parts.length - 1) {
                            result.add(separator);
                        }
                    }
                    return result.toArray(new String[0]);
                }
            }
        }

        return new String[]{text};
    }

    /**
     * Split large chunk into smaller parts
     */
    private static List<String> splitLargeChunk(String text, int maxSize) {
        List<String> parts = new ArrayList<>();

        // Try to split at sentence boundaries first
        String sentencePattern = "([。.!?！？\n]+)";
        String[] sentences = text.split(sentencePattern);

        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (current.length() + sentence.length() <= maxSize) {
                current.append(sentence);
            } else {
                if (current.length() > 0) {
                    parts.add(current.toString().trim());
                    current = new StringBuilder();
                }

                // If single sentence is too large, split by characters
                if (sentence.length() > maxSize) {
                    parts.addAll(splitByCharLimit(sentence, maxSize));
                } else {
                    current.append(sentence);
                }
            }
        }

        if (current.length() > 0) {
            parts.add(current.toString().trim());
        }

        return parts;
    }

    /**
     * Split text by character limit (last resort)
     */
    private static List<String> splitByCharLimit(String text, int maxSize) {
        List<String> parts = new ArrayList<>();
        int length = text.length();

        for (int i = 0; i < length; i += maxSize) {
            int end = Math.min(i + maxSize, length);
            // Try to break at punctuation
            int breakPoint = findBreakPoint(text, i, end);
            parts.add(text.substring(i, breakPoint));
            i = breakPoint - maxSize; // Adjust for next iteration
        }

        return parts;
    }

    /**
     * Find a good break point near the end of chunk
     */
    private static int findBreakPoint(String text, int start, int end) {
        String punctuations = "。.!?！？\n ,，";
        for (int i = end - 1; i > start + max(0, end - start - 50); i--) {
            if (punctuations.indexOf(text.charAt(i)) != -1) {
                return i + 1;
            }
        }
        return end;
    }

    private static int max(int a, int b) {
        return a > b ? a : b;
    }

    /**
     * Generate deterministic parent ID
     */
    private static String generateParentId(String docId, int position) {
        String input = docId + "_parent_" + position;
        return "p_" + Math.abs(input.hashCode());
    }

    /**
     * Generate deterministic child ID
     */
    private static String generateChildId(String parentId, int position) {
        String input = parentId + "_child_" + position;
        return "c_" + Math.abs(input.hashCode());
    }

    /**
     * Extract title from chunk content (first line if it's a heading)
     */
    private static String extractTitle(String content, String defaultTitle) {
        if (content == null || content.isEmpty()) {
            return defaultTitle;
        }

        String firstLine = content.split("\n")[0].trim();

        // Check if first line looks like a heading
        if (firstLine.matches("^#{1,6}\\s+.+") ||          // Markdown heading
            firstLine.matches("^\\d+\\.\\s+.+") ||        // Numbered heading
            firstLine.matches("^[A-Z][^.!?：:]*[：:]$")) {  // Uppercase heading

            // Remove heading markers
            return firstLine.replaceAll("^#{1,6}\\s+", "")
                           .replaceAll("^\\d+\\.\\s+", "")
                           .trim();
        }

        // Use first 50 chars as title
        if (firstLine.length() > 50) {
            return firstLine.substring(0, 50) + "...";
        }

        return firstLine.isEmpty() ? defaultTitle : firstLine;
    }

    /**
     * Simple split method for backward compatibility
     * Returns child chunks as flat list
     */
    public static List<String> split(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        HierarchicalChunks result = splitHierarchical(text, "default", "Document");
        return result.getChildren().stream()
                .map(ChildChunk::getContent)
                .toList();
    }

    /**
     * Configurable split method
     * @param text 输入文本
     * @param docId 文档 ID
     * @param title 文档标题
     * @param chunkSize chunk 大小 (500-800 tokens recommended)
     * @param overlap 重叠大小 (50-100 tokens recommended)
     * @return 分块结果
     */
    public static HierarchicalChunks splitWithConfig(String text, String docId, String title,
                                                     int chunkSize, int overlap) {
        if (text == null || text.isEmpty()) {
            return new HierarchicalChunks(new ArrayList<>(), new ArrayList<>());
        }

        // 使用可配置参数进行分块
        // chunkSize 和 overlap 可用于后续优化，目前使用标准分块方法
        // TODO: 根据 chunkSize 和 overlap 动态调整分块策略
        return splitHierarchical(text, docId, title);
    }

    /**
     * Simple text chunking without parent-child hierarchy
     * @param text 输入文本
     * @param chunkSize chunk 大小 (字符数)
     * @param overlap 重叠大小 (字符数)
     * @return 文本块列表
     */
    public static List<String> splitSimple(String text, int chunkSize, int overlap) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> chunks = new ArrayList<>();
        int textLength = text.length();
        int position = 0;

        while (position < textLength) {
            int end = Math.min(position + chunkSize, textLength);
            String chunk = text.substring(position, end);
            chunks.add(chunk);

            position += (chunkSize - overlap);
            if (position < 0 || position >= textLength) {
                break;
            }
        }

        return chunks;
    }
}
