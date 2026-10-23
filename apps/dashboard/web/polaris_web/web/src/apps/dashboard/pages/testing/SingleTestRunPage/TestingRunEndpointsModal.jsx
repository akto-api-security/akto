import { Modal, EmptySearchResult } from "@shopify/polaris";
import GithubSimpleTable from "@/apps/dashboard/components/tables/GithubSimpleTable";
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow"
import TestingStore from "../testingStore";
import PersistStore from "../../../../main/PersistStore";
import { labelMap } from "../../../../main/labelHelperMap";
import { mapLabel } from "../../../../main/labelHelper";


const TestingEndpointsModal = ({ showTestingEndpointsModal, setShowTestingEndpointsModal }) => {

    const testingEndpointsApisList = TestingStore((state) => state.testingEndpointsApisList);
    const dashboardCategory = PersistStore(state => state.dashboardCategory)
    const handleClose = () => {
        setShowTestingEndpointsModal(false);
    }

    const resourceName = {
        singular: labelMap[dashboardCategory]["API endpoint"],
        plural: labelMap[dashboardCategory]["API endpoints"],
    };

    const headers = [
         {
            text: labelMap[dashboardCategory]["API endpoint"],
            title: labelMap[dashboardCategory]["API endpoint"],
            value: 'apiEndpointComp'
        },
        {
            text: mapLabel('API', dashboardCategory) + ' collection name',
            title: mapLabel('API', dashboardCategory) + ' collection name',
            value: 'apiCollectionName',
            isText: CellType.TEXT
        }
    ]

    const emptyStateMarkup = (
        <EmptySearchResult
            title={'No ' + mapLabel('APIs', dashboardCategory) + ' found'}
            withIllustration
        />
    );

    return (
        <Modal
            open={showTestingEndpointsModal}
            onClose={handleClose}
            title={"See " + mapLabel('APIs', dashboardCategory)}
            fullScreen={true}
        >
            <Modal.Section>
                <GithubSimpleTable
                    key="apisList"
                    data={testingEndpointsApisList}
                    resourceName={resourceName}
                    headers={headers}
                    useNewRow={true}
                    condensedHeight={true}
                    hideQueryField={true}
                    headings={headers}
                    pageLimit={10}
                    showFooter={false}
                    emptyStateMarkup={emptyStateMarkup}
                />
            </Modal.Section>
        </Modal>
    );
}

export default TestingEndpointsModal;