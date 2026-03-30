package platform_ocean.Entities.Messaging;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OceanMessageConverter extends JsonDeserializer<DataMapper> {

    private static final long serialVersionUID = 1L;

    @Override
    @Bean
    public DataMapper deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {

        ObjectMapper mapper = new ObjectMapper();

        TreeNode node = parser.getCodec().readTree(parser);

        TreeNode dataFromPayload = node.get("dataNode");
        TreeNode persistFromPayload = node.get("persist");

        JsonNode data = mapper.treeToValue(dataFromPayload, JsonNode.class);
        JsonNode persist = mapper.treeToValue(persistFromPayload, JsonNode.class);

        Boolean parsedPersist = persist.asBoolean();

        TreeNode recipientsFromPayload = node.get("recipients");
        List<UUID> parsedRecipients = null;
        if (recipientsFromPayload != null && recipientsFromPayload.isArray()) {
            parsedRecipients = new ArrayList<>();
            for (int i = 0; i < ((JsonNode) recipientsFromPayload).size(); i++) {
                String recipientStr = ((JsonNode) recipientsFromPayload).get(i).asText();
                parsedRecipients.add(UUID.fromString(recipientStr));
            }
        }

        return new DataMapper(data, parsedPersist, parsedRecipients);
    }

    @Bean
    public SimpleModule oceanMessageDeserialiser() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(DataMapper.class, new OceanMessageConverter());
        return module;
    }

    public static long getSerialversionuid() {
        return serialVersionUID;
    }

}