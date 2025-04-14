import { useEffect, useState } from "react";
import api from "../api";
import { FilterToolbar } from "../../../components/tables/FilterToolbar";
import func from "../../../../../util/func";

let filters = [];

function ThreatActorFilters({ onFilterChange, appliedFilters }) {
    async function fillFilters() {
        const res = await api.fetchThreatActorFilters();
        const attackTypeChoices = res?.latestAttack.map(x => ({
          label: x,
          value: x
        }));
        const countryChoices = res?.country.map(x => ({
          label: x,
          value: x
        }));
        const actorIdChoices = res?.actorId.map(x => ({
          label: x,
          value: x
        }));
        filters = [
          {
            key: 'actorId',
            label: 'Actor Id',
            type: 'select',
            choices: actorIdChoices,
            multiple: true
          },
          {
            key: 'latestAttack',
            label: 'Latest attack sub-category',
            type: 'select',
            choices: attackTypeChoices,
            multiple: true
          },
          {
            key: 'country',
            label: 'Country',
            type: 'select',
            choices: countryChoices,
            multiple: true
          }
        ]
    }
    
    useEffect(() => {
        fillFilters();
    }, []);

    function disambiguateLabel(key, value) {
      return func.convertToDisambiguateLabelObj(value, null, 2);
    }

    const sortOptions = [
      {
        label: "Discovered time",
        value: "discoveredAt asc",
        directionLabel: "Newest",
        sortKey: "discoveredAt",
        columnIndex: 4,
      },
      {
        label: "Discovered time",
        value: "discoveredAt desc",
        directionLabel: "Oldest",
        sortKey: "discoveredAt",
        columnIndex: 4,
      },
    ];

    return (
        <FilterToolbar
          onFilterChange={onFilterChange}
          sortOptions={sortOptions}
          filters={filters}
          disambiguateLabel={disambiguateLabel}
          appliedFilters={appliedFilters}
        />
    )
}

export default ThreatActorFilters;