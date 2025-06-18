import React from 'react';
import DropdownSearch from './DropdownSearch';

const SEVERITY_OPTIONS = [
  { label: "Critical", value: "CRITICAL" },
  { label: "High", value: "HIGH" },
  { label: "Medium", value: "MEDIUM" },
  { label: "Low", value: "LOW" }
];

function SeverityLevelDropdown({ severityLevels, setSeverityLevels }) {
  return (
    <DropdownSearch
      label="Block severity levels"
      placeholder="Select severity levels"
      optionsList={SEVERITY_OPTIONS}
      setSelected={setSeverityLevels}
      preSelected={severityLevels}
      itemName={"severity level"}
      value={`${severityLevels.length} severity level${severityLevels.length === 1 ? '' : 's'} selected`}
      allowMultiple
    />
  );
}

export default SeverityLevelDropdown;