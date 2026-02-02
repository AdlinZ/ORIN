package com.adlin.orin.modules.knowledge.component;

import java.util.List;

public interface EmbeddingService {
    /**
     * Embed text into vector.
     * 
     * @param text input text
     * @return vector as list of floats
     */
    List<Float> embed(String text);

    /**
     * Get the name of the embedding provider.
     * 
     * @return provider name (e.g. "siliconflow")
     */
    String getProviderName();
}
