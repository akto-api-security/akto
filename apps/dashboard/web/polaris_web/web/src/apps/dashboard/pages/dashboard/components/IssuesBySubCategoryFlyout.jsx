import React, { useEffect, useMemo, useState } from 'react';
import GithubSimpleTable from "@/apps/dashboard/components/tables/GithubSimpleTable";
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import { Box, HorizontalStack } from "@shopify/polaris";
import TooltipText from "@/apps/dashboard/components/shared/TooltipText";
import GetPrettifyEndpoint from "@/apps/dashboard/pages/observe/GetPrettifyEndpoint";
import LocalStore from "@/apps/main/LocalStorageStore";

function IssuesBySubCategoryFlyout({ urlsByIssues }) {
  const [loading, setLoading] = useState(true);
  const [rows, setRows] = useState([]);

  useEffect(() => {
    let isMounted = true;
    (() => {
      setLoading(true);
      try {
        const testSubCategories = urlsByIssues?.testSubCategories || [];

        const processed = testSubCategories.map((entry, idx) => {
          const subCategory = entry?._id?.testSubCategory || "-";
          const subCategoryPretty = LocalStore.getState()?.subCategoryMap?.[subCategory]?.testName || subCategory;
          const endpoints = Array.isArray(entry?.apiInfoKeySet) ? entry.apiInfoKeySet : [];

          return {
            id: `${subCategory}-${idx}`,
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

        if (isMounted) setRows(processed);
      } catch (e) {
        if (isMounted) setRows([]);
        console.error("Error preparing urls by issues:", e);
      } finally {
        if (isMounted) setLoading(false);
      }
    })();

    return () => {
      isMounted = false;
    };
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

  return (
    <GithubSimpleTable
      data={rows}
      resourceName={resourceName}
      headers={headers}
      headings={headers}
      useNewRow={true}
      condensedHeight={true}
      hideQueryField={true}
      pageLimit={10}
      showFooter={true}
      loading={loading}
      treeView={false}
      emptyStateMarkup={null}
      emptyStateMessage="No test subcategories found"
    />
  );
}

export default IssuesBySubCategoryFlyout;


