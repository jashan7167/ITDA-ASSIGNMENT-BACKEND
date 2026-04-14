package com.itda.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParticipantState {
    private Long userId;
    private String sessionId;
    private Boolean audioMuted;
    private Boolean videoDisabled;
    private Long joinedAt;
    private String username;
}
