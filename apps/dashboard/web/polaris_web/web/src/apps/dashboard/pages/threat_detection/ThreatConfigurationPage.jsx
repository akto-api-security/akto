import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import ThreatConfigurationComponent from "./components/ThreatConfigurationComponent";

function ThreatConfigurationPage() {
    return <PageWithMultipleCards
        title={
            <TitleWithInfo
                titleText={"Configure Threat Detection"}
                tooltipContent={"Configure Akto's powerful threat detection capabilities"}
            />
        }
        isFirstPage={true}
        components={[
            <ThreatConfigurationComponent key="threat-configuration-component" />
        ]}
    />;
}

export default ThreatConfigurationPage;