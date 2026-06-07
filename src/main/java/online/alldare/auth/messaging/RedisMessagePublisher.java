package online.alldare.auth.messaging;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessagePublisher implements MessagePublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public <T> void publish(String streamName, T payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            log.debug("Publishing message to stream {}: {}", streamName, jsonPayload);
            
            ObjectRecord<String, String> record = StreamRecords.newRecord()
                    .in(streamName)
                    .ofObject(jsonPayload);
            
            redisTemplate.opsForStream().add(record);
            log.info("Message published to stream {}", streamName);
        } catch (Exception e) {
            log.error("Failed to publish message to stream {}", streamName, e);
            // We don't throw exception here to avoid breaking the main transaction 
            // if messaging is not critical for the immediate user response
        }
    }
}
