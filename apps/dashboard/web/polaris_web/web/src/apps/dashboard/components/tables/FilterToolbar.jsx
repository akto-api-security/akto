import { IndexFilters, IndexFiltersMode, useSetIndexFiltersMode, ChoiceList, LegacyCard } from "@shopify/polaris";
import { useMemo, useState, useEffect, useCallback } from "react";
import DropdownSearch from "../shared/DropdownSearch";
import tableFunc from './transform';
import PersistStore from '../../../main/PersistStore';
import { useSearchParams } from 'react-router-dom';
import func from '../../../../util/func';


export const FilterToolbar = ({
    loading,
    filters,
    disambiguateLabel,
    appliedFilters: initialAppliedFilters,
    filterStateUrl,
    sortOptions: initialSortOptions,
    onFilterChange
}) => {
    const [searchParams, setSearchParams] = useSearchParams();
    const updateQueryParams = (key, value) => {
        const newSearchParams = new URLSearchParams(searchParams);
        newSearchParams.set(key, value);
        setSearchParams(newSearchParams);
    };
    const setFiltersMap = PersistStore(state => state.setFiltersMap)
    const filtersMap = PersistStore(state => state.filtersMap)
    const currentPageKey = filterStateUrl || (window.location.pathname + "/" +  window.location.hash)
    const pageFiltersMap = filtersMap[currentPageKey];
    const [sortSelected, setSortSelected] = useState(tableFunc.getInitialSortSelected(initialSortOptions, pageFiltersMap))

    const { mode, setMode } = useSetIndexFiltersMode(IndexFiltersMode.Filtering);
    const initialStateFilters = tableFunc.mergeFilters(initialAppliedFilters || [], (pageFiltersMap?.filters || []), disambiguateLabel, handleRemoveAppliedFilter)
    const [appliedFilters, setAppliedFilters] = useState(initialStateFilters);

    useEffect(()=> {
        let queryFilters 
        if (performance.getEntriesByType('navigation')[0].type === 'reload') {
            queryFilters = []
        }else{
            queryFilters = tableFunc.getFiltersMapFromUrl(decodeURIComponent(searchParams.get("filters") || ""), disambiguateLabel, handleRemoveAppliedFilter, currentPageKey)
        }
        const currentFilters = tableFunc.mergeFilters(queryFilters,initialStateFilters,disambiguateLabel, handleRemoveAppliedFilter)
        setAppliedFilters((prev) => {
            if(func.deepComparison(prev, currentFilters)){
            return prev
            }else{
            return currentFilters;
            }
        })
    },[currentPageKey])

    useEffect(() => {
        updateQueryParams("filters",tableFunc.getPrettifiedFilter(appliedFilters))
    },[appliedFilters])

    function handleRemoveAppliedFilter(key) {
        console.log("handleRemoveAppliedFilter", key);
        let temp = appliedFilters
        temp = temp.filter((filter) => {
            return filter.key != key
        })
        console.log("temp", temp);
        // initialAppliedFilters?.forEach((defaultAppliedFilter) => {
        //     if (key === defaultAppliedFilter.key) {
        //         temp.push({
        //             ...defaultAppliedFilter,
        //             onRemove: handleRemoveAppliedFilter
        //         })
        //     }
        // })
        setAppliedFilters(temp);
        let tempFilters = filtersMap
        tempFilters[currentPageKey] = {
            'filters': temp,
            'sort': pageFiltersMap?.sort || []
        }
        setFiltersMap(tempFilters)
        onFilterChange(temp);
    }

    const getSortedChoices = (choices) => {
        return choices.sort((a, b) => (a?.label || a) - (b?.label || b));
    }

    const formattedFilters = useMemo(() => {
        return formatFilters(filters);
    }, [filters, appliedFilters]);

    const changeAppliedFilters = useCallback((key, value) => {
        let temp = appliedFilters.filter(filter => filter.key !== key);
        if (value.length > 0 || Object.keys(value).length > 0) {
            temp.push({
            key: key,
            label: disambiguateLabel(key, value),
            onRemove: handleRemoveAppliedFilter,
            value: value
            });
        }
        let tempFilters = filtersMap
        tempFilters[currentPageKey]= {
            filters: temp,
            sort: pageFiltersMap?.sort || []
        }
        setFiltersMap(tempFilters);
        setAppliedFilters(temp);
        onFilterChange(temp);
    }, [appliedFilters, disambiguateLabel, handleRemoveAppliedFilter, setFiltersMap, currentPageKey, pageFiltersMap]);
    

    const handleFilterStatusChange = (key,value) => {
        changeAppliedFilters(key, value);
    }

    const handleFiltersClearAll = useCallback(() => {
        setFiltersMap({
          ...filtersMap,
          [currentPageKey]:{
            sort: filtersMap[currentPageKey]?.sort || []
          }
        })
        setAppliedFilters([])
        onFilterChange([])
      }, []);

      useEffect(() => {
        setAppliedFilters(initialAppliedFilters.map(filter => ({
            ...filter,
            onRemove: handleRemoveAppliedFilter
        })));
     }, [initialAppliedFilters]);

    function formatFilters(filters) {
        return filters 
          .map((filter) => {
            return {
              key: filter.key,
              label: filter.label,
              filter: (
                  filter.choices.length < 10 ?       
                  <ChoiceList
                    title={filter.title}
                    titleHidden
                    choices={getSortedChoices(filter.choices)}
                    selected={
                      appliedFilters.filter((localFilter) => { return localFilter.key == filter.key }).length == 1 ?
                        appliedFilters.filter((localFilter) => { return localFilter.key == filter.key })[0].value : filter.selected || []
                    }
                    onChange={(value) => handleFilterStatusChange(filter.key,value)}
                    {...(filter.singleSelect ? {} : { allowMultiple: true })}
                  />
                  :
                  <DropdownSearch 
                    placeHoder={"Apply filters"} 
                    optionsList={getSortedChoices(filter.choices)}
                    setSelected={(value) => handleFilterStatusChange(filter.key,value)}
                    {...(filter.singleSelect ? {} : { allowMultiple: true })}
                    allowMultiple
                    preSelected={
                      appliedFilters.filter((localFilter) => { return localFilter.key == filter.key }).length == 1 ?
                        appliedFilters.filter((localFilter) => { return localFilter.key == filter.key })[0].value : filter.selected || []
                    }
                  />
              ),
              // shortcut: true,
              pinned: true
            }
          })
      }

    return (
        <LegacyCard>
            <LegacyCard.Section flush>
            <IndexFilters
                queryValue=""
                hideQueryField={true}
                filters={formattedFilters}
                appliedFilters={appliedFilters}
                onClearAll={handleFiltersClearAll}
                tabs={[]}
                sortOptions={initialSortOptions}
                sortSelected={sortSelected}
                loading={loading || false}
                mode={mode}
                canCreateNewView={false}
                setMode={setMode}
            />
            </LegacyCard.Section>
        </LegacyCard>
    )
}