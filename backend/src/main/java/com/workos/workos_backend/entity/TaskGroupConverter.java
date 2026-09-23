package com.workos.workos_backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores {@link TaskGroup} using the same lowercase strings the frontend
 * sends/expects (e.g. "this-week"), instead of the Java enum constant names.
 */
@Converter(autoApply = true)
public class TaskGroupConverter implements AttributeConverter<TaskGroup, String> {

    @Override
    public String convertToDatabaseColumn(TaskGroup attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public TaskGroup convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TaskGroup.fromValue(dbData);
    }
}
