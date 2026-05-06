import React, { useEffect, useState } from 'react';
import DropdownSearch from './DropdownSearch';
import settingRequests from '../../pages/settings/api';

function ThreatPoliciesDropdown({ threatPolicies, setThreatPolicies }) {
  const [threatPoliciesOptions, setThreatPoliciesOptions] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchThreatPolicies();
  }, []);

  const fetchThreatPolicies = async () => {
    try {
      setLoading(true);
      const response = await settingRequests.fetchFilterYamlTemplate();

      if (response && response.templates) {
        const excludedCategories = ['SuccessfulExploit', 'IgnoredEvent'];
        const options = response.templates
          .filter(template => !excludedCategories.includes(template.info?.category?.name))
          .map(template => ({
            label: template.id,
            value: template.id
          }));
        setThreatPoliciesOptions(options);
      }
    } catch (error) {
      console.error('Error fetching threat policies:', error);
      setThreatPoliciesOptions([]);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <DropdownSearch
      label="Block threat policies"
      placeholder="Loading threat policies..."
      optionsList={[]}
      setSelected={setThreatPolicies}
      preSelected={threatPolicies}
      itemName={"threat policy"}
      value={`${threatPolicies.length} threat polic${threatPolicies.length === 1 ? 'y' : 'ies'} selected`}
      allowMultiple
      disabled
    />;
  }

  return (
    <DropdownSearch
      label="Block threat policies"
      placeholder="Select threat policies"
      optionsList={threatPoliciesOptions}
      setSelected={setThreatPolicies}
      preSelected={threatPolicies}
      itemName={"threat policy"}
      value={`${threatPolicies.length} threat polic${threatPolicies.length === 1 ? 'y' : 'ies'} selected`}
      allowMultiple
    />
  );
}

export default ThreatPoliciesDropdown;
