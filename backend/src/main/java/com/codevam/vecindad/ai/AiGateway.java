package com.codevam.vecindad.ai;

public interface AiGateway {
    boolean isConfigured();
    String providerName();
    String answer(String systemPrompt, String userPrompt);
}
