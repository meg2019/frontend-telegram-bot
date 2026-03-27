package org.acme.processor;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CallbackQueryProcessorTest {

    private static final List<Integer> INTEGERS = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    private static Stream<Arguments> providesSourceAndResult() {
        return Stream.of(
                Arguments.of(INTEGERS, 5, 5),
                Arguments.of(INTEGERS, 8, 8),
                Arguments.of(INTEGERS, 10, 10)
        );
    }

    @ParameterizedTest
    @MethodSource("providesSourceAndResult")
    @DisplayName("Should return n random elements from a list")
    void getNRandomElements(List<Integer> source, int n, int expected) {
        List<Integer> nRandomElements = CallbackQueryProcessor.getNRandomElements(source, n);
        Log.infof("We got %s as result list", nRandomElements);
        assertEquals(expected, nRandomElements.size());
    }
}