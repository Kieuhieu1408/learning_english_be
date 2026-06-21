package com.learning.english.client.dto;

public record AIResult(
    String text,
    String translation,
    String partOfSpeech,
    String ipa,
    String exampleContext,
    String exampleTranslation
) {
    public static AIResultBuilder builder() {
        return new AIResultBuilder();
    }

    public static class AIResultBuilder {
        private String text;
        private String translation;
        private String partOfSpeech;
        private String ipa;
        private String exampleContext;
        private String exampleTranslation;

        public AIResultBuilder text(String text) {
            this.text = text;
            return this;
        }

        public AIResultBuilder translation(String translation) {
            this.translation = translation;
            return this;
        }

        public AIResultBuilder partOfSpeech(String partOfSpeech) {
            this.partOfSpeech = partOfSpeech;
            return this;
        }

        public AIResultBuilder ipa(String ipa) {
            this.ipa = ipa;
            return this;
        }

        public AIResultBuilder exampleContext(String exampleContext) {
            this.exampleContext = exampleContext;
            return this;
        }

        public AIResultBuilder exampleTranslation(String exampleTranslation) {
            this.exampleTranslation = exampleTranslation;
            return this;
        }

        public AIResult build() {
            return new AIResult(text, translation, partOfSpeech, ipa, exampleContext, exampleTranslation);
        }
    }
}
