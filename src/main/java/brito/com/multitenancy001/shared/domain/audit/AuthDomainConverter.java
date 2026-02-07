package brito.com.multitenancy001.shared.domain.audit;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class AuthDomainConverter implements AttributeConverter<AuthDomain, String> {

    @Override
    public String convertToDatabaseColumn(AuthDomain attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public AuthDomain convertToEntityAttribute(String dbData) {
        return AuthDomain.fromDbValueOrNull(dbData);
    }
}
