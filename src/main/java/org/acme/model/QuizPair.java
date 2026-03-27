package org.acme.model;

import lombok.Builder;

/**
 * Represents a single quiz question-answer pair.
 *
 * @param wrdQuestion The word in the source language (question)
 * @param wrdAnswer   The word in the target language (expected answer)
 */
@Builder
public record QuizPair(String wrdQuestion,
                       String wrdQuestionDesc,
                       String wrdAnswer,
                       String wrdAnswerDesc) {
}
