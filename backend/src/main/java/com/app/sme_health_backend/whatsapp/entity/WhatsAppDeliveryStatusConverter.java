package com.app.sme_health_backend.whatsapp.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class WhatsAppDeliveryStatusConverter implements AttributeConverter<WhatsAppDeliveryStatus, String> {

    @Override
    public String convertToDatabaseColumn(WhatsAppDeliveryStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.toDbValue();
    }

    @Override
    public WhatsAppDeliveryStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return WhatsAppDeliveryStatus.fromDbValue(dbData);
    }
}
