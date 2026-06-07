package online.alldare.auth.messaging;

public interface MessagePublisher {
    <T> void publish(String streamName, T payload);
}
