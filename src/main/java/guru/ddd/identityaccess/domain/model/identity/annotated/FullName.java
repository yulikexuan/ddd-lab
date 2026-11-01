//: guru.ddd.identityaccess.domain.model.identity.annotated.FullName.java

package guru.ddd.identityaccess.domain.model.identity.annotated;


import static guru.ddd.common.util.AssertionConcern.assertArgumentLength;
import static guru.ddd.common.util.AssertionConcern.assertArgumentNotEmpty;

import lombok.NonNull;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.NullMarked;


@NullMarked
@ValueObject
public record FullName(@NonNull String firstName, @NonNull String lastName) {

    public FullName {

        assertArgumentNotEmpty(firstName, "First name is required.");
        assertArgumentLength(firstName, 1, 50,
                "The first name must be 50 characters or less.");
        firstName = firstName.trim();

        assertArgumentNotEmpty(lastName, "Last name is required.");
        assertArgumentLength(lastName, 1, 50,
                "The last name must be 50 characters or less.");
        lastName = lastName.trim();
    }

    public String formattedName() {
        return this.firstName() + " " + this.lastName();
    }

    public FullName withChangedFirstName(@NonNull String aFirstName) {
        return new FullName(aFirstName, this.lastName());
    }

    public FullName withChangedLastName(@NonNull String aLastName) {
        return new FullName(this.firstName(), aLastName);
    }

}
