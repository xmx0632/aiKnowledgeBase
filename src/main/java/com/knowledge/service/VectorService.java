package com.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.FieldData;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResultData;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.IndexType;
import io.milvus.param.collection.*;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorService implements InitializingBean {

    private static final String COLLECTION_NAME = "doc_vectors";
    private final MilvusServiceClient milvusClient;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;

    @Value("${vector.dimension}")
    private int vectorDimension;

    public String storeVector(float[] vector, Long documentId, int chunkIndex) {
        String vectorId = UUID.randomUUID().toString();
        log.info("Storing vector with ID: {}, document ID: {}, chunk index: {}", vectorId, documentId, chunkIndex);

        // Convert float[] to List<Float>
        List<Float> vectorList = new ArrayList<>();
        for (float v : vector) {
            vectorList.add(v);
        }

        log.info("vectorId={},vectorList={},documentId={},chunkIndex={}", vectorId, vectorList, documentId, chunkIndex);

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("vector_id", Collections.singletonList(vectorId)));
        fields.add(new InsertParam.Field("vector", Collections.singletonList(vectorList)));
        fields.add(new InsertParam.Field("document_id", Collections.singletonList(documentId)));
        fields.add(new InsertParam.Field("chunk_index", Collections.singletonList(chunkIndex)));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFields(fields)
                .build();

        R<MutationResult> response = milvusClient.insert(insertParam);
        log.info("Insert vector response status: {}", response.getStatus());
        if (response.getStatus() != 0) {
            log.error("Failed to insert vector: {}", response.getMessage());
            throw new RuntimeException("Failed to insert vector: " + response.getMessage());
        }

        return vectorId;
    }

    public List<Map<String, Object>> searchSimilarVectors(String query, int limit) {
        log.info("Searching similar vectors for query: {}, limit: {}", query, limit);
        float[] queryVector = embeddingModel.embed(query).content().vector();
        log.info("Generated query vector with dimension: {}", queryVector.length);

        // Convert float[] to List<Float>
        List<Float> queryVectorList = new ArrayList<>();
        for (float v : queryVector) {
            queryVectorList.add(v);
        }

        List<String> outputFields = Arrays.asList("vector_id", "document_id", "chunk_index");
        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .withOutFields(outputFields)
                .withTopK(limit)
                .withVectors(Collections.singletonList(queryVectorList))
                .withVectorFieldName("vector")
                .withMetricType(MetricType.COSINE)
                .build();

        log.info("Executing vector search with parameters: collection={}, consistency={}, topK={}",
                COLLECTION_NAME, ConsistencyLevelEnum.STRONG, limit);
        R<SearchResults> searchResponse = milvusClient.search(searchParam);
        log.info("Search response status: {}", searchResponse.getStatus());
        if (searchResponse.getStatus() != 0) {
            log.error("Search failed: {}", searchResponse.getMessage());
            throw new RuntimeException("Search failed: " + searchResponse.getMessage());
        }

        List<Map<String, Object>> results = new ArrayList<>();
        SearchResults searchResults = searchResponse.getData();
        SearchResultData resultData = searchResults.getResults();

        // 获取字段数据
        List<FieldData> fieldsData = resultData.getFieldsDataList();

        log.info("fieldsData={}", fieldsData);

        // 获取分数
        List<Float> scores = new ArrayList<>();
        for (int i = 0; i < resultData.getNumQueries(); i++) {
            scores.add(Float.valueOf(resultData.getScores(i)));
        }

        int numResults = scores.size();
        log.info("Found {} results", numResults);

        for (int i = 0; i < numResults; i++) {
            Map<String, Object> result = new HashMap<>();

            // 获取 chunk_index (Int)
            result.put("chunk_index", fieldsData.get(0).getScalars().getIntData());

            // 获取 vector_id (String)
            result.put("vector_id", fieldsData.get(1).getScalars().getStringData());

            // 获取 document_id (Long)
            result.put("document_id", fieldsData.get(2).getScalars().getLongData());

            // 获取相似度分数
            result.put("score", scores.get(i));

            results.add(result);
            log.debug("Result {}: vectorId={}, documentId={}, chunkIndex={}, score={}",
                    i, result.get("vector_id"), result.get("document_id"),
                    result.get("chunk_index"), result.get("score"));
        }

        return results;
    }

    @Override
    public void afterPropertiesSet() {
        createCollectionIfNotExists();
    }

    private void createCollectionIfNotExists() {
        log.info("Checking if collection {} exists...", COLLECTION_NAME);
        HasCollectionParam hasCollectionParam = HasCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build();

        R<Boolean> hasCollectionResponse = milvusClient.hasCollection(hasCollectionParam);
        if (hasCollectionResponse.getData()) {
            log.info("Collection {} already exists", COLLECTION_NAME);
            loadCollection();
            return;
        }

        log.info("Creating collection: {}", COLLECTION_NAME);
        FieldType vectorIdField = FieldType.newBuilder()
                .withName("vector_id")
                .withDataType(DataType.VarChar)
                .withMaxLength(36)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build();

        FieldType vectorField = FieldType.newBuilder()
                .withName("vector")
                .withDataType(DataType.FloatVector)
                .withDimension(vectorDimension)
                .build();

        FieldType documentIdField = FieldType.newBuilder()
                .withName("document_id")
                .withDataType(DataType.Int64)
                .build();

        FieldType chunkIndexField = FieldType.newBuilder()
                .withName("chunk_index")
                .withDataType(DataType.Int32)
                .build();

        CreateCollectionParam createCollectionParam = CreateCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withDescription("Document vectors collection")
                .withShardsNum(2)
                .addFieldType(vectorIdField)
                .addFieldType(vectorField)
                .addFieldType(documentIdField)
                .addFieldType(chunkIndexField)
                .build();

        R<RpcStatus> createCollectionResponse = milvusClient.createCollection(createCollectionParam);
        if (createCollectionResponse.getStatus() != 0) {
            log.error("Failed to create collection: {}", createCollectionResponse.getMessage());
            throw new RuntimeException("Failed to create collection: " + createCollectionResponse.getMessage());
        }
        log.info("Collection {} created successfully", COLLECTION_NAME);

        // Create index on vector field
        log.info("Creating index on vector field");
        CreateIndexParam createIndexParam = CreateIndexParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldName("vector")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"nlist\":1024}")
                .withSyncMode(Boolean.TRUE)
                .build();

        R<RpcStatus> createIndexResponse = milvusClient.createIndex(createIndexParam);
        if (createIndexResponse.getStatus() != 0) {
            log.error("Failed to create index: {}", createIndexResponse.getMessage());
            throw new RuntimeException("Failed to create index: " + createIndexResponse.getMessage());
        }
        log.info("Index created successfully");

        loadCollection();
    }

    private void loadCollection() {
        log.info("Loading collection {} into memory", COLLECTION_NAME);
        LoadCollectionParam loadCollectionParam = LoadCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build();

        R<RpcStatus> loadCollectionResponse = milvusClient.loadCollection(loadCollectionParam);
        if (loadCollectionResponse.getStatus() != 0) {
            log.error("Failed to load collection: {}", loadCollectionResponse.getMessage());
            throw new RuntimeException("Failed to load collection: " + loadCollectionResponse.getMessage());
        }
        log.info("Collection {} loaded successfully", COLLECTION_NAME);
    }
}
