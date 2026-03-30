package platform_ocean.Entities.Messaging;

import java.util.List;
import java.util.UUID;

public class SimpleDataMapper {

    private final UUID sender;
    private final String message;
    private final UUID messageID;
    private final MessageProtocol protocol;
    private final List<UUID> recipients;

    public SimpleDataMapper(UUID sender, String message, UUID messageID, MessageProtocol protocol, List<UUID> recipients) {
        this.sender = sender;
        this.message = message;
        this.messageID = messageID;
        this.protocol = protocol;
        this.recipients = recipients;
    }

    public UUID getSender() {
        return sender;
    }


    public String getMessage() {
        return message;
    }


    public UUID getMessageID() {
        return messageID;
    }

    public MessageProtocol getProtocol() {
        return protocol;
    }

    public List<UUID> getRecipients() {
        return recipients;
    }

}
