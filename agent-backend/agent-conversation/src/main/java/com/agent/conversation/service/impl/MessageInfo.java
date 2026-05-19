package com.agent.conversation.service.impl;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MessageInfo(
    @JsonProperty("role") String role,
    @JsonProperty("content") String content
) {}
