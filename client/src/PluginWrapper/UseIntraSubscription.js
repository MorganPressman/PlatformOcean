import { useEffect } from "react";
import { useMessageQueues } from "./MessageQueue";
import MessageProtcol from "./MessageProtocol";
import { useClientDataContext } from "../Contexts/ClientContext";
import { usePluginRegistry } from "../Contexts/PluginRegistryContext";

export default function useIntraSubscription(routingKey) {
  const { client, clientID } = useClientDataContext();
  const { markReady } = usePluginRegistry();
  const { enqueueMessage, dequeueMessage, queueLength } = useMessageQueues();
  const { dataHistory, runMessageProtocol } = MessageProtcol(
    routingKey,
    enqueueMessage
  );

  useEffect(() => {
    if (!client) return;

    const messageHandler = (resp) => {
      const deserialiseJSONHeaders = JSON.parse(resp.body);
      const deserialiseJSON = deserialiseJSONHeaders.body;
      const JSONsender = deserialiseJSON.sender;
      const JSONmessage = JSON.parse(deserialiseJSON.message);
      const JSONmessageID = deserialiseJSON.messageID;
      const MessageProtcol = deserialiseJSON.protocol;
      const ParsedDatagram = {
        sender: JSONsender,
        message: JSONmessage,
        messageID: JSONmessageID,
      };
      runMessageProtocol(ParsedDatagram, MessageProtcol);
    };

    const subscribe = () => {
      const BroadcastAddress = `/topic/${routingKey}/receive`;
      const RecipientAddress = `/topic/${routingKey}/${clientID}/receive`;
      try {
        const broadcastSubscription = client.subscribe(
          BroadcastAddress,
          messageHandler,
          { id: `sub-${clientID}-${routingKey}` }
        );
        const recipientSubscription = client.subscribe(
          RecipientAddress,
          messageHandler,
          { id: `sub-${clientID}-${routingKey}-recipient` }
        );
        markReady(routingKey);
        return { broadcastSubscription, recipientSubscription };
      } catch (error) {
        console.log(error);
      }
      return null;
    };

    const subscriptions = subscribe();

    return () => {
      if (subscriptions) {
        subscriptions.broadcastSubscription && subscriptions.broadcastSubscription.unsubscribe();
        subscriptions.recipientSubscription && subscriptions.recipientSubscription.unsubscribe();
      }
    };
  }, [runMessageProtocol, markReady, client, clientID, routingKey]);

  function sendCreateMessage(processedData, shouldPersist = true, recipients = null) {
    const SenderRoutingAddress = `/app/${clientID}/${routingKey}/send`;
    const CreateStruct = JSON.stringify({
      dataNode: processedData,
      persist: shouldPersist,
      recipients: recipients,
    });
    try {
      client.send(SenderRoutingAddress, {}, CreateStruct);
    } catch (error) {
      console.log(error);
    }
  }

  function sendUpdateMessage(newMessage, messageID, recipients = null) {
    const SenderRoutingAddress = `/app/${clientID}/${routingKey}/update`;
    const UpdateStruct = JSON.stringify({
      dataNode: newMessage,
      persist: true,
      id: messageID,
      recipients: recipients,
    });
    try {
      client.send(SenderRoutingAddress, {}, UpdateStruct);
    } catch (error) {
      console.log(error);
    }
  }

  function sendDeleteMessage(messageID) {
    const SenderRoutingAddress = `/app/${clientID}/${routingKey}/delete`;
    const DeleteStruct = JSON.stringify({ messageID: messageID });
    try {
      client.send(SenderRoutingAddress, {}, DeleteStruct);
    } catch (error) {
      console.log(error);
    }
  }

  return {
    watchers: {
      getData: dequeueMessage,
      numMessages: queueLength,
      dataHistory,
    },
    actions: { sendCreateMessage, sendUpdateMessage, sendDeleteMessage },
  };
}
