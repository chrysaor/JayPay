package com.jaypay.taskconsumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaypay.common.RechargingMoneyTask;
import com.jaypay.common.SubTask;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskConsumer {

    private final ObjectMapper mapper = new ObjectMapper();
    private final TaskResultProducer taskResultProducer;

    @KafkaListener(topics = "${task.topic}", groupId = "my-group")
    public void consume(String message) throws JsonProcessingException {
        RechargingMoneyTask task = mapper.readValue(message, RechargingMoneyTask.class);

        for (SubTask subTask : task.getSubTaskList()) {
            subTask.setStatus("success");
        }

        this.taskResultProducer.sendTaskResult(task.getTaskID(), task);
    }
}
