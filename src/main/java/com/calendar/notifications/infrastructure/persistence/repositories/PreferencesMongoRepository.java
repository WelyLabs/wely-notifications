package com.calendar.notifications.infrastructure.persistence.repositories;

import com.calendar.notifications.infrastructure.persistence.entities.PreferencesEntity;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PreferencesMongoRepository extends ReactiveMongoRepository<PreferencesEntity, String> {
}
