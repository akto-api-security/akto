import {
    TextField,
    IndexTable,
    LegacyCard,
    IndexFilters,
    useSetIndexFiltersMode,
    useIndexResourceState,
    Text,
    ChoiceList,
    RangeSlider,
    useBreakpoints,
    Badge
  } from '@shopify/polaris';
import dayjs from 'dayjs';
import func from '@/util/func';
import {useState, useCallback} from 'react';
import GetPrettifyEndpoint from '../../observe/GetPrettifyEndpoint';
import PersistStore from "../../../../main/PersistStore";
  
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
        setData(activityLog.filter(log => log.severity.toLowerCase() === item.toLowerCase()));
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
      singular: 'activity',
      plural: 'activities',
    };

    const handleRowClick = (url) => {
        console.log('clicked', url);
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
        {detectedAt, subCategory, url, severity},
        index,
      ) => (
        <IndexTable.Row
          onClick={() => handleRowClick(url)}
          id={detectedAt}
          key={detectedAt}
          position={index}
        >
          <IndexTable.Cell>
            <Text variant="bodyMd" fontWeight="bold" as="span">
              {dayjs(detectedAt*1000).format('hh:mm A')}
            </Text>
          </IndexTable.Cell>
          <IndexTable.Cell>
            <Text variant="bodyMd" fontWeight="medium" as="span">
              {subCategory}
            </Text>
          </IndexTable.Cell>
          <IndexTable.Cell>
            <div key={severity} className={`badge-wrapper-${severity.toUpperCase()}`}>
              <Badge status={func.getHexColorForSeverity(severity)}>{func.toSentenceCase(severity)}</Badge>
            </div>
          </IndexTable.Cell>
          <IndexTable.Cell>
            <GetPrettifyEndpoint
                maxWidth={'200px'}
                method={'GET'}
                url={url}
                isNew={false}
            />
          </IndexTable.Cell>
        </IndexTable.Row>
      ),
    );
  
    return (
      <LegacyCard>
        <IndexFilters
          tabs={tabs}
          mode={mode}
          setMode={setMode}
          selected={selected}
        />
        <IndexTable
            condensed={useBreakpoints().smDown}
            resourceName={resourceName}
            itemCount={data.length}
            headings={[
                {title: 'Time'},
                {title: 'Attack type'},
                {title: 'Severity'},
                {title: 'Api Endpoint'},
            ]}
        >
          {rowMarkup}
        </IndexTable>
      </LegacyCard>
    );
  }