package com.knowledge.service;

import com.knowledge.domain.DocumentChunk;
import com.knowledge.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QAService {

    private final VectorService vectorService;
    private final DocumentChunkRepository chunkRepository;

    public String getAnswer(String question) throws IOException {
        // Search for similar vectors
        List<Map<String, Object>> similarVectors = vectorService.searchSimilarVectors(question, 3);

        // Collect relevant chunks
        List<String> relevantTexts = new ArrayList<>();
        // for (Map<String, Object> vector : similarVectors) {
        // log.info("=== Found vector with document_id={} and chunk_index={}",
        // vector.get("document_id"), vector.get("chunk_index"));
        // Long documentId = ((Number) vector.get("document_id")).longValue();
        // Integer chunkIndex = ((Number) vector.get("chunk_index")).intValue();

        // DocumentChunk chunk = chunkRepository.findByDocumentId(documentId).stream()
        // .filter(c -> c.getChunkIndex().equals(chunkIndex))
        // .findFirst()
        // .orElse(null);

        // if (chunk != null) {
        // relevantTexts.add(chunk.getContent());
        // }
        // }
        relevantTexts.add("This is a test chunk.");

        // For now, just return the most relevant chunk
        // In a real implementation, you would use an LLM to generate a proper answer
        return relevantTexts.isEmpty() ? "No relevant information found." : relevantTexts.get(0);
    }
}
