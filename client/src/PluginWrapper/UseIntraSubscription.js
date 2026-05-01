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
      if (!deserialiseJSON) return;
      if (deserialiseJSON.protocol === "ERROR") {
        console.error(`[plugin ${routingKey}] server error`, deserialiseJSON);
        return;
      }
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
      const RecipientAddress = `/topic/${routingKey}/${clientID}/receive`;
      try {
        const subscription = client.subscribe(
          RecipientAddress,
          messageHandler,
          { id: `sub-${clientID}-${routingKey}` }
        );
        markReady(routingKey);
        return subscription;
      } catch (error) {
        console.log(error);
      }
      return null;
    };

    const subscription = subscribe();

    return () => {
      if (subscription) subscription.unsubscribe();
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
