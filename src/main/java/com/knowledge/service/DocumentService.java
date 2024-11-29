package com.knowledge.service;

import com.knowledge.domain.Document;
import com.knowledge.domain.DocumentChunk;
import com.knowledge.repository.DocumentChunkRepository;
import com.knowledge.repository.DocumentRepository;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingModel embeddingModel;
    private final VectorService vectorService;

    @Transactional
    public Document uploadDocument(MultipartFile file, String title) throws IOException {
        // Create document
        Document document = new Document();
        document.setTitle(title);
        document.setContent(new String(file.getBytes(), StandardCharsets.UTF_8));
        document.setFileType(file.getContentType());
        document = documentRepository.save(document);

        // Split content into chunks
        List<String> chunks = splitContent(document.getContent());
        List<DocumentChunk> documentChunks = new ArrayList<>();

        // Process each chunk
        for (int i = 0; i < chunks.size(); i++) {
            String chunkContent = chunks.get(i);
            
            // Create embedding and store in vector database
            float[] embedding = embeddingModel.embed(chunkContent).content().vector();
            String vectorId = vectorService.storeVector(embedding, document.getId(), i);

            // Create document chunk
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(document);
            chunk.setContent(chunkContent);
            chunk.setChunkIndex(i);
            chunk.setVectorId(vectorId);
            documentChunks.add(chunk);
        }

        chunkRepository.saveAll(documentChunks);
        return document;
    }

    private List<String> splitContent(String content) {
        // Simple implementation - split by paragraphs
        // In a real implementation, you might want to use more sophisticated text splitting
        String[] paragraphs = content.split("\\n\\n");
        List<String> chunks = new ArrayList<>();
        for (String paragraph : paragraphs) {
            if (!paragraph.trim().isEmpty()) {
                chunks.add(paragraph.trim());
            }
        }
        return chunks;
    }

    public Document getDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));
    }

    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }
}
