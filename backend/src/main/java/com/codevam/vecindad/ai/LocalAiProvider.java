package com.codevam.vecindad.ai;

class LocalAiProvider implements AiProvider {

    @Override
    public String name() {
        return "local";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String answer(String systemPrompt, String userPrompt) {
        return "Gracias por escribir. En este momento el asistente virtual no está disponible. "
                + "Para consultar tu cuenta, reservas o PQRS, ingresa al portal de la copropiedad "
                + "o comunícate con la administración.";
    }
}
