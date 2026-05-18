package com.agent.conversation.service.impl;

import com.agent.conversation.service.ChatService;

import com.agent.conversation.entity.Message;
import com.agent.conversation.mapper.MessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatServiceImpl implements ChatService {

    @Autowired
    private MessageMapper messageMapper;

    @Override
    public Message saveUserMessage(Long conversationId, String content) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole("user");
        msg.setContent(content);
        messageMapper.insert(msg);
        return msg;
    }

    @Override
    public Message saveAssistantMessage(Long conversationId, String content) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole("assistant");
        msg.setContent(content);
        messageMapper.insert(msg);
        return msg;
    }
}
