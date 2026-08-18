package dev.minicoder.llm;

import dev.minicoder.agent.CancellationToken;
import dev.minicoder.llm.LlmModels.ProviderRequest;
import dev.minicoder.llm.LlmModels.ProviderResponse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

public final class ScriptedLlmProvider implements LlmProvider {
    private final Deque<Object> script = new ArrayDeque<>();
    private final List<ProviderRequest> requests = new ArrayList<>();

    public ScriptedLlmProvider(Object... steps) {
        script.addAll(Arrays.asList(steps));
    }

    @Override
    public ProviderResponse generate(ProviderRequest request, CancellationToken cancellationToken)
            throws ProviderException {
        cancellationToken.throwIfCancelled();
        requests.add(request);
        if (script.isEmpty()) {
            throw new ProviderException(ProviderException.Category.PROTOCOL, false, 0,
                    "Script exhausted");
        }
        Object step = script.removeFirst();
        if (step instanceof ProviderException exception) throw exception;
        return (ProviderResponse) step;
    }

    public List<ProviderRequest> requests() {
        return List.copyOf(requests);
    }

    public int remainingSteps() {
        return script.size();
    }
}
