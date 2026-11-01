//: guru.ddd.identityaccess.domain.model.identity.hybrid.TenantId.java

package guru.ddd.identityaccess.domain.model.identity.hybrid;


import java.util.UUID;

import guru.ddd.common.domain.model.IdentitySpec;


public record TenantId(String id) implements IdentitySpec {

    public static TenantId of(UUID id) {
        throw new UnsupportedOperationException("TODO J1");
    }

    public TenantId {
        throw new UnsupportedOperationException("TODO J1");
    }

}
