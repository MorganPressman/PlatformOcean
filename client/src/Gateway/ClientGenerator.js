import { useState, useEffect } from "react";
import * as Stomp from "stompjs";
import SockJS from "sockjs-client";

export default function ClientGenerator(endpoint, clientID) {
  const [client, setClient] = useState(null);

  useEffect(() => {
    const socket = new SockJS(`${endpoint}/PlatformOcean`); // handshake with endpoint
    const clientHelper = Stomp.over(socket);
    clientHelper.debug = () => {};
    const connectHeaders = clientID ? { clientID } : {};
    clientHelper.connect(connectHeaders, () => setClient(clientHelper));
    // Cleanup on unmount
    return () => {
      clientHelper && clientHelper.disconnect();
    };
  }, [endpoint, clientID]);

  return client;
}
