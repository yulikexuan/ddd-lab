//: guru.ddd.common.util.AssertionConcern.java

package guru.ddd.common.util;


import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NullMarked;


@NullMarked
public final class AssertionConcern {

    private AssertionConcern() {
    }

    public static void assertArgumentNotEmpty(String aString, String aMessage) {

        if (StringUtils.isBlank(aString)) {
            throw new IllegalArgumentException(aMessage);
        }
    }

    public static void assertArgumentLength(
            @NonNull String aString,
            int aMinimum,
            int aMaximum,
            @NonNull String aMessage) {

        int length = aString.trim().length();
        if (length < aMinimum || length > aMaximum) {
            throw new IllegalArgumentException(aMessage);
        }
    }

}
