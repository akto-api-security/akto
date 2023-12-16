package com.akto.mixpanel;

import com.akto.dao.ConfigsDao;
import com.akto.dto.Config;
import com.mixpanel.mixpanelapi.ClientDelivery;
import com.mixpanel.mixpanelapi.MessageBuilder;
import com.mixpanel.mixpanelapi.MixpanelAPI;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class AktoMixpanel {
    private static final Logger logger = LoggerFactory.getLogger(AktoMixpanel.class);
    private Config.MixpanelConfig mixpanelConfig = null;
    public AktoMixpanel() {
        if (mixpanelConfig == null) {
            synchronized (AktoMixpanel.class) {
                if (mixpanelConfig == null) {
                    try {
                        Config config = ConfigsDao.instance.findOne("_id", "MIXPANEL-ankush");
                        if (config == null) {
                            logger.error("No mixpanel config found");
                        } else {
                            mixpanelConfig = (Config.MixpanelConfig) config;
                        }
                    } catch (Exception e) {
                        logger.error("Error while fetching mixpanel config: " + e.getMessage());
                    }
                }
            }
        }
    }

    private final MixpanelAPI mixpanel = new MixpanelAPI();

    public static AktoMixpanel instance = new AktoMixpanel();

    public void sendEvent(String distinctId, String eventName, JSONObject props) {

        if (mixpanelConfig == null) {
            throw new IllegalStateException("Mixpanel config is not initialised");
        }
        try {
            String projectToken = mixpanelConfig.getProjectToken();
            
            MessageBuilder messageBuilder = new MessageBuilder(projectToken);

            JSONObject event = messageBuilder.event(distinctId, eventName, props);

            ClientDelivery delivery = new ClientDelivery();

            delivery.addMessage(event);

            mixpanel.deliver(delivery);
        } catch (IOException e) {
            logger.error("Failed to raise mixpanel event", e);
        }

    }
}
