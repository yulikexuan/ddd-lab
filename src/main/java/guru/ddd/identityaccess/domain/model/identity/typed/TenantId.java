//: guru.ddd.identityaccess.domain.model.identity.typed.TenantId.java

package guru.ddd.identityaccess.domain.model.identity.typed;


import java.util.UUID;

import guru.ddd.common.domain.model.IdentitySpec;
import lombok.NonNull;
import org.jmolecules.ddd.types.Identifier;
import org.jspecify.annotations.NullMarked;


@NullMarked
public record TenantId(@NonNull String id) implements IdentitySpec, Identifier {

    public static TenantId of(@NonNull UUID id) {
        return new TenantId(id.toString());
    }

    public TenantId {
        if (!UUID_FORMAT.matcher(id).matches()) {
            throw new IllegalArgumentException("The id has an invalid format.");
        }
    }

}