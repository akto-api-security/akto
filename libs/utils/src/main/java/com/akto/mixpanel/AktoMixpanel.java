package com.akto.mixpanel;

import com.mixpanel.mixpanelapi.ClientDelivery;
import com.mixpanel.mixpanelapi.MessageBuilder;
import com.mixpanel.mixpanelapi.MixpanelAPI;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class AktoMixpanel {
    private static final Logger logger = LoggerFactory.getLogger(AktoMixpanel.class);

    private static final String MIXPANEL_PROJECT_TOKEN = System.getenv("MIXPANEL_PROJECT_TOKEN");

    private final MixpanelAPI mixpanel = new MixpanelAPI();

    public static AktoMixpanel instance = new AktoMixpanel();

    public void sendEvent(String distinctId, String eventName, JSONObject props) {

        if (StringUtils.isEmpty(MIXPANEL_PROJECT_TOKEN)) return;

        try {
            MessageBuilder messageBuilder = new MessageBuilder(MIXPANEL_PROJECT_TOKEN);

            JSONObject event = messageBuilder.event(distinctId, eventName, props);

            ClientDelivery delivery = new ClientDelivery();

            delivery.addMessage(event);

            mixpanel.deliver(delivery);
        } catch (IOException e) {
            logger.error("Failed to raise mixpanel event", e);
        }

    }
}
