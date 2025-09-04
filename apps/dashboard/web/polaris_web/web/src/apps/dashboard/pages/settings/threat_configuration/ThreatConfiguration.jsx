import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import ThreatActorConfigComponent from "./ThreatActorConfig.jsx"
import RatelimitConfigComponent from "./RatelimitConfigComponent.jsx"

function ThreatConfiguration() {

    const components = [
        <ThreatActorConfigComponent
            title={"Actor Information"}
            description={"Configure threat actors. By default the actor is set to IP"}
            key={"actorConfig"}
        />,
        <RatelimitConfigComponent
            title={"Rate Limit Configuration"}
            description={"Configure rate limiting rules to protect your APIs from abuse. Set thresholds, time windows, and actions for different API endpoints."}
            key={"ratelimitConfig"}
        />
    ];

    return (
        <PageWithMultipleCards
            title={"Threat Configuration"}
            isFirstPage={true}
            divider={true}
            components={components}
        />
    )
}

export default ThreatConfiguration;