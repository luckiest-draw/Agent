package com.agent.conversation.service;

import com.agent.conversation.entity.Message;

public interface ChatService {
    Message saveUserMessage(Long conversationId, String content);
    Message saveAssistantMessage(Long conversationId, String content);
}
