import ConfigureEventsPage from "./ConfigureEventsPage";

function ConfigureIgnoredEventsPage() {
    return <ConfigureEventsPage 
        categoryName="IgnoredEvent"
        pageTitle="Configure Ignored Events"
        pageTooltip="Manage ignored event configurations to filter out non-threatening activities"
        componentTitle="Ignored events"
    />;
}

export default ConfigureIgnoredEventsPage;
