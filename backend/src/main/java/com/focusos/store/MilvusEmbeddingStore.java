package com.focusos.store;

import com.focusos.config.MilvusProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.DescribeIndexResponse;
import io.milvus.grpc.GetLoadStateResponse;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.R.Status;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.index.DescribeIndexParam;
import io.milvus.response.QueryResultsWrapper.RowRecord;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于原生 Milvus SDK 实现的 EmbeddingStore。
 * <p>
 * 相比 langchain4j 默认 MilvusEmbeddingStore：
 * 1. 使用独立标量字段 userId/documentId/fileName，支持高效标量过滤
 * 2. 支持 Collection 自动初始化、索引自动创建
 * 3. 支持按 documentId 批量删除向量
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "focusos.milvus.enabled", havingValue = "true", matchIfMissing = true)
public class MilvusEmbeddingStore implements EmbeddingStore<TextSegment> {

    public static final String FIELD_ID = "id";
    public static final String FIELD_VECTOR = "vector";
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_DOCUMENT_ID = "documentId";
    public static final String FIELD_FILE_NAME = "fileName";
    public static final String FIELD_DOCUMENT_TITLE = "documentTitle";
    public static final String FIELD_CHUNK_INDEX = "chunkIndex";
    public static final String FIELD_TEXT = "text";
    // Sprint 5-B: Personal Knowledge Base 扩展字段
    public static final String FIELD_CATEGORY = "category";
    public static final String FIELD_DOCUMENT_TYPE = "documentType";
    public static final String FIELD_PRIORITY = "priority";
    public static final String FIELD_SOURCE = "source";

    private static final int INDEX_TYPE_IVF_FLAT_NLIST = 128;

    private final MilvusServiceClient milvusClient;
    private final MilvusProperties properties;

    // ================ Collection 管理 ================

    /**
     * 检查 Collection 是否存在，不存在则创建并建索引、加载。
     */
    public void ensureCollectionReady() {
        String collectionName = properties.getCollectionName();
        R<Boolean> hasResp = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collectionName).build());
        boolean exists = hasResp.getData() != null && Boolean.TRUE.equals(hasResp.getData());

        if (!exists) {
            log.info("Milvus collection {} not found, creating...", collectionName);
            createCollection();
        } else {
            log.info("Milvus collection {} already exists", collectionName);
        }

        ensureIndexExists();
        ensureCollectionLoaded();
    }

    /**
     * 按 documentId 批量删除该文档所有 chunk。
     */
    public void deleteByDocumentId(long documentId) {
        String expr = FIELD_DOCUMENT_ID + " == " + documentId;
        R<MutationResult> resp = milvusClient.delete(DeleteParam.newBuilder()
                .withCollectionName(properties.getCollectionName())
                .withExpr(expr)
                .build());
        if (resp.getStatus() != Status.Success.getCode()) {
            log.warn("Failed to delete vectors by documentId={}: {}", documentId, resp.getException());
        } else {
            log.info("Deleted vectors for documentId={}", documentId);
        }
    }

    /**
     * 按 userId 和 documentId 删除。
     */
    public void deleteByUserAndDocument(long userId, long documentId) {
        String expr = FIELD_USER_ID + " == " + userId + " && " + FIELD_DOCUMENT_ID + " == " + documentId;
        R<MutationResult> resp = milvusClient.delete(DeleteParam.newBuilder()
                .withCollectionName(properties.getCollectionName())
                .withExpr(expr)
                .build());
        if (resp.getStatus() != Status.Success.getCode()) {
            log.warn("Failed to delete vectors userId={},documentId={}: {}", userId, documentId, resp.getException());
        }
    }

    /**
     * 清空整个 Collection（慎用，仅用于重建）。
     */
    public void clearAll() {
        String collectionName = properties.getCollectionName();
        R<Boolean> hasResp = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collectionName).build());
        if (hasResp.getData() != null && Boolean.TRUE.equals(hasResp.getData())) {
            milvusClient.dropCollection(DropCollectionParam.newBuilder()
                    .withCollectionName(collectionName).build());
            log.warn("Milvus collection {} dropped for rebuild", collectionName);
        }
        createCollection();
        ensureIndexExists();
        ensureCollectionLoaded();
    }

    // ================ EmbeddingStore 接口 ================

    @Override
    public String add(Embedding embedding) {
        return internalAdd(embedding, null, null, null, null, null, null, null, null, null, null);
    }

    @Override
    public void add(String id, Embedding embedding) {
        internalAddById(id, embedding, null, null, null, null, null, null, null, null, null, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        Long userId = null, documentId = null, chunkIndex = null;
        String fileName = null, documentTitle = null;
        String category = null, documentType = null, source = null;
        Integer priority = null;
        if (textSegment != null && textSegment.metadata() != null) {
            userId = parseLong(textSegment.metadata().getString("userId"));
            documentId = parseLong(textSegment.metadata().getString("documentId"));
            documentTitle = textSegment.metadata().getString("documentTitle");
            fileName = textSegment.metadata().getString("fileName");
            chunkIndex = parseLong(textSegment.metadata().getString("chunkIndex"));
            category = textSegment.metadata().getString("category");
            documentType = textSegment.metadata().getString("documentType");
            source = textSegment.metadata().getString("source");
            String prioStr = textSegment.metadata().getString("priority");
            if (prioStr != null) {
                try { priority = Integer.parseInt(prioStr); } catch (NumberFormatException ignore) {}
            }
        }
        return internalAdd(embedding, textSegment == null ? null : textSegment.text(),
                userId, documentId, fileName, documentTitle, chunkIndex,
                category, documentType, priority, source);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = new ArrayList<>();
        for (Embedding e : embeddings) ids.add(add(e));
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = new ArrayList<>();
        if (embedded == null || embedded.isEmpty()) {
            return addAll(embeddings);
        }
        for (int i = 0; i < embeddings.size(); i++) {
            TextSegment seg = i < embedded.size() ? embedded.get(i) : null;
            ids.add(add(embeddings.get(i), seg));
        }
        return ids;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        Embedding queryEmbedding = request.queryEmbedding();
        int maxResults = request.maxResults();
        double minScore = request.minScore();

        List<Float> vectorList = toFloatList(queryEmbedding.vector());

        // 构造标量过滤表达式（仅 userId 过滤，searchKnowledgeByUser 二次过滤 documentId 可选）
        String expr = null;
        if (request.filter() != null) {
            // langchain4j Filter 没有 metadataFilter() API，直接 toString 做轻量解析
            // searchKnowledgeByUser 二次过滤保证正确性，这里仅做预过滤优化
            String filterStr = request.filter().toString();
            if (filterStr.contains("userId")) {
                try {
                    String uidStr = filterStr.replaceAll(".*userId[= ]*(\\d+).*", "$1");
                    if (!uidStr.equals(filterStr) && !uidStr.isBlank()) {
                        expr = FIELD_USER_ID + " == " + Long.parseLong(uidStr);
                    }
                } catch (Exception ignore) { /* 表达式解析失败，走二次过滤即可 */ }
            }
        }

        SearchParam.Builder searchBuilder = SearchParam.newBuilder()
                .withCollectionName(properties.getCollectionName())
                .withConsistencyLevel(ConsistencyLevelEnum.EVENTUALLY)
                .withMetricType(MetricType.COSINE)
                .withTopK(maxResults)
                .withVectors(Collections.singletonList(vectorList))
                .withVectorFieldName(FIELD_VECTOR)
                .withOutFields(Arrays.asList(FIELD_ID, FIELD_TEXT, FIELD_USER_ID, FIELD_DOCUMENT_ID,
                        FIELD_FILE_NAME, FIELD_DOCUMENT_TITLE, FIELD_CHUNK_INDEX,
                        FIELD_CATEGORY, FIELD_DOCUMENT_TYPE, FIELD_PRIORITY, FIELD_SOURCE));
        if (expr != null) searchBuilder.withExpr(expr);

        R<SearchResults> resp = milvusClient.search(searchBuilder.build());
        if (resp.getStatus() != Status.Success.getCode()) {
            log.error("Milvus search failed: {}", resp.getException());
            return new EmbeddingSearchResult<>(Collections.emptyList());
        }

        SearchResultsWrapper wrapper = new SearchResultsWrapper(resp.getData().getResults());
        List<SearchResultsWrapper.IDScore> raw = wrapper.getIDScore(0);
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();

        for (SearchResultsWrapper.IDScore s : raw) {
            double score = s.getScore();
            // Cosine 相似度，Milvus 高版本已归一化到 [0,1]
            if (score < minScore) continue;

            RowRecord row = null;
            try {
                row = wrapper.getRowRecords(0).get(raw.indexOf(s));
            } catch (Exception ignore) { /* 降级通过 id 查询 */ }

            String embeddedId = String.valueOf(s.getStrID() == null ? String.valueOf(s.getLongID()) : s.getStrID());
            String text = null;
            Long userId = null, documentId = null, chunkIdx = null;
            String fileName = null, docTitle = null;
            String category = null, documentType = null, source = null;
            String priority = null;

            if (row != null) {
                text = asString(row.get(FIELD_TEXT));
                userId = asLong(row.get(FIELD_USER_ID));
                documentId = asLong(row.get(FIELD_DOCUMENT_ID));
                chunkIdx = asLong(row.get(FIELD_CHUNK_INDEX));
                fileName = asString(row.get(FIELD_FILE_NAME));
                docTitle = asString(row.get(FIELD_DOCUMENT_TITLE));
                category = asString(row.get(FIELD_CATEGORY));
                documentType = asString(row.get(FIELD_DOCUMENT_TYPE));
                source = asString(row.get(FIELD_SOURCE));
                Object prioVal = row.get(FIELD_PRIORITY);
                if (prioVal != null) priority = String.valueOf(prioVal);
            }

            TextSegment segment = null;
            if (text != null) {
                segment = TextSegment.from(text);
                if (userId != null) segment.metadata().put("userId", String.valueOf(userId));
                if (documentId != null) segment.metadata().put("documentId", String.valueOf(documentId));
                if (docTitle != null) segment.metadata().put("documentTitle", docTitle);
                if (fileName != null) segment.metadata().put("fileName", fileName);
                if (chunkIdx != null) segment.metadata().put("chunkIndex", String.valueOf(chunkIdx));
                if (category != null) segment.metadata().put("category", category);
                if (documentType != null) segment.metadata().put("documentType", documentType);
                if (source != null) segment.metadata().put("source", source);
                if (priority != null) segment.metadata().put("priority", priority);
            }
            matches.add(new EmbeddingMatch<>(score, embeddedId, queryEmbedding, segment));
        }
        return new EmbeddingSearchResult<>(matches);
    }

    // ================ 内部实现 ================

    private void createCollection() {
        String collectionName = properties.getCollectionName();
        int dim = properties.getEmbeddingDimension();

        FieldType idField = FieldType.newBuilder()
                .withName(FIELD_ID).withDataType(DataType.VarChar).withMaxLength(64)
                .withPrimaryKey(true).withAutoID(false).build();
        FieldType vectorField = FieldType.newBuilder()
                .withName(FIELD_VECTOR).withDataType(DataType.FloatVector).withDimension(dim).build();
        FieldType userIdField = FieldType.newBuilder()
                .withName(FIELD_USER_ID).withDataType(DataType.Int64).build();
        FieldType documentIdField = FieldType.newBuilder()
                .withName(FIELD_DOCUMENT_ID).withDataType(DataType.Int64).build();
        FieldType fileNameField = FieldType.newBuilder()
                .withName(FIELD_FILE_NAME).withDataType(DataType.VarChar).withMaxLength(512).build();
        FieldType documentTitleField = FieldType.newBuilder()
                .withName(FIELD_DOCUMENT_TITLE).withDataType(DataType.VarChar).withMaxLength(512).build();
        FieldType chunkIndexField = FieldType.newBuilder()
                .withName(FIELD_CHUNK_INDEX).withDataType(DataType.Int32).build();
        FieldType textField = FieldType.newBuilder()
                .withName(FIELD_TEXT).withDataType(DataType.VarChar).withMaxLength(65535).build();
        // Sprint 5-B: Personal Knowledge Base 扩展字段
        FieldType categoryField = FieldType.newBuilder()
                .withName(FIELD_CATEGORY).withDataType(DataType.VarChar).withMaxLength(100).build();
        FieldType documentTypeField = FieldType.newBuilder()
                .withName(FIELD_DOCUMENT_TYPE).withDataType(DataType.VarChar).withMaxLength(50).build();
        FieldType priorityField = FieldType.newBuilder()
                .withName(FIELD_PRIORITY).withDataType(DataType.Int32).build();
        FieldType sourceField = FieldType.newBuilder()
                .withName(FIELD_SOURCE).withDataType(DataType.VarChar).withMaxLength(50).build();

        CreateCollectionParam param = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDescription("FocusOS AI Personal Knowledge Base 向量存储")
                .withShardsNum(1)
                .addFieldType(idField)
                .addFieldType(vectorField)
                .addFieldType(userIdField)
                .addFieldType(documentIdField)
                .addFieldType(fileNameField)
                .addFieldType(documentTitleField)
                .addFieldType(chunkIndexField)
                .addFieldType(textField)
                .addFieldType(categoryField)
                .addFieldType(documentTypeField)
                .addFieldType(priorityField)
                .addFieldType(sourceField)
                .build();

        R<?> resp = milvusClient.createCollection(param);
        if (resp.getStatus() != Status.Success.getCode()) {
            throw new IllegalStateException("Failed to create Milvus collection: " + resp.getException());
        }
        log.info("Milvus collection {} created (dim={})", collectionName, dim);
    }

    private void ensureIndexExists() {
        String collectionName = properties.getCollectionName();
        R<DescribeIndexResponse> idx = milvusClient.describeIndex(DescribeIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName(FIELD_VECTOR)
                .build());
        boolean hasIndex = idx.getStatus() == Status.Success.getCode()
                && idx.getData() != null
                && idx.getData().getIndexDescriptionsCount() > 0;

        if (!hasIndex) {
            log.info("Creating IVF_FLAT index on {} for field {}", collectionName, FIELD_VECTOR);
            String paramsJson = "{\"nlist\":" + INDEX_TYPE_IVF_FLAT_NLIST + "}";
            R<?> resp = milvusClient.createIndex(CreateIndexParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldName(FIELD_VECTOR)
                    .withIndexType(IndexType.IVF_FLAT)
                    .withMetricType(MetricType.COSINE)
                    .withExtraParam(paramsJson)
                    .withSyncMode(Boolean.TRUE)
                    .build());
            if (resp.getStatus() != Status.Success.getCode()) {
                throw new IllegalStateException("Failed to create Milvus index: " + resp.getException());
            }
        }
    }

    private void ensureCollectionLoaded() {
        String collectionName = properties.getCollectionName();
        R<GetLoadStateResponse> state = milvusClient.getLoadState(
                io.milvus.param.collection.GetLoadStateParam.newBuilder()
                        .withCollectionName(collectionName).build());
        if (state.getStatus() == Status.Success.getCode()
                && state.getData() != null
                && !state.getData().getState().equals(io.milvus.grpc.LoadState.LoadStateLoaded)) {
            milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withSyncLoad(Boolean.TRUE)
                    .build());
            log.info("Milvus collection {} loaded into memory", collectionName);
        }
    }

    private String internalAdd(Embedding embedding, String text, Long userId, Long documentId,
                               String fileName, String documentTitle, Long chunkIndex,
                               String category, String documentType, Integer priority, String source) {
        String id = UUID.randomUUID().toString().replace("-", "");
        internalAddById(id, embedding, text, userId, documentId, fileName, documentTitle, chunkIndex,
                category, documentType, priority, source);
        return id;
    }

    private void internalAddById(String id, Embedding embedding, String text, Long userId, Long documentId,
                                 String fileName, String documentTitle, Long chunkIndex,
                                 String category, String documentType, Integer priority, String source) {
        List<String> idField = Collections.singletonList(id);
        List<List<Float>> vectorField = Collections.singletonList(toFloatList(embedding.vector()));
        List<Long> userIdField = Collections.singletonList(userId == null ? 0L : userId);
        List<Long> documentIdField = Collections.singletonList(documentId == null ? 0L : documentId);
        List<String> fileNameField = Collections.singletonList(fileName == null ? "" : fileName);
        List<String> titleField = Collections.singletonList(documentTitle == null ? "" : documentTitle);
        List<Integer> chunkIdxField = Collections.singletonList(chunkIndex == null ? 0 : chunkIndex.intValue());
        List<String> textField = Collections.singletonList(text == null ? "" : text);
        List<String> categoryField = Collections.singletonList(category == null ? "" : category);
        List<String> documentTypeField = Collections.singletonList(documentType == null ? "" : documentType);
        List<Integer> priorityField = Collections.singletonList(priority == null ? 3 : priority);
        List<String> sourceField = Collections.singletonList(source == null ? "upload" : source);

        List<InsertParam.Field> fields = Arrays.asList(
                new InsertParam.Field(FIELD_ID, idField),
                new InsertParam.Field(FIELD_VECTOR, vectorField),
                new InsertParam.Field(FIELD_USER_ID, userIdField),
                new InsertParam.Field(FIELD_DOCUMENT_ID, documentIdField),
                new InsertParam.Field(FIELD_FILE_NAME, fileNameField),
                new InsertParam.Field(FIELD_DOCUMENT_TITLE, titleField),
                new InsertParam.Field(FIELD_CHUNK_INDEX, chunkIdxField),
                new InsertParam.Field(FIELD_TEXT, textField),
                new InsertParam.Field(FIELD_CATEGORY, categoryField),
                new InsertParam.Field(FIELD_DOCUMENT_TYPE, documentTypeField),
                new InsertParam.Field(FIELD_PRIORITY, priorityField),
                new InsertParam.Field(FIELD_SOURCE, sourceField)
        );

        R<MutationResult> resp = milvusClient.insert(InsertParam.newBuilder()
                .withCollectionName(properties.getCollectionName())
                .withFields(fields)
                .build());
        if (resp.getStatus() != Status.Success.getCode()) {
            throw new RuntimeException("Milvus insert failed: " + resp.getException());
        }
    }

    private static List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) list.add(v);
        return list;
    }

    private static Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.parseLong(s); } catch (Exception e) { return null; }
    }

    private static String asString(Object o) {
        if (o == null) return null;
        return o.toString();
    }

    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return null; }
    }
}
