//: guru.ddd.identityaccess.domain.model.identity.annotated.TenantId.java

package guru.ddd.identityaccess.domain.model.identity.annotated;


import java.util.UUID;

import guru.ddd.common.domain.model.IdentitySpec;
import lombok.NonNull;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.NullMarked;


@NullMarked
@ValueObject
public record TenantId(@NonNull String id) implements IdentitySpec {

    public static TenantId of(@NonNull UUID id) {
        return new TenantId(id.toString());
    }

    public TenantId {
        if (!UUID_FORMAT.matcher(id).matches()) {
            throw new IllegalArgumentException("The id has an invalid format.");
        }
    }

}
