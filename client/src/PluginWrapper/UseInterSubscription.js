import { useEffect, useState } from "react";
import MessageProtocol from "./MessageProtocol";
import { useMessageQueues } from "./MessageQueue";
import { usePluginRegistry } from "../Contexts/PluginRegistryContext";
import { useClientDataContext } from "../Contexts/ClientContext";

export default function useInterSubscription(routingKey, depKey) {
  const { client, clientID } = useClientDataContext();
  const [canSubscribe, setCanSubscribe] = useState(!depKey);
  const { enqueueMessage, dequeueMessage, queueLength } = useMessageQueues();
  const { dataHistory, runMessageProtocol } = MessageProtocol(
    depKey,
    enqueueMessage
  );
  const { pluginReadyCount, areSubsReady } = usePluginRegistry();

  // dependency check
  useEffect(() => {
    if (canSubscribe) return;
    const ready = areSubsReady(routingKey);
    if (ready) {
      setCanSubscribe(true);
    }
  }, [canSubscribe, pluginReadyCount, areSubsReady, routingKey]);

  // subscribe once deps are ready
  useEffect(() => {
    if (!canSubscribe || !client || !depKey) return;

    const messageHandler = (resp) => {
      const deserialiseJSONHeaders = JSON.parse(resp.body);
      const deserialiseJSON = deserialiseJSONHeaders.body;
      const ParsedDatagram = {
        sender: deserialiseJSON.sender,
        message: JSON.parse(deserialiseJSON.message),
        messageID: deserialiseJSON.messageID,
      };
      runMessageProtocol(ParsedDatagram, deserialiseJSON.protocol);
    };

    const subscribe = () => {
      const BroadcastAddress = `/topic/${depKey}/receive`;
      const RecipientAddress = `/topic/${depKey}/${clientID}/receive`;

      try {
        const broadcastSubscription = client.subscribe(
          BroadcastAddress,
          messageHandler,
          { id: `sub-${clientID}-${routingKey}-${depKey}` }
        );
        const recipientSubscription = client.subscribe(
          RecipientAddress,
          messageHandler,
          { id: `sub-${clientID}-${routingKey}-${depKey}-recipient` }
        );
        return { broadcastSubscription, recipientSubscription };
      } catch (err) {
        console.log(err);
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
  }, [client, clientID, routingKey, depKey, runMessageProtocol, canSubscribe]);

  return {
    canSubscribe,
    watchers: {
      getData: dequeueMessage,
      numMessages: queueLength,
      dataHistory,
    },
  };
}
