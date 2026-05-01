package platform_ocean.Controller.Messaging;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import platform_ocean.Config.Messaging.ConnectedClientRegistry;
import platform_ocean.Entities.Messaging.*;
import platform_ocean.Service.Messaging.OceanService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Controller
@CrossOrigin
public class MessagingController implements MessagingControllerInterface {

    @Autowired
    private OceanService serv;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ConnectedClientRegistry clientRegistry;

    private void routeMessage(UUID pluginKey, List<UUID> recipients,
                              ResponseEntity<SimpleDataMapper> response) {
        Set<UUID> connected = clientRegistry.getConnectedClients();
        StringBuilder topic = new StringBuilder("/topic/").append(pluginKey).append('/');
        int prefixLen = topic.length();

        Iterable<UUID> targets;
        if (recipients == null || recipients.isEmpty()) {
            targets = connected;
        } else {
            List<UUID> filtered = new ArrayList<>(recipients.size());
            for (UUID r : recipients) {
                if (connected.contains(r)) filtered.add(r);
            }
            targets = filtered;
        }

        for (UUID target : targets) {
            topic.setLength(prefixLen);
            topic.append(target).append("/receive");
            messagingTemplate.convertAndSend(topic.toString(), response);
        }
    }

    private void sendErrorToSender(UUID pluginKey, UUID clientKey, HttpStatus status, UUID relatedMessageId) {
        if (!clientRegistry.isConnected(clientKey)) return;

        StringBuilder topic = new StringBuilder("/topic/")
            .append(pluginKey).append('/').append(clientKey).append("/receive");

        SimpleDataMapper errorBody = new SimpleDataMapper(
            clientKey, null, relatedMessageId, MessageProtocol.ERROR, null
        );
        ResponseEntity<SimpleDataMapper> errorResponse = ResponseEntity.status(status).body(errorBody);
        messagingTemplate.convertAndSend(topic.toString(), errorResponse);
    }

    @Override
    @MessageMapping("/{ClientKey}/{PluginKey}/send")
    public void createMessage(@DestinationVariable("ClientKey") UUID clientKey,
                              @DestinationVariable("PluginKey") UUID pluginKey,
                              @Payload DataMapper dataFromFrontend) {

        dataFromFrontend.setClientKey(clientKey);
        dataFromFrontend.setPluginKey(pluginKey);

        if (dataFromFrontend.shouldPersist()) {
            boolean created = serv.createMessage(dataFromFrontend);
            if (!created) {
                sendErrorToSender(pluginKey, clientKey, HttpStatus.INSUFFICIENT_STORAGE, null);
                return;
            }
        }

        SimpleDataMapper parsedData = dataFromFrontend.castToSimpleDataMapper(MessageProtocol.CREATE);
        ResponseEntity<SimpleDataMapper> response = ResponseEntity.status(HttpStatus.CREATED).body(parsedData);
        routeMessage(pluginKey, dataFromFrontend.getRecipients(), response);
    }

    @Override
    @MessageMapping("{ClientKey}/{PluginKey}/delete")
    public void deleteMessage(@DestinationVariable("ClientKey") UUID clientKey,
                              @DestinationVariable("PluginKey") UUID pluginKey,
                              @Payload DeleteRequest messageDeleteRequest) {

        final UUID messageID = messageDeleteRequest.getMessageID();
        boolean canDelete = serv.matchRequestWithSender(clientKey, messageID);
        if (!canDelete) {
            sendErrorToSender(pluginKey, clientKey, HttpStatus.UNAUTHORIZED, messageID);
            return;
        }

        boolean deleted = serv.deleteMessage(messageID);
        if (!deleted) {
            sendErrorToSender(pluginKey, clientKey, HttpStatus.INSUFFICIENT_STORAGE, messageID);
            return;
        }

        SimpleDataMapper deleteConfirmed = new SimpleDataMapper(
            clientKey, null, messageID, MessageProtocol.DELETE, null);
        ResponseEntity<SimpleDataMapper> response = ResponseEntity.status(HttpStatus.OK).body(deleteConfirmed);
        routeMessage(pluginKey, null, response);
    }

    @Override
    @MessageMapping("{ClientKey}/{PluginKey}/update")
    public void updateMessage(@DestinationVariable("ClientKey") UUID clientKey,
                              @DestinationVariable("PluginKey") UUID pluginKey,
                              @Payload UpdatedDataMapper messageUpdateRequest) {

        UUID idForChange = messageUpdateRequest.getId();
        String contentToChange = messageUpdateRequest.getData();
        boolean updated = serv.updateMessage(idForChange, contentToChange);
        if (!updated) {
            sendErrorToSender(pluginKey, clientKey, HttpStatus.INSUFFICIENT_STORAGE, idForChange);
            return;
        }

        messageUpdateRequest.setClientKey(clientKey);
        messageUpdateRequest.setPluginKey(pluginKey);
        SimpleDataMapper updateRequest = messageUpdateRequest.castToSimpleDataMapper(MessageProtocol.UPDATE);
        ResponseEntity<SimpleDataMapper> response = ResponseEntity.status(HttpStatus.OK).body(updateRequest);
        routeMessage(pluginKey, messageUpdateRequest.getRecipients(), response);
    }

}
