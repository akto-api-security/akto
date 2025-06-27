import { Modal, EmptySearchResult } from "@shopify/polaris";
import { useEffect, useState } from "react";
import PersistStore from '@/apps/main/PersistStore';
import observeApi from "@/apps/dashboard/pages/observe/api";
import GithubSimpleTable from "@/apps/dashboard/components/tables/GithubSimpleTable";
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow"
import GetPrettifyEndpoint from "@/apps/dashboard/pages/observe/GetPrettifyEndpoint";

const TestingEndpointsModal = ({ showTestingEndpointsModal, setShowTestingEndpointsModal, testingEndpoints }) => {

    const collectionsMap = PersistStore((state) => state.collectionsMap);

    const [apisList, setApisList] = useState([]);

    const handleClose = () => {
        setShowTestingEndpointsModal(false);
    }

    const populateApis = async () => {
        if (testingEndpoints) {
            let limitedApisList = [];

            if (testingEndpoints.type === "COLLECTION_WISE") {
                const collectionId = testingEndpoints.apiCollectionId;
                if (collectionId) {
                    try {
                        const response = await observeApi.fetchApiInfosForCollection(collectionId);
                        if (response?.apiInfoList) {
                            const apiInfoListIds = response.apiInfoList.map((api) => api.id);
                            limitedApisList = apiInfoListIds.slice(0, 5000);
                        }
                    } catch (error) {
                        console.error("Error fetching collection endpoints:", error);
                    }
                }
            } else if (testingEndpoints.type === "CUSTOM" && testingEndpoints.apisList) {
                limitedApisList = testingEndpoints.apisList.slice(0, 5000);
            }

            limitedApisList = limitedApisList.map(api => ({
                ...api,
                id: api.method + "###" + api.url + "###" + api.apiCollectionId + "###" + Math.random(),
                apiEndpointComp: <GetPrettifyEndpoint method={api.method} url={api.url} isNew={false} maxWidth="15vw" />,
                apiCollectionName: collectionsMap?.[api.apiCollectionId] || ""
            }));
            setApisList(limitedApisList);
        }
    }

    useEffect(() => {
        populateApis();
    },
        [testingEndpoints]
    );

    const resourceName = {
        singular: 'API endpoint',
        plural: 'API endpoints',
    };

    const headers = [
         {
            text: 'API endpoint',
            title: 'API endpoint',
            value: 'apiEndpointComp'
        },
        {
            text: 'API collection name',
            title: 'API collection name',
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
                    data={apisList}
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