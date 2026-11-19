import React, { useCallback, useMemo } from 'react';
import GithubServerTable from "@/apps/dashboard/components/tables/GithubServerTable";
import api from "../api";
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import { Box, HorizontalStack } from "@shopify/polaris";
import TooltipText from "@/apps/dashboard/components/shared/TooltipText";
import GetPrettifyEndpoint from "@/apps/dashboard/pages/observe/GetPrettifyEndpoint";
import LocalStore from "@/apps/main/LocalStorageStore";

const PAGE_SIZE = 10;

// the test sub categories more than one api has an open issue for, a page at a time
function IssuesBySubCategoryFlyout() {
  const getCollapsibleRow = (endpoints = []) => {
    return (
      <tr>
        <td colSpan={"100%"}>
          <Box background="bg" borderColor="border-subdued" borderBlockStartWidth="1">
            {endpoints.map((ep, index) => {
              const hasDivider = index < (endpoints.length - 1);
              return (
                <Box
                  padding={"2"}
                  paddingInlineEnd={"4"}
                  paddingInlineStart={"12"}
                  key={`${ep?.method}-${ep?.url}-${index}`}
                  borderColor="border-subdued"
                  {...(hasDivider ? { borderBlockEndWidth: "1" } : {})}
                >
                  <HorizontalStack gap={"4"} wrap={false}>
                    <GetPrettifyEndpoint url={ep?.url} method={ep?.method} />
                  </HorizontalStack>
                </Box>
              );
            })}
          </Box>
        </td>
      </tr>
    );
  };

  const fetchData = useCallback(async (sortKey, sortOrder, skip, limit) => {
    try {
      const resp = await api.fetchPostureActionItems('URLS_BY_ISSUE', skip, limit);
      const value = (resp?.subCategories || []).map((entry, idx) => {
        const subCategory = entry?.testSubCategory || "-";
        const subCategoryPretty = LocalStore.getState()?.subCategoryMap?.[subCategory]?.testName || subCategory;
        const endpoints = entry?.endpoints || [];
        return {
          id: `${subCategory}-${skip + idx}`,
          name: subCategory,
          subCategory: (
            <Box maxWidth="420px">
              <TooltipText tooltip={subCategoryPretty} text={subCategoryPretty} />
            </Box>
          ),
          endpoints,
          collapsibleRow: getCollapsibleRow(endpoints),
        };
      });
      return { value, total: resp?.total || 0 };
    } catch (e) {
      console.error("Error loading urls by issues:", e);
      return { value: [], total: 0 };
    }
  }, []);

  const headers = useMemo(
    () => [
      { title: '', type: CellType.COLLAPSIBLE },
      { text: "Test Subcategory", title: "Test Subcategory", value: "subCategory", isText: CellType.TEXT, maxWidth: "420px" },
    ],
    []
  );

  const resourceName = useMemo(
    () => ({ singular: "test subcategory", plural: "test subcategories" }),
    []
  );

  return (
    <GithubServerTable
      resourceName={resourceName}
      headers={headers}
      headings={headers}
      fetchData={fetchData}
      pageLimit={PAGE_SIZE}
      hidePageSizeSelector
      sortOptions={[]}
      filters={[]}
      selectable={false}
      useNewRow={true}
      condensedHeight={true}
      hideQueryField={true}
      skipUrlFilters
    />
  );
}

export default IssuesBySubCategoryFlyout;
