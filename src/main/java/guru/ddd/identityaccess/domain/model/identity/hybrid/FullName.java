//: guru.ddd.identityaccess.domain.model.identity.hybrid.FullName.java

package guru.ddd.identityaccess.domain.model.identity.hybrid;


public record FullName(String firstName, String lastName) {

    public FullName {
        throw new UnsupportedOperationException("TODO J1");
    }

    public String formattedName() {
        throw new UnsupportedOperationException("TODO J1");
    }

    public FullName withChangedFirstName(String aFirstName) {
        throw new UnsupportedOperationException("TODO J1");
    }

    public FullName withChangedLastName(String aLastName) {
        throw new UnsupportedOperationException("TODO J1");
    }

}
