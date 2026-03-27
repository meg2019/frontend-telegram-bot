package org.acme.service;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@QuarkusTest
class MenuServiceTest {

    private static Stream<Arguments> longStringsProvider() {
        return Stream.of(
                Arguments.of("My long string", new String[]{"My long", "string"}),
                Arguments.of("My very long string", new String[]{"My very", "long string"}),
                Arguments.of("SolidString", new String[]{"SolidString"}),
                Arguments.of(null, new String[]{})
        );
    }

    @ParameterizedTest
    @MethodSource("longStringsProvider")
    @DisplayName("Test long string formatting with {0} string)")
    void testLongStringFormatter(String input, String[] expected) {
        String[] actualResult = MenuService.longStringFormatter(input);
        Log.infof("We got: %s as result array", Arrays.toString(actualResult));
        assertArrayEquals(expected, actualResult);
    }
}