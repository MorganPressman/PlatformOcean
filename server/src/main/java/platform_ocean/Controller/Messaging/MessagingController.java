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
import platform_ocean.Entities.Messaging.*;
import platform_ocean.Service.Messaging.OceanService;

import java.util.List;
import java.util.UUID;

@Controller
@CrossOrigin
public class MessagingController implements MessagingControllerInterface {

    @Autowired
    private OceanService serv;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private void routeMessage(UUID pluginKey, List<UUID> recipients, ResponseEntity<SimpleDataMapper> response) {
        if (recipients != null && !recipients.isEmpty()) {
            for (UUID recipient : recipients) {
                messagingTemplate.convertAndSend(
                    "/topic/" + pluginKey + "/" + recipient + "/receive",
                    response
                );
            }
        } else {
            messagingTemplate.convertAndSend(
                "/topic/" + pluginKey + "/receive",
                response
            );
        }
    }

    @Override
    @MessageMapping("/{ClientKey}/{PluginKey}/send")
    public void createMessage(@DestinationVariable("ClientKey") UUID clientKey,
                              @DestinationVariable("PluginKey") UUID pluginKey, @Payload DataMapper dataFromFrontend) {

        dataFromFrontend.setClientKey(clientKey);
        dataFromFrontend.setPluginKey(pluginKey);
        if (dataFromFrontend.shouldPersist()) {
            boolean created = serv.createMessage(dataFromFrontend);
            if (!created) {
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
                              @DestinationVariable("PluginKey") UUID pluginKey, @Payload DeleteRequest messageDeleteRequest) {

        final UUID messageID = messageDeleteRequest.getMessageID();
        boolean canDelete = serv.matchRequestWithSender(clientKey, messageID);

        if (!canDelete) {
            return;
        }

        boolean deleted = serv.deleteMessage(messageID);
        if (!deleted) {
            return;
        }

        SimpleDataMapper deleteConfirmed = new SimpleDataMapper(clientKey, null, messageID, MessageProtocol.DELETE, null);
        ResponseEntity<SimpleDataMapper> response = ResponseEntity.status(HttpStatus.OK).body(deleteConfirmed);
        messagingTemplate.convertAndSend("/topic/" + pluginKey + "/receive", response);
    }

    @Override
    @MessageMapping("{ClientKey}/{PluginKey}/update")
    public void updateMessage(@DestinationVariable("ClientKey") UUID clientKey,
                              @DestinationVariable("PluginKey") UUID pluginKey, @Payload UpdatedDataMapper messageUpdateRequest) {

        UUID idForChange = messageUpdateRequest.getId();
        String contentToChange = messageUpdateRequest.getData();
        boolean updated = serv.updateMessage(idForChange, contentToChange);
        if (!updated) {
            return;
        }
        messageUpdateRequest.setClientKey(clientKey);
        messageUpdateRequest.setPluginKey(pluginKey);
        SimpleDataMapper updateRequest = messageUpdateRequest.castToSimpleDataMapper(MessageProtocol.UPDATE);
        ResponseEntity<SimpleDataMapper> response = ResponseEntity.status(HttpStatus.OK).body(updateRequest);
        routeMessage(pluginKey, messageUpdateRequest.getRecipients(), response);
    }

}
