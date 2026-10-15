package com.calendar.notifications.application.dtos;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Which notifications to mark read: some by id, a whole group, or all. Exactly one of the three. */
public record ReadRequest(@Size(max = 500) List<String> ids, String groupKey, Boolean all) {

    @AssertTrue(message = "give exactly one of ids, groupKey or all=true")
    public boolean isExactlyOneSelector() {
        int given = (ids != null && !ids.isEmpty() ? 1 : 0)
                + (groupKey != null && !groupKey.isBlank() ? 1 : 0)
                + (Boolean.TRUE.equals(all) ? 1 : 0);
        return given == 1;
    }
}
