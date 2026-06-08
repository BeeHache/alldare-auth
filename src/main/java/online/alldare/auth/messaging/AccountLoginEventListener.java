package online.alldare.auth.messaging;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.alldare.auth.domain.entity.Account;
import online.alldare.auth.repository.AccountRepository;
import online.alldare.common.event.AccountLoginEvent;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountLoginEventListener implements StreamListener<String, ObjectRecord<String, String>> {

    private final ObjectMapper objectMapper;
    private final AccountRepository accountRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String STREAM_KEY = "stream:logins";
    private static final String GROUP_NAME = "alldare-auth-login-group";

    @Override
    @Transactional
    public void onMessage(ObjectRecord<String, String> record) {
        try {
            String jsonPayload = record.getValue();
            log.debug("Received AccountLoginEvent from stream: {}", jsonPayload);

            AccountLoginEvent event = objectMapper.readValue(jsonPayload, AccountLoginEvent.class);
            
            updateLastLogin(event);

            // Acknowledge the message
            redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, record.getId());
            log.info("Successfully processed and acknowledged AccountLoginEvent for account: {}", event.accountId());

        } catch (Exception e) {
            log.error("Error processing AccountLoginEvent from Redis Stream", e);
        }
    }

    private void updateLastLogin(AccountLoginEvent event) {
        accountRepository.findById(event.accountId()).ifPresentOrElse(
            account -> {
                account.setLastLogin(event.loginTime());
                accountRepository.save(account);
                log.debug("Updated last_login for account: {}", event.accountId());
            },
            () -> log.warn("Account not found for last_login update: {}", event.accountId())
        );
    }
}
