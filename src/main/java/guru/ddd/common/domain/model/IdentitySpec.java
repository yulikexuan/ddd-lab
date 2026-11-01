//: guru.ddd.common.domain.model.IdentitySpec.java

package guru.ddd.common.domain.model;


import java.io.Serializable;
import java.util.regex.Pattern;


public interface IdentitySpec extends Serializable {

    Pattern UUID_FORMAT = Pattern.compile(
            "\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}");

    String id();
}
