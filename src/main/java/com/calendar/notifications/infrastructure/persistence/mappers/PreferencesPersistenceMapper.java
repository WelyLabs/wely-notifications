package com.calendar.notifications.infrastructure.persistence.mappers;

import com.calendar.notifications.domain.models.NotificationPreferences;
import com.calendar.notifications.infrastructure.persistence.entities.PreferencesEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PreferencesPersistenceMapper {

    PreferencesEntity toEntity(NotificationPreferences preferences);

    NotificationPreferences toDomain(PreferencesEntity entity);
}
