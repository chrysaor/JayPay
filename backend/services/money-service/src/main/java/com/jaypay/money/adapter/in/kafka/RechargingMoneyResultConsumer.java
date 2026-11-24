package com.jaypay.money.adapter.in.kafka;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaypay.common.CountDownLatchManager;
import com.jaypay.common.LoggingProducer;
import com.jaypay.common.RechargingMoneyTask;
import com.jaypay.common.SubTask;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RechargingMoneyResultConsumer {

    private final ObjectMapper mapper = new ObjectMapper();
    private final LoggingProducer loggingProducer;
    private final CountDownLatchManager countDownLatchManager;

    @KafkaListener(topics = "${task.result.topic}", groupId = "my-group")
    public void consume(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.info("[TaskResultConsumer] Received message: {} / {}", record.key(), record.value());

        RechargingMoneyTask task = mapper.readValue(record.value(), RechargingMoneyTask.class);

        boolean taskResult = true;
        for (SubTask subTask : task.getSubTaskList()) {
            if (subTask.getStatus().equals("fail")) {
                taskResult = false;
                break;
            }
        }

        if (taskResult) {
            this.loggingProducer.sendMessage(task.getTaskID(), "task success");
            this.countDownLatchManager.setDataForKey(task.getTaskID(), "success");
        } else {
            this.loggingProducer.sendMessage(task.getTaskID(), "task failed");
            this.countDownLatchManager.setDataForKey(task.getTaskID(), "failed");
        }

        this.countDownLatchManager.getCountDownLatch(task.getTaskID()).countDown();
    }
}
