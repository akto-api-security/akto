import {
    IndexTable,
    LegacyCard,
    IndexFilters,
    useSetIndexFiltersMode,
    Text,
    useBreakpoints,
    Badge,
    Box
  } from '@shopify/polaris';
import dayjs from 'dayjs';
import func from '@/util/func';
import {useState} from 'react';
import GetPrettifyEndpoint from '../../observe/GetPrettifyEndpoint';
import PersistStore from "../../../../main/PersistStore";
import { labelMap } from '../../../../main/labelHelperMap';
  
 export const ActivityLog = ({ activityLog, actorDetails }) => {
    const [itemStrings, setItemStrings] = useState([
      'All',
      'Critical',
      'High',
      'Medium',
      'Low',
    ]);

    const [data, setData] = useState(activityLog);
    const [selected, setSelected] = useState(0);

    const handleTabItemClick = (item, index) => {
      if (item === 'All') {
        setSelected(0);
        setData(activityLog);
      } else {
        setSelected(index);
        const filteredData = activityLog.filter(log => log.severity.toLowerCase() === item.toLowerCase());
        setData(filteredData);
      } 
    }

  
    const tabs = itemStrings.map((item, index) => ({
      content: item,
      badge: activityLog.filter(log => log.severity.toLowerCase() === item.toLowerCase()).length,
      index,
      onAction: () => { handleTabItemClick(item, index)},
      id: `${item}-${index}`,
    }));

    const {mode, setMode} = useSetIndexFiltersMode();
  
    const resourceName = {
      singular: 'activitysdc',
      plural: 'activities',
    };

    const handleRowClick = (url) => {
        const actorIp = actorDetails.latestApiIp;
        const tempKey = `/dashboard/protection/threat-activity/`
        let filtersMap = PersistStore.getState().filtersMap;
        if(filtersMap !== null && filtersMap.hasOwnProperty(tempKey)){
        delete filtersMap[tempKey];
        PersistStore.getState().setFiltersMap(filtersMap);
        }


        const filters = `actor__${actorIp}&url__${url}`;
        const navigateUrl = `${window.location.origin}/dashboard/protection/threat-activity?filters=${encodeURIComponent(filters)}`;
        window.open(navigateUrl, "_blank");
    }
  
    const rowMarkup = data.map(
      (
        {detectedAt, subCategory, url, severity, method, host},
        index,
      ) => (
        <IndexTable.Row
          onClick={() => handleRowClick(url)}
          id={`${detectedAt}-${index}`}
          key={`${detectedAt}-${url}-${index}-${severity}`}
          position={index}
        >
          <IndexTable.Cell>
            <Text variant="bodyMd" fontWeight="bold" as="span">
              {detectedAt ? dayjs(detectedAt*1000).format('hh:mm A') : "-"}
            </Text>
          </IndexTable.Cell>
          <IndexTable.Cell>
            <Text variant="bodyMd" fontWeight="medium" as="span">
              {subCategory || "-"}
            </Text>
          </IndexTable.Cell>
          <IndexTable.Cell>
            {severity ? (<div key={severity} className={`badge-wrapper-${severity.toUpperCase()}`}>
              <Badge status={func.getHexColorForSeverity(severity)}>{func.toSentenceCase(severity)}</Badge>
            </div>) : "-"}
          </IndexTable.Cell>
          <IndexTable.Cell>
            <Text variant="bodyMd" fontWeight="medium" as="span">
              {host || "-"}
            </Text>
          </IndexTable.Cell>
          <IndexTable.Cell>
            <GetPrettifyEndpoint
                maxWidth={'200px'}
                method={method || "GET"}
                url={url}
                isNew={false}
            />
          </IndexTable.Cell>
        </IndexTable.Row>
      ),
    );
  
    return (
      <> 
      <style>
        {`
          #activity-log-card .Polaris-IndexTable__TableHeading--first,
          #activity-log-card .Polaris-IndexTable__TableCell--first {
            display: none;
          }
          #activity-log-card .Polaris-IndexTable__TableHeading,
          #activity-log-card .Polaris-IndexTable__TableCell {
            padding-left: 16px;
          }
        `}
      </style>
      <Box id="activity-log-card">
        <LegacyCard className="activity-log-card">
          <IndexFilters
            tabs={tabs}
            mode={mode}
            setMode={setMode}
            selected={selected}
            hideFilters
            hideQueryField
            canCreateNewView={false}
          />
          <IndexTable
              condensed={useBreakpoints().smDown}
              resourceName={resourceName}
              itemCount={data.length}
              headings={[
                  {title: 'Time'},
                  {title: 'Attack type'},
                  {title: 'Severity'},
                  {title: 'Host'},
                  {title: labelMap[PersistStore.getState().dashboardCategory]["API endpoint"]},
              ]}
          >
            {rowMarkup}
          </IndexTable>
        </LegacyCard>
      </Box>
      </>
    );
  }