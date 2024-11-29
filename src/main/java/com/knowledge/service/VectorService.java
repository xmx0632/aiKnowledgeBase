package com.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.FieldData;
import io.milvus.grpc.SearchResultData;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
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

import java.util.*;
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
        
        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("vector_id", Collections.singletonList(vectorId)));
        fields.add(new InsertParam.Field("vector", Collections.singletonList(vector)));
        fields.add(new InsertParam.Field("document_id", Collections.singletonList(documentId)));
        fields.add(new InsertParam.Field("chunk_index", Collections.singletonList(chunkIndex)));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFields(fields)
                .build();

        var response = milvusClient.insert(insertParam);
        if (response.getStatus() != 0) {
            throw new RuntimeException("Failed to insert vector: " + response.getMessage());
        }
        return vectorId;
    }

    public List<Map<String, Object>> searchSimilarVectors(String query, int limit) {
        float[] queryVector = embeddingModel.embed(query).content().vector();

        List<String> outputFields = Arrays.asList("vector_id", "document_id", "chunk_index");
        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .withOutFields(outputFields)
                .withTopK(limit)
                .withVectors(Collections.singletonList(queryVector))
                .withVectorFieldName("vector")
                .withMetricType(MetricType.COSINE)
                .build();

        var searchResponse = milvusClient.search(searchParam);
        if (searchResponse.getStatus() != 0) {
            throw new RuntimeException("Search failed: " + searchResponse.getMessage());
        }

        List<Map<String, Object>> results = new ArrayList<>();
        SearchResults searchResults = searchResponse.getData();
        
        // 获取结果列表
        SearchResultData resultsList = searchResults.getResults();
        for (FieldData result : resultsList.getFieldsDataList()) {
            Map<String, Object> resultMap = new HashMap<>();
            log.info("field:{}",result.getFieldName());
            // 从字段数据中获取值
            // var fieldsDataList = result.getFieldsDataList();
            // resultMap.put("id", fieldsDataList.get(0).getScalarField().getStringData());      // vector_id
            // resultMap.put("document_id", fieldsDataList.get(1).getScalarField().getLongData());
            // resultMap.put("chunk_index", fieldsDataList.get(2).getScalarField().getIntData());
            // resultMap.put("score", result.getScore());
            
            results.add(resultMap);
        }
        
        return results;
    }

    @Override
    public void afterPropertiesSet() {
        try {
            createCollectionIfNotExists();
            log.info("Vector collection initialization completed");
        } catch (Exception e) {
            log.error("Failed to initialize vector collection", e);
            throw new RuntimeException(e);
        }
    }

    private void createCollectionIfNotExists() {
        HasCollectionParam hasParam = HasCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build();

        var response = milvusClient.hasCollection(hasParam);
        if (!response.getData()) {
            List<FieldType> fields = new ArrayList<>();
            
            fields.add(FieldType.newBuilder()
                    .withName("vector_id")
                    .withDescription("Vector ID")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(36)
                    .withPrimaryKey(true)
                    .build());

            fields.add(FieldType.newBuilder()
                    .withName("vector")
                    .withDescription("Vector data")
                    .withDataType(DataType.FloatVector)
                    .withDimension(vectorDimension)
                    .build());

            fields.add(FieldType.newBuilder()
                    .withName("document_id")
                    .withDescription("Document ID")
                    .withDataType(DataType.Int64)
                    .build());

            fields.add(FieldType.newBuilder()
                    .withName("chunk_index")
                    .withDescription("Chunk index")
                    .withDataType(DataType.Int32)
                    .build());

            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withDescription("Document vectors collection")
                    .withShardsNum(2)
                    .addFieldType(fields.get(0))
                    .addFieldType(fields.get(1))
                    .addFieldType(fields.get(2))
                    .addFieldType(fields.get(3))
                    .build();

            var createResponse = milvusClient.createCollection(createParam);
            if (createResponse.getStatus() != 0) {
                throw new RuntimeException("Failed to create collection: " + createResponse.getMessage());
            }

            // Create index on vector field
            CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withFieldName("vector")
                    .withIndexType(IndexType.IVF_FLAT)
                    .withMetricType(MetricType.COSINE)
                    .withExtraParam("{\"nlist\":1024}")
                    .build();

            var indexResponse = milvusClient.createIndex(indexParam);
            if (indexResponse.getStatus() != 0) {
                throw new RuntimeException("Failed to create index: " + indexResponse.getMessage());
            }

            log.info("Created vector collection and index: {}", COLLECTION_NAME);
        }
    }
}
