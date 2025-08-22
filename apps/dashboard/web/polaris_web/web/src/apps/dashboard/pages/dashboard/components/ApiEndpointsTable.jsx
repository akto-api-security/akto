import React from 'react';
import GithubSimpleTable from "@/apps/dashboard/components/tables/GithubSimpleTable";
import { EmptySearchResult } from "@shopify/polaris";
import PersistStore from '../../../../main/PersistStore';
import { labelMap } from '../../../../main/labelHelperMap';

function ApiEndpointsTable({ data, headers, loading, pageLimit = 10, showFooter = true, emptyStateTitle = 'No APIs found', emptyStateDescription = '', ...rest }) {
    const dashboardCategory = PersistStore(state => state.dashboardCategory)
    const resourceName = {
        singular: labelMap[dashboardCategory]["API endpoint"],
        plural: labelMap[dashboardCategory]["API endpoints"],
    };

    const emptyStateMarkup = (
        <EmptySearchResult
            title={loading ? 'Loading APIs...' : emptyStateTitle}
            description={loading ? 'Please wait while we fetch the data.' : emptyStateDescription}
            withIllustration
        />
    );

    return (
        <div style={{ 
            width: '100%',
            height: '100%',
            overflow: 'visible' 
        }}>
            <div style={{
                border: 'none', 
                borderRadius: '0', 
                boxShadow: 'none', 
                overflow: 'visible' 
            }}>
                <GithubSimpleTable
                    data={data}
                    resourceName={resourceName}
                    headers={headers}
                    useNewRow={true}
                    condensedHeight={true}
                    hideQueryField={true} 
                    headings={headers}
                    pageLimit={pageLimit}
                    showFooter={showFooter}
                    emptyStateMarkup={emptyStateMarkup}
                    loading={loading}
                    style={{
                        border: 'none',
                        borderRadius: '0',
                        boxShadow: 'none',
                        overflow: 'visible'
                    }}
                    {...rest}
                />
            </div>
        </div>
    );
}

export default ApiEndpointsTable;