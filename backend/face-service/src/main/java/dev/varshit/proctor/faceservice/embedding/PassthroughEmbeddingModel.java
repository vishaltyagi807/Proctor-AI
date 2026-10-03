package dev.varshit.proctor.faceservice.embedding;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

public class PassthroughEmbeddingModel implements EmbeddingModel {

    private final int dimensions;

    public PassthroughEmbeddingModel(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        int index = 0;
        for (String instruction : request.getInstructions()) {
            embeddings.add(new Embedding(EmbeddingCodec.decode(instruction), index++));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return EmbeddingCodec.decode(document.getText());
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}
