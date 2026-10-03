package dev.varshit.proctor.faceservice.config;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import dev.varshit.proctor.faceservice.vision.ArcFaceEmbedder;
import dev.varshit.proctor.faceservice.vision.FaceDetector;
import dev.varshit.proctor.faceservice.vision.FaceEmbedder;
import dev.varshit.proctor.faceservice.vision.ScrfdFaceDetector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class OnnxRuntimeConfiguration {

    @Bean
    public OrtEnvironment ortEnvironment() {
        return OrtEnvironment.getEnvironment();
    }

    @Bean(destroyMethod = "close")
    @Lazy
    public FaceDetector faceDetector(OrtEnvironment environment, FaceModelProperties properties) throws OrtException {
        OrtSession session = environment.createSession(properties.detectionModelPath(), new OrtSession.SessionOptions());
        return new ScrfdFaceDetector(
                environment,
                session,
                properties.detectionInputSize(),
                properties.detectionScoreThreshold(),
                properties.detectionNmsThreshold());
    }

    @Bean(destroyMethod = "close")
    @Lazy
    public FaceEmbedder faceEmbedder(
            OrtEnvironment environment,
            FaceModelProperties properties,
            @Value("${spring.ai.vectorstore.pgvector.dimensions:512}") int dimensions) throws OrtException {
        OrtSession session = environment.createSession(properties.embeddingModelPath(), new OrtSession.SessionOptions());
        return new ArcFaceEmbedder(environment, session, dimensions);
    }
}
