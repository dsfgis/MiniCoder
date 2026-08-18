package dev.minicoder.llm;

import dev.minicoder.agent.CancellationToken;
import dev.minicoder.llm.LlmModels.ProviderRequest;
import dev.minicoder.llm.LlmModels.ProviderResponse;

public interface LlmProvider {
    ProviderResponse generate(ProviderRequest request, CancellationToken cancellationToken) throws ProviderException;
}
