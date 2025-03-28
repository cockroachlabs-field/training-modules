package io.cockroachdb.training.domain.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer")
public class Customer extends AbstractEntity<UUID> {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Customer instance = new Customer();

        private Builder() {
        }

        public Builder withGeneratedId() {
            instance.id = UUID.randomUUID();
            return this;
        }

        public Builder withFirstName(String firstName) {
            instance.firstName = firstName;
            return this;
        }

        public Builder withLastName(String lastName) {
            instance.lastName = lastName;
            return this;
        }

        public Builder withEmail(String email) {
            instance.email = email;
            return this;
        }

        public Builder withAddress(Address address) {
            instance.address = address;
            return this;
        }

        public Customer build() {
            return instance;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "first_name", length = 45)
    private String firstName;

    @Column(name = "last_name", length = 45)
    private String lastName;

    @Column(length = 128)
    private String email;

    @Embedded
    private Address address;

    @Override
    public UUID getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public Address getAddress() {
        return address;
    }
}
