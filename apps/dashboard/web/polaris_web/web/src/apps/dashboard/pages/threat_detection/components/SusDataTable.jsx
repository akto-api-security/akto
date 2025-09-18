import { useEffect, useState } from "react";
import GithubServerTable from "../../../components/tables/GithubServerTable";
import api from "../api";
import { CellType } from "../../../components/tables/rows/GithubRow";
import GetPrettifyEndpoint from "../../observe/GetPrettifyEndpoint";
import PersistStore from "../../../../main/PersistStore";
import func from "../../../../../util/func";
import { Badge, IndexFiltersMode } from "@shopify/polaris";
import dayjs from "dayjs";
import SessionStore from "../../../../main/SessionStore";
import { labelMap } from "../../../../main/labelHelperMap";
import { formatActorId } from "../utils/formatUtils";
import useTable from "../../../components/tables/TableContext";
import threatDetectionRequests from "../api";

const resourceName = {
  singular: "sample",
  plural: "samples",
};

const headers = [
  {
    text: "Severity",
    value: "severityComp",
    title: "Severity",
  },
  {
    text: labelMap[PersistStore.getState().dashboardCategory]["API endpoint"],
    value: "endpointComp",
    title: labelMap[PersistStore.getState().dashboardCategory]["API endpoint"],
  },
  {
    text: "Threat Actor",
    value: "actorComp",
    title: "Actor",
    filterKey: 'actor'
  },
  {
    text: "Filter",
    value: "filterId",
    title: "Attack type",
  },
  {
    text: "Collection",
    value: "apiCollectionName",
    title: "Collection",
    maxWidth: "95px",
    type: CellType.TEXT,
  },
  {
    text: "Discovered",
    title: "Detected",
    value: "discoveredTs",
    type: CellType.TEXT,
    sortActive: true,
  },
];

const sortOptions = [
  {
    label: "Discovered time",
    value: "detectedAt asc",
    directionLabel: "Newest",
    sortKey: "detectedAt",
    columnIndex: 5,
  },
  {
    label: "Discovered time",
    value: "detectedAt desc",
    directionLabel: "Oldest",
    sortKey: "detectedAt",
    columnIndex: 5,
  },
];

let filters = [];

function SusDataTable({ currDateRange, rowClicked, triggerRefresh }) {
  const getTimeEpoch = (key) => {
    return Math.floor(Date.parse(currDateRange.period[key]) / 1000);
  };
  const startTimestamp = getTimeEpoch("since");
  const endTimestamp = getTimeEpoch("until");

  const [loading, setLoading] = useState(true);
  const collectionsMap = PersistStore((state) => state.collectionsMap);
  const threatFiltersMap = SessionStore((state) => state.threatFiltersMap);
  const [currentTab, setCurrentTab] = useState('active');
  const [selected, setSelected] = useState(0)
  const [currentFilters, setCurrentFilters] = useState({})
  const [totalFilteredCount, setTotalFilteredCount] = useState(0)

  const tableTabs = [
    {
      content: 'Active',
      onAction: () => { setCurrentTab('active') },
      id: 'active',
      index: 0
    },
    {
      content: 'Under Review',
      onAction: () => { setCurrentTab('under_review') },
      id: 'under_review',
      index: 1
    },
    {
      content: 'Ignored',
      onAction: () => { setCurrentTab('ignored') },
      id: 'ignored',
      index: 2
    }
  ]

  const handleSelectedTab = (selectedIndex) => {
    setLoading(true)
    setSelected(selectedIndex)
    setTimeout(()=>{
        setLoading(false)
    },200)
  }

  const handleBulkIgnore = async (selectedIds) => {
    console.log("Selected IDs for ignore:", selectedIds);

    if (!selectedIds || selectedIds.length === 0) {
      console.error("No IDs selected for ignore");
      func.setToast(true, true, 'No events selected');
      return;
    }

    const validIds = selectedIds.filter(id => id != null && id !== '');
    console.log("Valid IDs for ignore:", validIds);

    if (validIds.length === 0) {
      console.error("No valid IDs found");
      func.setToast(true, true, 'No valid events selected');
      return;
    }

    try {
      const response = await threatDetectionRequests.bulkUpdateMaliciousEventStatus(validIds, 'IGNORED');
      console.log("Ignore response:", response);
      if (response?.updateSuccess) {
        func.setToast(true, false, `${response.updatedCount || validIds.length} event${validIds.length === 1 ? '' : 's'} ignored successfully`);
        if (triggerRefresh) {
          triggerRefresh();
        }
      } else {
        console.error("Failed to ignore events, response:", response);
        func.setToast(true, true, `Failed to ignore events: ${response?.updateMessage || 'Unknown error'}`);
      }
    } catch (error) {
      console.error("Error ignoring events - full error:", error);
      func.setToast(true, true, 'Error ignoring events');
    }
  };

  const handleBulkDelete = async (selectedIds) => {
    console.log("Selected IDs for deletion:", selectedIds);

    if (!selectedIds || selectedIds.length === 0) {
      console.error("No IDs selected for deletion");
      func.setToast(true, true, 'No events selected');
      return;
    }

    const validIds = selectedIds.filter(id => id != null && id !== '');
    console.log("Valid IDs for deletion:", validIds);

    if (validIds.length === 0) {
      console.error("No valid IDs found");
      func.setToast(true, true, 'No valid events selected');
      return;
    }

    try {
      const response = await threatDetectionRequests.bulkDeleteMaliciousEvents(validIds);
      console.log("Delete response:", response);
      if (response?.deleteSuccess) {
        func.setToast(true, false, `${response.deletedCount || validIds.length} event${validIds.length === 1 ? '' : 's'} deleted successfully`);
        if (triggerRefresh) {
          triggerRefresh();
        }
      } else {
        console.error("Failed to delete events, response:", response);
        func.setToast(true, true, `Failed to delete events: ${response?.deleteMessage || 'Unknown error'}`);
      }
    } catch (error) {
      console.error("Error deleting events - full error:", error);
      func.setToast(true, true, 'Error deleting events');
    }
  };

  const handleBulkMarkForReview = async (selectedIds) => {
    console.log("Selected IDs for review:", selectedIds);
    // selectedIds are already the IDs, no need to map
    
    // Check if selectedIds is empty or has invalid values
    if (!selectedIds || selectedIds.length === 0) {
      console.error("No IDs selected for review");
      func.setToast(true, true, 'No events selected');
      return;
    }
    
    // Filter out any null/undefined IDs
    const validIds = selectedIds.filter(id => id != null && id !== '');
    console.log("Valid IDs for review:", validIds);
    
    if (validIds.length === 0) {
      console.error("No valid IDs found");
      func.setToast(true, true, 'No valid events selected');
      return;
    }
    
    try {
      const response = await threatDetectionRequests.bulkUpdateMaliciousEventStatus(validIds, 'UNDER_REVIEW');
      console.log("Review response:", response);
      if (response?.updateSuccess) {
        func.setToast(true, false, `${response.updatedCount || validIds.length} event${validIds.length === 1 ? '' : 's'} marked for review successfully`);
        // Trigger table refresh if callback provided
        if (triggerRefresh) {
          triggerRefresh();
        }
      } else {
        console.error("Failed to mark events for review, response:", response);
        func.setToast(true, true, `Failed to mark events for review: ${response?.updateMessage || 'Unknown error'}`);
      }
    } catch (error) {
      console.error("Error marking events for review - full error:", error);
      console.error("Error response:", error.response);
      func.setToast(true, true, 'Error marking events for review');
    }
  };

  const handleBulkRemoveFromReview = async (selectedIds) => {
    console.log("Selected IDs for removing from review:", selectedIds);
    // selectedIds are already the IDs, no need to map
    try {
      const response = await threatDetectionRequests.bulkUpdateMaliciousEventStatus(selectedIds, 'ACTIVE');
      if (response?.updateSuccess) {
        func.setToast(true, false, `${response.updatedCount || selectedIds.length} event${selectedIds.length === 1 ? '' : 's'} removed from review successfully`);
        // Trigger table refresh if callback provided
        if (triggerRefresh) {
          triggerRefresh();
        }
      } else {
        func.setToast(true, true, 'Failed to remove events from review');
      }
    } catch (error) {
      console.error("Error removing events from review:", error);
      func.setToast(true, true, 'Error removing events from review');
    }
  };

  const handleIgnoreAllFiltered = async () => {
    console.log("Ignoring all filtered events with filters:", currentFilters);
    try {
      const response = await threatDetectionRequests.bulkUpdateFilteredEvents(
        currentFilters.actor || [],
        currentFilters.url || [],
        currentFilters.type || [],
        currentFilters.latestAttack || [],
        startTimestamp,
        endTimestamp,
        currentTab.toUpperCase(),
        'IGNORED'
      );
      if (response?.updateSuccess) {
        func.setToast(true, false, `${response.updatedCount || 0} events ignored successfully`);
        if (triggerRefresh) {
          triggerRefresh();
        }
      } else {
        func.setToast(true, true, 'Failed to ignore filtered events');
      }
    } catch (error) {
      console.error("Error ignoring filtered events:", error);
      func.setToast(true, true, 'Error ignoring filtered events');
    }
  };

  const handleDeleteAllFiltered = async () => {
    console.log("Deleting all filtered events with filters:", currentFilters);
    try {
      const response = await threatDetectionRequests.bulkDeleteFilteredEvents(
        currentFilters.actor || [],
        currentFilters.url || [],
        currentFilters.type || [],
        currentFilters.latestAttack || [],
        startTimestamp,
        endTimestamp,
        currentTab.toUpperCase()
      );
      if (response?.deleteSuccess) {
        func.setToast(true, false, `${response.deletedCount || 0} events deleted successfully`);
        if (triggerRefresh) {
          triggerRefresh();
        }
      } else {
        func.setToast(true, true, 'Failed to delete filtered events');
      }
    } catch (error) {
      console.error("Error deleting filtered events:", error);
      func.setToast(true, true, 'Error deleting filtered events');
    }
  };

  const handleMarkAllFilteredForReview = async () => {
    console.log("Marking all filtered events for review with filters:", currentFilters);
    try {
      const response = await threatDetectionRequests.bulkUpdateFilteredEvents(
        currentFilters.actor || [],
        currentFilters.url || [],
        currentFilters.type || [],
        currentFilters.latestAttack || [],
        startTimestamp,
        endTimestamp,
        currentTab.toUpperCase(),
        'UNDER_REVIEW'
      );
      if (response?.updateSuccess) {
        func.setToast(true, false, `${response.updatedCount || 0} events marked for review successfully`);
        if (triggerRefresh) {
          triggerRefresh();
        }
      } else {
        func.setToast(true, true, 'Failed to mark filtered events for review');
      }
    } catch (error) {
      console.error("Error marking filtered events for review:", error);
      func.setToast(true, true, 'Error marking filtered events for review');
    }
  };

  const handleRemoveAllFilteredFromReview = async () => {
    console.log("Removing all filtered events from review with filters:", currentFilters);
    try {
      const response = await threatDetectionRequests.bulkUpdateFilteredEvents(
        currentFilters.actor || [],
        currentFilters.url || [],
        currentFilters.type || [],
        currentFilters.latestAttack || [],
        startTimestamp,
        endTimestamp,
        currentTab.toUpperCase(),
        'ACTIVE'
      );
      if (response?.updateSuccess) {
        func.setToast(true, false, `${response.updatedCount || 0} events removed from review successfully`);
        if (triggerRefresh) {
          triggerRefresh();
        }
      } else {
        func.setToast(true, true, 'Failed to remove filtered events from review');
      }
    } catch (error) {
      console.error("Error removing filtered events from review:", error);
      func.setToast(true, true, 'Error removing filtered events from review');
    }
  };

  const promotedBulkActions = (selectedIds) => {
    console.log("promotedBulkActions called with IDs:", selectedIds);
    const actions = [];

    // Determine the count to display
    let eventCount = 0;
    let eventText = '';
    let useFilterBasedUpdate = false;

    // Check if "All" is selected - when GithubServerTable passes 'All' as selectedIds
    if (selectedIds === 'All') {
      // When "select all" is clicked, use the total count from API
      eventCount = totalFilteredCount;
      eventText = `ALL ${eventCount} event${eventCount === 1 ? '' : 's'}`;
      useFilterBasedUpdate = true;
    } else if (Array.isArray(selectedIds) && selectedIds.length > 0) {
      // When specific items are selected
      eventCount = selectedIds.length;
      eventText = `${eventCount} selected event${eventCount === 1 ? '' : 's'}`;
      useFilterBasedUpdate = false;
    }

    // Action buttons based on current tab
    if (eventCount > 0) {
      if (currentTab === 'active') {
        // Mark for Review button
        actions.push({
          content: `Mark for Review ${eventText}`,
          onAction: () => {
            const confirmationMessage = `Are you sure you want to mark ${eventText} for review?`;
            if (useFilterBasedUpdate) {
              func.showConfirmationModal(confirmationMessage, "Mark for Review", () => handleMarkAllFilteredForReview());
            } else {
              func.showConfirmationModal(confirmationMessage, "Mark for Review", () => handleBulkMarkForReview(selectedIds));
            }
          },
        });
        // Ignore button
        actions.push({
          content: `Ignore ${eventText}`,
          onAction: () => {
            const confirmationMessage = `Are you sure you want to ignore ${eventText}?`;
            if (useFilterBasedUpdate) {
              func.showConfirmationModal(confirmationMessage, "Ignore", () => handleIgnoreAllFiltered());
            } else {
              func.showConfirmationModal(confirmationMessage, "Ignore", () => handleBulkIgnore(selectedIds));
            }
          },
        });
      } else if (currentTab === 'under_review') {
        // Remove from Review button
        actions.push({
          content: `Remove from Review ${eventText}`,
          onAction: () => {
            const confirmationMessage = `Are you sure you want to remove ${eventText} from review?`;
            if (useFilterBasedUpdate) {
              func.showConfirmationModal(confirmationMessage, "Remove from Review", () => handleRemoveAllFilteredFromReview());
            } else {
              func.showConfirmationModal(confirmationMessage, "Remove from Review", () => handleBulkRemoveFromReview(selectedIds));
            }
          },
        });
        // Ignore button
        actions.push({
          content: `Ignore ${eventText}`,
          onAction: () => {
            const confirmationMessage = `Are you sure you want to ignore ${eventText}?`;
            if (useFilterBasedUpdate) {
              func.showConfirmationModal(confirmationMessage, "Ignore", () => handleIgnoreAllFiltered());
            } else {
              func.showConfirmationModal(confirmationMessage, "Ignore", () => handleBulkIgnore(selectedIds));
            }
          },
        });
      } else if (currentTab === 'ignored') {
        // Unignore (reactivate) button
        actions.push({
          content: `Reactivate ${eventText}`,
          onAction: () => {
            const confirmationMessage = `Are you sure you want to reactivate ${eventText}?`;
            if (useFilterBasedUpdate) {
              func.showConfirmationModal(confirmationMessage, "Reactivate", () => handleRemoveAllFilteredFromReview());
            } else {
              func.showConfirmationModal(confirmationMessage, "Reactivate", () => handleBulkRemoveFromReview(selectedIds));
            }
          },
        });
      }

      // Delete button for all tabs
      actions.push({
        content: `Delete ${eventText}`,
        onAction: () => {
          const confirmationMessage = `Are you sure you want to permanently delete ${eventText}? This action cannot be undone.`;
          if (useFilterBasedUpdate) {
            func.showConfirmationModal(confirmationMessage, "Delete", () => handleDeleteAllFiltered());
          } else {
            func.showConfirmationModal(confirmationMessage, "Delete", () => handleBulkDelete(selectedIds));
          }
        },
      });
    }

    return actions;
  };



  async function fetchData(
    sortKey,
    sortOrder,
    skip,
    limit,
    filters,
    filterOperators,
    queryValue
  ) {
    setLoading(true);
    let sourceIpsFilter = [],
      apiCollectionIdsFilter = [],
      matchingUrlFilter = [],
      typeFilter = [],
      latestAttack = [];
    if (filters?.actor) {
      sourceIpsFilter = filters?.actor;
    }
    if (filters?.apiCollectionId) {
      apiCollectionIdsFilter = filters?.apiCollectionId;
    }
    if (filters?.url) {
      matchingUrlFilter = filters?.url;
    }
    if(filters?.type){
      typeFilter = filters?.type
    }
    if(filters?.latestAttack){
      latestAttack = filters?.latestAttack
    }
    
    // Store current filters for bulk operations
    setCurrentFilters({
      actor: sourceIpsFilter,
      apiCollectionId: apiCollectionIdsFilter,
      url: matchingUrlFilter,
      type: typeFilter,
      latestAttack: latestAttack,
      sortKey: sortKey,
      sortOrder: sortOrder
    });
    
    const sort = { [sortKey]: sortOrder };
    const res = await api.fetchSuspectSampleData(
      skip,
      sourceIpsFilter,
      apiCollectionIdsFilter,
      matchingUrlFilter,
      typeFilter,
      sort,
      startTimestamp,
      endTimestamp,
      latestAttack,
      50,
      currentTab.toUpperCase(),
    );

    // Store the total count for filtered results
    setTotalFilteredCount(res.total || 0);
//    setSubCategoryChoices(distinctSubCategories);
    let total = res.total;
    let ret = res?.maliciousEvents.map((x) => {
      const severity = threatFiltersMap[x?.filterId]?.severity || "HIGH"
      return {
        ...x,
        id: x.id,
        actorComp: formatActorId(x.actor),
        endpointComp: (
          <GetPrettifyEndpoint 
            maxWidth="300px" 
            method={x.method}
            url={x.url} 
            isNew={false} 
          />
        ),
        apiCollectionName: collectionsMap[x.apiCollectionId] || "-",
        discoveredTs: dayjs(x.timestamp*1000).format("DD-MM-YYYY HH:mm:ss"),
        sourceIPComponent: x?.ip || "-",
        type: x?.type || "-",
        severityComp: (<div className={`badge-wrapper-${severity}`}>
                          <Badge size="small">{func.toSentenceCase(severity)}</Badge>
                      </div>
        )
      };
    });
    setLoading(false);
    return { value: ret, total: total };
  }

  const attackTypeChoices = Object.keys(threatFiltersMap).length === 0 ? [] : Object.entries(threatFiltersMap).map(([key, value]) => {
    return {
      label: value?._id || key,
      value: value?._id || key
    }
  })
  

  async function fillFilters() {
    const res = await api.fetchFiltersThreatTable();
    let urlChoices = res?.urls
      .map((x) => {
        const url = x || "/"
        return { label: url, value: x };
      });
    let ipChoices = res?.ips.map((x) => {
      return { label: x, value: x };
    });


    filters = [
      {
        key: "actor",
        label: "Actor",
        title: "Actor",
        choices: ipChoices,
      },
      {
        key: "url",
        label: "URL",
        title: "URL",
        choices: urlChoices,
      },
      {
        key: 'type',
        label: "Type",
        title: "Type",
        choices: [
          {label: 'Rule based', value: 'Rule-Based'},
          {label: 'Anomaly', value: 'Anomaly'},
        ],
      },
      {
        key: 'latestAttack',
        label: 'Latest attack sub-category',
        type: 'select',
        choices: attackTypeChoices,
        multiple: true
      },
    ];
  }

  useEffect(() => {
    fillFilters();
  }, []);

  function disambiguateLabel(key, value) {
    switch (key) {
      case "apiCollectionId":
        return func.convertToDisambiguateLabelObj(value, collectionsMap, 2);
      default:
        return func.convertToDisambiguateLabelObj(value, null, 2);
    }
  }

  const key = startTimestamp + endTimestamp;
  return (
    <GithubServerTable
      key={key}
      onRowClick={(data) => rowClicked(data)}
      pageLimit={50}
      headers={headers}
      resourceName={resourceName}
      sortOptions={sortOptions}
      disambiguateLabel={disambiguateLabel}
      loading={loading}
      fetchData={fetchData}
      filters={filters}
      selectable={true}
      promotedBulkActions={promotedBulkActions}
      headings={headers}
      useNewRow={true}
      condensedHeight={true}
      tableTabs={tableTabs}
      selected={selected}
      onSelect={handleSelectedTab}
      mode={IndexFiltersMode.Default}
      hideQueryField={true}
    />
  );
}

export default SusDataTable;
