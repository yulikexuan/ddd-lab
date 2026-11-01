//: guru.ddd.identityaccess.domain.model.identity.typed.Tenant.java

package guru.ddd.identityaccess.domain.model.identity.typed;


import static guru.ddd.common.util.AssertionConcern.assertArgumentLength;
import static guru.ddd.common.util.AssertionConcern.assertArgumentNotEmpty;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import org.jmolecules.ddd.types.AggregateRoot;


@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class Tenant implements AggregateRoot<Tenant, TenantId> {

    @EqualsAndHashCode.Include
    private final TenantId id;

    private String name;

    private boolean active;

    public Tenant(@NonNull TenantId id, @NonNull String name, boolean active) {
        this.id = id;
        this.name(name);
        this.active = active;
    }

    @Override
    public TenantId getId() {
        return id;
    }

    public String name() {
        return name;
    }

    private void name(String name) {
        assertArgumentNotEmpty(name, "The tenant name is required.");
        assertArgumentLength(name, 1, 100,
                "The name must be 100 characters or less.");
        this.name = name.trim();
    }

    public boolean isActive() {
        return this.active;
    }

    public void activate() {
        if (this.isActive()) {
            return;
        }
        this.active = true;
    }

    public void deactivate() {
        if (!this.isActive()) {
            return;
        }
        this.active = false;
    }

}