package com.codevam.vecindad.ai;

interface AiProvider {
    String name();
    boolean isConfigured();
    String answer(String systemPrompt, String userPrompt);
}
