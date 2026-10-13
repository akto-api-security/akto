import React, { useEffect, useState } from 'react';
import { Text } from '@shopify/polaris';
import Dropdown from '../layouts/Dropdown';
import testingApi from '../../pages/testing/api';

function TestRoleDropdown({ selectedRole, setSelectedRole, label = "Test Role", helpText, selectFirstByDefault = false }) {
  const [testRoles, setTestRoles] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchTestRoles();
  }, []);

  const fetchTestRoles = async () => {
    try {
      const testRolesResponse = await testingApi.fetchTestRoles();
      const roles = (testRolesResponse?.testRoles || []).map(testRole => ({
        label: testRole.name,
        value: testRole.hexId
      }));
      setTestRoles(roles);
      if (selectFirstByDefault && !selectedRole && roles.length > 0) {
        setSelectedRole(roles[0].value);
      }
    } catch (error) {
    } finally {
      setLoading(false);
    }
  };

  if (!loading && testRoles.length === 0) {
    return (
      <Text variant="bodyMd" color="subdued">
        No test roles found. Create one under Testing → Test roles and configure its authentication.
      </Text>
    );
  }

  return (
    <Dropdown
      menuItems={testRoles}
      label={label}
      initial={selectedRole}
      selected={setSelectedRole}
      helpText={helpText}
    />
  );
}

export default TestRoleDropdown;
