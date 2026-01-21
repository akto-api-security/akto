import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import ThreatActorConfigComponent from "./ThreatActorConfig.jsx"
import RatelimitConfigComponent from "./RatelimitConfigComponent.jsx"
import ParamEnumerationConfigComponent from "./ParamEnumerationConfigComponent.jsx"
import ArchivalConfigComponent from "./ArchivalConfigComponent.jsx"

function ThreatConfiguration() {

    const components = [
        <ThreatActorConfigComponent
            title={"Actor Information"}
            description={"Configure threat actors. By default the actor is set to IP"}
            key={"actorConfig"}
        />,
        <RatelimitConfigComponent
            title={"Rate Limit Configuration"}
            description={"Configure rate limiting rules to protect your APIs from abuse."}
            key={"ratelimitConfig"}
        />,
        <ParamEnumerationConfigComponent
            title={"Param Enumeration Detection"}
            description={"Configure detection thresholds for parameter enumeration attacks (e.g., IDOR scanning)."}
            key={"paramEnumerationConfig"}
        />,
        <ArchivalConfigComponent
            title={"Deletion Configuration"}
            description={"Choose how long to retain malicious events before deletion."}
            key={"deletionConfig"}
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