package com.workos.workos_backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores {@link BoardIcon} using the same lowercase strings the frontend
 * sends/expects (e.g. "kanban"), instead of the Java enum constant names.
 */
@Converter(autoApply = true)
public class BoardIconConverter implements AttributeConverter<BoardIcon, String> {

    @Override
    public String convertToDatabaseColumn(BoardIcon attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public BoardIcon convertToEntityAttribute(String dbData) {
        return dbData == null ? null : BoardIcon.fromValue(dbData);
    }
}
