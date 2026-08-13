package com.atguigu.llmcallback.Service;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
public class sendUrl {
    @Autowired
    RabbitTemplate rabbitTemplate;

    public void test(String url,String title){

        try {
            rabbitTemplate.convertAndSend("sendUrl",url+"|||"+title);
        } catch (Exception e) {
            log.warn("RabbitMQ unavailable, skip sending message", e);
        }

    }

//    @RabbitListener(queues = "sendUrl")
//    public void receiveUrl(String url, Channel channel, Message message) throws IOException {
//
//        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
//    }
}
