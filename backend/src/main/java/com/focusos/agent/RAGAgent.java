package com.focusos.agent;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RAGAgent implements FocusAgent {

    private final ChatLanguageModel chatLanguageModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore embeddingStore;
    private final AgentPromptProvider promptProvider;

    public String chat(String message, String context) {
        String fullPrompt = String.format("%s\n\n用户问题：%s\n\n上下文：%s",
                promptProvider.ragSystemPrompt(), message,
                context != null ? context : "无特定上下文");

        try {
            return chatLanguageModel.chat(fullPrompt);
        } catch (Exception e) {
            log.error("Failed to generate chat response", e);
            return "抱歉，回答生成失败，请稍后重试。";
        }
    }

    /**
     * 带用户隔离的RAG检索对话
     */
    public String chatWithRetrieval(String message, Long userId) {
        try {
            String context = searchKnowledgeByUser(message, userId);

            if (context.isEmpty()) {
                return chat(message, "");
            }

            String prompt = String.format("""
                    基于以下参考资料回答问题。如果参考资料与问题无关，请说明。

                    参考资料：
                    %s

                    问题：%s
                    """, context, message);

            String fullPrompt = promptProvider.ragSystemPrompt() + "\n\n" + prompt;

            return chatLanguageModel.chat(fullPrompt);
        } catch (Exception e) {
            log.error("Failed to chat with retrieval", e);
            return chat(message, "");
        }
    }

    public List<String> splitIntoChunks(String content, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = content.split("\\n\\n+");
        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) continue;

            // 单个段落超过 chunkSize 时，按单换行或固定长度二次切分
            if (paragraph.length() > chunkSize) {
                // 先把当前已累积的内容保存
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
                // 按单换行切分超长段落
                String[] lines = paragraph.split("\\n");
                for (String line : lines) {
                    if (line.isEmpty()) continue;
                    if (currentChunk.length() + line.length() + 1 > chunkSize && currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString().trim());
                        currentChunk = new StringBuilder();
                    }
                    // 单行仍然超长时强制按 chunkSize 截断
                    if (line.length() > chunkSize) {
                        for (int i = 0; i < line.length(); i += chunkSize) {
                            String piece = line.substring(i, Math.min(i + chunkSize, line.length()));
                            if (currentChunk.length() + piece.length() > chunkSize && currentChunk.length() > 0) {
                                chunks.add(currentChunk.toString().trim());
                                currentChunk = new StringBuilder();
                            }
                            currentChunk.append(piece).append("\n");
                        }
                    } else {
                        currentChunk.append(line).append("\n");
                    }
                    if (currentChunk.length() >= chunkSize) {
                        chunks.add(currentChunk.toString().trim());
                        currentChunk = new StringBuilder();
                    }
                }
                continue;
            }

            if (currentChunk.length() + paragraph.length() > chunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }

            currentChunk.append(paragraph).append("\n\n");

            if (currentChunk.length() >= chunkSize) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * 带用户隔离的向量化存储（Sprint 5-B 扩展：支持 Personal KB metadata）
     */
    public void addToVectorStore(String documentTitle, String fileName, List<String> chunks,
                                 Long userId, Long documentId,
                                 String category, String documentType, Integer priority, String source) {
        try {
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                TextSegment segment = TextSegment.from(chunk);
                segment.metadata().put("userId", String.valueOf(userId));
                segment.metadata().put("documentId", String.valueOf(documentId));
                segment.metadata().put("documentTitle", documentTitle == null ? "" : documentTitle);
                segment.metadata().put("fileName", fileName == null ? "" : fileName);
                segment.metadata().put("chunkIndex", String.valueOf(i));
                // Sprint 5-B: Personal KB 扩展 metadata
                segment.metadata().put("category", category == null ? "" : category);
                segment.metadata().put("documentType", documentType == null ? "" : documentType);
                segment.metadata().put("priority", String.valueOf(priority == null ? 3 : priority));
                segment.metadata().put("source", source == null ? "upload" : source);

                Response<Embedding> response = embeddingModel.embed(segment);
                embeddingStore.add(response.content(), segment);
            }
            log.info("Added {} chunks to vector store for document: {}, user: {}, category: {}, type: {}",
                    chunks.size(), documentTitle, userId, category, documentType);
        } catch (Exception e) {
            log.error("Failed to add to vector store", e);
            throw new RuntimeException("向量写入失败：" + e.getMessage(), e);
        }
    }

    /**
     * 向后兼容：不带 Personal KB metadata 的写入
     */
    public void addToVectorStore(String documentTitle, String fileName, List<String> chunks, Long userId, Long documentId) {
        addToVectorStore(documentTitle, fileName, chunks, userId, documentId, null, null, null, null);
    }

    /**
     * 带用户隔离的知识检索
     */
    public String searchKnowledgeByUser(String query, Long userId) {
        return searchKnowledgeByUser(query, userId, null);
    }

    /**
     * 带用户隔离+分类过滤的知识检索（Sprint 5-B: Personal KB）
     * @param category 可选，如 career/learning/project/experience/goal
     */
    public String searchKnowledgeByUser(String query, Long userId, String category) {
        try {
            Response<Embedding> queryEmbedding = embeddingModel.embed(query);

            // Sprint 6-C: 降低 minScore 从 0.5 → 0.25，提高召回率
            // 之前 0.5 阈值过高，中文关键词 embedding 与文档 cosine 相似度经常在 0.3-0.5 之间
            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(
                    queryEmbedding.content(),
                    15,
                    0.25
            );

            return matches.stream()
                    .filter(m -> m.embedded() != null)
                    .filter(m -> {
                        String metadataUserId = m.embedded().metadata().getString("userId");
                        return metadataUserId != null && metadataUserId.equals(String.valueOf(userId));
                    })
                    .filter(m -> {
                        if (category == null || category.isEmpty()) return true;
                        String metaCategory = m.embedded().metadata().getString("category");
                        return category.equals(metaCategory);
                    })
                    .map(m -> m.embedded().text())
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.error("Failed to search knowledge for user: {}, category: {}", userId, category, e);
            return "";
        }
    }

    /**
     * Sprint 6-C: 带来源信息的检索（返回 EmbeddingMatch 列表，含 fileName/documentTitle 等元数据）
     * <p>
     * 供 PersonalProfileService 使用，构建带来源的 UserProfileContext
     */
    public List<EmbeddingMatch<TextSegment>> searchWithMetadata(String query, Long userId, String category, int maxResults, double minScore) {
        try {
            Response<Embedding> queryEmbedding = embeddingModel.embed(query);
            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(
                    queryEmbedding.content(),
                    maxResults,
                    minScore
            );
            return matches.stream()
                    .filter(m -> m.embedded() != null)
                    .filter(m -> {
                        String metadataUserId = m.embedded().metadata().getString("userId");
                        return metadataUserId != null && metadataUserId.equals(String.valueOf(userId));
                    })
                    .filter(m -> {
                        if (category == null || category.isEmpty()) return true;
                        String metaCategory = m.embedded().metadata().getString("category");
                        return category.equals(metaCategory);
                    })
                    .toList();
        } catch (Exception e) {
            log.error("Failed to search with metadata for user: {}, category: {}", userId, category, e);
            return List.of();
        }
    }

    /**
     * 检索用户画像：按多个 category 分别检索并组装（Sprint 5-B: CareerAgent 调用）
     * @param categories 需检索的分类列表，如 ["career","project","experience"]
     * @return 组装后的用户画像文本
     */
    public String searchUserProfile(Long userId, List<String> categories) {
        StringBuilder profile = new StringBuilder();
        for (String cat : categories) {
            String query = switch (cat) {
                case "career" -> "技能 技术 工具 框架 编程语言";
                case "project" -> "项目 项目经历 技术栈 职责 成果";
                case "experience" -> "实习 工作 经历 公司 职位";
                case "learning" -> "学习 课程 知识 笔记";
                case "goal" -> "目标 规划 方向 职业目标";
                default -> cat;
            };
            String result = searchKnowledgeByUser(query, userId, cat);
            if (!result.isEmpty()) {
                profile.append("【").append(cat).append("】\n").append(result).append("\n\n");
            }
        }
        return profile.toString().trim();
    }

    /**
     * @deprecated 使用 searchKnowledgeByUser 替代，此方法已不使用
     */
    @Deprecated
    public String searchKnowledge(String query) {
        log.warn("Called deprecated global searchKnowledge without userId filter");
        return "";
    }

    @Override
    public String type() {
        return "rag";
    }

    @Override
    public String handle(String message, Long userId, String context) {
        return chatWithRetrieval(message, userId);
    }
}