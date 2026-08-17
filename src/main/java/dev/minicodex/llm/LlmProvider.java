package dev.minicodex.llm;

import dev.minicodex.agent.CancellationToken;
import dev.minicodex.llm.LlmModels.ProviderRequest;
import dev.minicodex.llm.LlmModels.ProviderResponse;

public interface LlmProvider {
    ProviderResponse generate(ProviderRequest request, CancellationToken cancellationToken) throws ProviderException;
}

