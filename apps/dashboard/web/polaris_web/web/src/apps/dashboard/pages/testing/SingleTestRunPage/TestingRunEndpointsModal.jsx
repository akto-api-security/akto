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
            text: mapLabel(dashboardCategory, 'API') + ' collection name',
            title: mapLabel(dashboardCategory, 'API') + ' collection name',
            value: 'apiCollectionName',
            isText: CellType.TEXT
        }
    ]

    const emptyStateMarkup = (
        <EmptySearchResult
            title={'No APIs found'}
            withIllustration
        />
    );

    return (
        <Modal
            open={showTestingEndpointsModal}
            onClose={handleClose}
            title={"See APIs"}
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