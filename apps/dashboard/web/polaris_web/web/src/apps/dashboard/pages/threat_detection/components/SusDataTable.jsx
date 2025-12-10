import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
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
import threatDetectionRequests from "../api";
import { LABELS } from "../constants";

const resourceName = {
  singular: "activity",
  plural: "activities",
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
    text: "Host",
    value: "host",
    title: "Host",
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
    text: "successfulExploit",
    value: "successfulComp",
    title: "Successful Exploit",
    maxWidth: "90px",
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

function SusDataTable({ currDateRange, rowClicked, triggerRefresh, label = LABELS.THREAT }) {
  const location = useLocation();
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

  // Helper function to validate filter requirements for bulk operations
  const validateFiltersForBulkOperation = (operationType = 'operation') => {
    // Check if both URL and Attack Type filters are present
    if (!currentFilters.url || currentFilters.url.length === 0 ||
        !currentFilters.latestAttack || currentFilters.latestAttack.length === 0) {
      const message = operationType === 'ignore'
        ? 'Both URL and Attack Type filters are required to ignore events. This prevents accidentally blocking too many future events.'
        : 'Both URL and Attack Type filters are required for filter-based operations. This ensures precise targeting of events.';
      func.setToast(true, true, message);
      return false;
    }

    // Check if any other filters are applied (only URL and attack category are allowed)
    const hasOtherFilters = (currentFilters.actor && currentFilters.actor.length > 0) ||
                           (currentFilters.type && currentFilters.type.length > 0) ||
                           (currentFilters.apiCollectionId && currentFilters.apiCollectionId.length > 0)
    
    if (hasOtherFilters) {
      const message = 'Only URL and Attack Category filters are allowed for bulk operations. Please remove other filters (Actor, Type, Collection) and try again.';
      func.setToast(true, true, message);
      return false;
    }

    return true;
  }

  // Generic handler for bulk operations on selected IDs
  const handleBulkOperation = async (selectedIds, operation, newState = null) => {
    const actionLabels = {
      ignore: { ing: 'ignoring', ed: 'ignored' },
      delete: { ing: 'deleting', ed: 'deleted' },
      markForReview: { ing: 'marking for review', ed: 'marked for review' },
      removeFromReview: { ing: 'removing from review', ed: 'removed from review' }
    };

    const label = actionLabels[operation];

    if (!selectedIds || selectedIds.length === 0) {
      func.setToast(true, true, 'No events selected');
      return;
    }

    const validIds = selectedIds.filter(id => id != null && id !== '');

    if (validIds.length === 0) {
      func.setToast(true, true, 'No valid events selected');
      return;
    }

    try {
      let response;
      if (operation === 'delete') {
        response = await threatDetectionRequests.deleteMaliciousEvents({ eventIds: validIds });
      } else {
        response = await threatDetectionRequests.updateMaliciousEventStatus({ eventIds: validIds, status: newState });
      }

      const isSuccess = operation === 'delete' ? response?.deleteSuccess : response?.updateSuccess;
      const count = operation === 'delete' ? response?.deletedCount : response?.updatedCount;
      const errorMessage = operation === 'delete' ? response?.deleteMessage : response?.updateMessage;

      if (isSuccess) {
        func.setToast(true, false, `${count || validIds.length} event${validIds.length === 1 ? '' : 's'} ${label.ed} successfully`);
        if (triggerRefresh) {
          triggerRefresh();
        }
      } else {
        func.setToast(true, true, `Failed to ${operation} events: ${errorMessage || 'Unknown error'}`);
      }
    } catch (error) {
      func.setToast(true, true, `Error ${label.ing} events`);
    }
  }

  // Generic handler for filtered bulk operations
  const handleFilteredOperation = async (operation, newState = null) => {
    const actionLabels = {
      ignore: { ing: 'ignoring', ed: 'ignored' },
      delete: { ing: 'deleting', ed: 'deleted' },
      markForReview: { ing: 'marking for review', ed: 'marked for review' },
      removeFromReview: { ing: 'removing from review', ed: 'removed from review' }
    };

    const label = actionLabels[operation];

    // Validate filters
    const validationType = operation === 'ignore' ? 'ignore' : undefined;
    if (!validateFiltersForBulkOperation(validationType)) return;

    try {
      let response;
      const filterParams = [
        currentFilters.actor || [],
        currentFilters.url || [],
        currentFilters.type || [],
        currentFilters.latestAttack || [],
        startTimestamp,
        endTimestamp,
        currentTab.toUpperCase(),
        currentFilters.host || []
      ];

      if (operation === 'delete') {
        response = await threatDetectionRequests.deleteMaliciousEvents({
          actors: filterParams[0],
          urls: filterParams[1],
          types: filterParams[2],
          latestAttack: filterParams[3],
          startTimestamp: filterParams[4],
          endTimestamp: filterParams[5],
          statusFilter: filterParams[6],
          hosts: filterParams[7]
        });
      } else {
        response = await threatDetectionRequests.updateMaliciousEventStatus({
          actors: filterParams[0],
          urls: filterParams[1],
          types: filterParams[2],
          latestAttack: filterParams[3],
          startTimestamp: filterParams[4],
          endTimestamp: filterParams[5],
          statusFilter: filterParams[6],
          status: newState,
          hosts: filterParams[7]
        });
      }

      const isSuccess = operation === 'delete' ? response?.deleteSuccess : response?.updateSuccess;
      const count = operation === 'delete' ? response?.deletedCount : response?.updatedCount;

      if (isSuccess) {
        func.setToast(true, false, `${count || 0} events ${label.ed} successfully`);
        if (triggerRefresh) {
          triggerRefresh();
        }
      } else {
        func.setToast(true, true, `Failed to ${operation === 'delete' ? 'delete' : operation} filtered events`);
      }
    } catch (error) {
      func.setToast(true, true, `Error ${label.ing} filtered events`);
    }
  }

  // Simplified handler functions using the generic handlers
  const handleBulkIgnore = (selectedIds) => handleBulkOperation(selectedIds, 'ignore', 'IGNORED');
  const handleBulkDelete = (selectedIds) => handleBulkOperation(selectedIds, 'delete');
  const handleBulkMarkForReview = (selectedIds) => handleBulkOperation(selectedIds, 'markForReview', 'UNDER_REVIEW');
  const handleBulkRemoveFromReview = (selectedIds) => handleBulkOperation(selectedIds, 'removeFromReview', 'ACTIVE');

  // Simplified filtered operation handlers
  const handleIgnoreAllFiltered = () => handleFilteredOperation('ignore', 'IGNORED');
  const handleDeleteAllFiltered = () => handleFilteredOperation('delete');
  const handleMarkAllFilteredForReview = () => handleFilteredOperation('markForReview', 'UNDER_REVIEW');
  const handleRemoveAllFilteredFromReview = () => handleFilteredOperation('removeFromReview', 'ACTIVE');


  const promotedBulkActions = (selectedIds) => {
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

    if (eventCount === 0) return actions;

    // Helper function to create an action button
    const createAction = (label, actionType, validationType = null, includeWarning = false) => {
      const warningText = includeWarning
        ? '\n\nNote: Future events matching these URL and Attack Type combinations will be automatically blocked.'
        : '';

      return {
        content: `${label} ${eventText}`,
        onAction: () => {
          if (useFilterBasedUpdate) {
            if (!validateFiltersForBulkOperation(validationType)) return;
            const message = actionType === 'delete'
              ? `Are you sure you want to permanently delete ${eventText}? This action cannot be undone.`
              : `Are you sure you want to ${label.toLowerCase()} ${eventText}?${warningText}`;
            const handlers = {
              markForReview: handleMarkAllFilteredForReview,
              ignore: handleIgnoreAllFiltered,
              removeFromReview: handleRemoveAllFilteredFromReview,
              reactivate: handleRemoveAllFilteredFromReview,
              delete: handleDeleteAllFiltered
            };
            func.showConfirmationModal(message, label, handlers[actionType]);
          } else {
            const message = actionType === 'delete'
              ? `Are you sure you want to permanently delete ${eventText}? This action cannot be undone.`
              : includeWarning && actionType === 'ignore'
                ? `Are you sure you want to ${label.toLowerCase()} ${eventText}?`
                : `Are you sure you want to ${label.toLowerCase()} ${eventText}?`;
            const handlers = {
              markForReview: () => handleBulkMarkForReview(selectedIds),
              ignore: () => handleBulkIgnore(selectedIds),
              removeFromReview: () => handleBulkRemoveFromReview(selectedIds),
              reactivate: () => handleBulkRemoveFromReview(selectedIds),
              delete: () => handleBulkDelete(selectedIds)
            };
            func.showConfirmationModal(message, label, handlers[actionType]);
          }
        },
      };
    };

    // Define actions for each tab
    const tabActions = {
      'active': [
        { label: 'Mark for Review', type: 'markForReview' },
        { label: 'Ignore', type: 'ignore', validationType: 'ignore', warning: true }
      ],
      'under_review': [
        { label: 'Remove from Review', type: 'removeFromReview' },
        { label: 'Ignore', type: 'ignore', validationType: 'ignore', warning: true }
      ],
      'ignored': [
        { label: 'Reactivate', type: 'reactivate' }
      ]
    };

    // Add tab-specific actions
    const currentTabActions = tabActions[currentTab] || [];
    currentTabActions.forEach(({ label, type, validationType, warning }) => {
      actions.push(createAction(label, type, validationType, warning));
    });

    // Delete button for all tabs
    actions.push(createAction('Delete', 'delete'));

    return actions;
  };



  async function fetchData(
    sortKey,
    sortOrder,
    skip,
    _limit,
    filters,
    _filterOperators,
    queryValue
  ) {
    setLoading(true);
    let sourceIpsFilter = [],
      apiCollectionIdsFilter = [],
      matchingUrlFilter = [],
      typeFilter = [],
      latestAttack = [],
      hostFilter = [];
    let latestApiOrigRegex = queryValue.length > 3 ? queryValue : "";
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
    if(filters?.host){
      hostFilter = filters?.host
    }
    
    // Store current filters for bulk operations
    setCurrentFilters({
      actor: sourceIpsFilter,
      apiCollectionId: apiCollectionIdsFilter,
      url: matchingUrlFilter,
      type: typeFilter,
      latestAttack: latestAttack,
      host: hostFilter,
      sortKey: sortKey,
      sortOrder: sortOrder
    });
    
    const sort = { [sortKey]: sortOrder };
    const successfulFilterValue = Array.isArray(filters?.successfulExploit) ? filters?.successfulExploit?.[0] : filters?.successfulExploit;
    const successfulBool = (successfulFilterValue === true || successfulFilterValue === 'true') ? true
                          : (successfulFilterValue === false || successfulFilterValue === 'false') ? false
                          : undefined;
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
      successfulBool,
      label, // Use the label prop (THREAT or GUARDRAIL)
      hostFilter,
      latestApiOrigRegex
    );

    // Store the total count for filtered results
    setTotalFilteredCount(res.total || 0);
//    setSubCategoryChoices(distinctSubCategories);
    let total = res.total;
    let ret = res?.maliciousEvents.map((x) => {
      const severity = threatFiltersMap[x?.filterId]?.severity || "HIGH"
      
      // Build nextUrl for table navigation (similar to prepareTestRunResult)
      let nextUrl = null;
      if (x.refId && x.eventType && x.actor && x.filterId) {
        const params = new URLSearchParams();
        params.set("refId", x.refId);
        params.set("eventType", x.eventType);
        params.set("actor", x.actor);
        params.set("filterId", x.filterId);
        if (x.status) {
          params.set("eventStatus", x.status.toUpperCase());
        }
        nextUrl = `${location.pathname}?${params.toString()}`;
      }
      
      return {
        ...x,
        id: x.id,
        actorComp: formatActorId(x.actor),
        host: x.host || "-",
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
        successfulComp: (
          <Badge size="small">{x?.successfulExploit ? "True" : "False"}</Badge>
        ),
        severityComp: (<div className={`badge-wrapper-${severity}`}>
                          <Badge size="small">{func.toSentenceCase(severity)}</Badge>
                      </div>
        ),
        nextUrl: nextUrl
      };
    });
    setLoading(false);
    return { value: ret, total: total };
  }

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
    
    // Extract unique hosts from the fetched data
    let hostChoices = [];
    if (res?.hosts && Array.isArray(res.hosts) && res.hosts.length > 0) {
      hostChoices = res.hosts
        .filter(host => host && host.trim() !== '' && host !== '-')
        .map(x => ({ label: x, value: x }));
    }

    const attackTypeChoices = Object.keys(threatFiltersMap).length === 0 ? [] : Object.entries(threatFiltersMap).map(([key, value]) => {
      return {
        label: value?._id || key,
        value: value?._id || key
      }
    })

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
        key: 'host',
        label: "Host",
        title: "Host",
        choices: hostChoices,
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
      {
        key: 'successfulExploit',
        label: 'Successful Exploit',
        title: 'Successful Exploit',
        choices: [
          { label: 'True', value: 'true' },
          { label: 'False', value: 'false' }
        ],
        singleSelect: true
      },
    ];
  }

  useEffect(() => {
    fillFilters();
  }, [threatFiltersMap]);

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
    />
  );
}

export default SusDataTable;
