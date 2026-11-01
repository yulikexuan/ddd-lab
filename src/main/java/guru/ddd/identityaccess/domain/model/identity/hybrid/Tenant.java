//: guru.ddd.identityaccess.domain.model.identity.hybrid.Tenant.java

package guru.ddd.identityaccess.domain.model.identity.hybrid;


public final class Tenant {

    private final TenantId id;

    private String name;

    private boolean active;

    public Tenant(TenantId id, String name, boolean active) {
        throw new UnsupportedOperationException("TODO J1");
    }

    public TenantId tenantId() {
        throw new UnsupportedOperationException("TODO J1");
    }

    public String name() {
        throw new UnsupportedOperationException("TODO J1");
    }

    public boolean isActive() {
        throw new UnsupportedOperationException("TODO J1");
    }

    public void activate() {
        throw new UnsupportedOperationException("TODO J1");
    }

    public void deactivate() {
        throw new UnsupportedOperationException("TODO J1");
    }

}
