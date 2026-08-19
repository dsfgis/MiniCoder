package dev.minicoder.llm;

import dev.minicoder.agent.CancellationToken;
import dev.minicoder.llm.LlmModels.ProviderRequest;
import dev.minicoder.llm.LlmModels.ProviderResponse;

/**
 * 约束模型供应商适配器的统一生成接口，使 AgentRuntime 不依赖具体线协议。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public interface LlmProvider {
    ProviderResponse generate(ProviderRequest request, CancellationToken cancellationToken) throws ProviderException;
}
