import FlyLayout from "../../../components/layouts/FlyLayout";
import SampleDataList from "../../../components/shared/SampleDataList";

function SampleDetails(props) {
    const { showDetails, setShowDetails, sampleData, title } = props

    const sampleDataComponent = sampleData.length > 0 ?
        <SampleDataList
            key="Sample values"
            sampleData={sampleData}
            heading={"Sample values"}
            minHeight={"35vh"}
            vertical={true}
        /> : <></>

    const components = [sampleDataComponent]
    return <FlyLayout
        title={title || ""}
        show={showDetails}
        setShow={setShowDetails}
        components={components}
    />
}

export default SampleDetails;