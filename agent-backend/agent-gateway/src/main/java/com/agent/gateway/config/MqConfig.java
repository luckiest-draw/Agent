package com.agent.gateway.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqConfig {

    public static final String QUEUE_DOC_PROCESS = "doc.process.queue";
    public static final String QUEUE_WORKFLOW_EXEC = "workflow.exec.queue";
    public static final String EXCHANGE_AGENT = "agent.exchange";
    public static final String ROUTING_DOC_PROCESS = "doc.process";
    public static final String ROUTING_WORKFLOW_EXEC = "workflow.exec";

    @Bean
    public TopicExchange agentExchange() {

        return new TopicExchange(EXCHANGE_AGENT);
    }

    @Bean
    public Queue docProcessQueue() {

        return new Queue(QUEUE_DOC_PROCESS, true);
    }

    @Bean
    public Queue workflowExecQueue() {

        return new Queue(QUEUE_WORKFLOW_EXEC, true);
    }

    @Bean
    public Binding docProcessBinding() {
        return BindingBuilder.bind(docProcessQueue()).to(agentExchange()).with(ROUTING_DOC_PROCESS);
    }

    @Bean
    public Binding workflowExecBinding() {
        return BindingBuilder.bind(workflowExecQueue()).to(agentExchange()).with(ROUTING_WORKFLOW_EXEC);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }
}
