package com.durgaprasad.nsai.llm;

/**
 * Minimal abstraction over the neural layer: send a prompt, get text back.
 * Keeps the gate independent of any one LLM vendor.
 */
public interface LlmClient {

    /**
     * @param prompt the full prompt to send
     * @return the model's raw text output
     * @throws LlmException if the model cannot be reached or returns an error
     */
    String complete(String prompt);

    /** Short name for logs, e.g. {@code mock} or {@code ollama:llama3.2}. */
    String name();
}
