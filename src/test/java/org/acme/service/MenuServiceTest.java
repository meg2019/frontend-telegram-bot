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
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class MenuServiceTest {

    private static Stream<Arguments> longStringsProvider() {
        return Stream.of(
                Arguments.of("My long string", new String[]{"My long", "string"}, 2),
                Arguments.of("My very long string", new String[]{"My very", "long string"}, 2),
                Arguments.of("SolidString", new String[]{"SolidString"}, 1),
                Arguments.of(null, new String[]{}, 0)
        );
    }

    @ParameterizedTest
    @MethodSource("longStringsProvider")
    @DisplayName("Test long string formatting with {0} string)")
    void testLongStringFormatter(String input, String[] expected, int expectedLength) {
        String[] actualResult = MenuService.longStringFormatter(input);
        Log.infof("We got: %s as result array, array length: %d", Arrays.toString(actualResult), actualResult.length);
        assertArrayEquals(expected, actualResult);
        assertEquals(expected.length, expectedLength);
    }
}