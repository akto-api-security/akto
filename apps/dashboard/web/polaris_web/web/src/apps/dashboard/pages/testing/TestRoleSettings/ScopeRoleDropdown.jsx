import React, { useEffect, useRef, useState } from "react";
import settingRequests from "../../settings/api";
import DropdownSearch from "../../../components/shared/DropdownSearch";
import func from "@/util/func"

const ScopeRoleDropdown = ({ dispatch, initialItems }) => {

  // check if the feature flag is enabled
  // This is a placeholder for the actual feature flag check
  
  if (!func.checkForFeatureSaas("TEST_ROLE_SCOPE_ROLES")) {
     return null;
  }

  let rbacAccess = func.checkForRbacFeatureBasic();
  let rbacAccessAdvanced = func.checkForRbacFeature();
  

  const [customRolesLoading, setCustomRolesLoading] = useState(false);
  const [errorLoadingList, setErrorLoadingList] = useState(false);
  const [selectedRoles, setSelectedRoles] = useState([]);
  const [rolesOptions, setRolesOptions] = useState([]);
  const hasDispatchedOnce = useRef(false);

  useEffect(() => {
    if (initialItems && !hasDispatchedOnce.current) {
      if(initialItems.scopeRoles && initialItems.scopeRoles.length > 0) {
        setSelectedRoles(initialItems.scopeRoles);
        hasDispatchedOnce.current = true;
      }
      else{
        let defaultSelected = rolesOptions.map((item) => item.key);
        setSelectedRoles(defaultSelected);
      }
    }
  }, [initialItems, rolesOptions]);

    const fetchRolesData = async () => {
      setCustomRolesLoading(true);
      try {
          // Fetch role hierarchy
          let rolesList = []
          if (rbacAccessAdvanced) {
              let roleHierarchyResp = await settingRequests.getRoleHierarchy();
              if (roleHierarchyResp.includes("MEMBER")) {
                rolesList.push("SECURITY ENGINEER");
              }

              // Fetch custom roles
              const customRoles = await settingRequests.getCustomRoles();
              if (customRoles.roles) {
                  customRoles.roles.map((x) => {
                      if (roleHierarchyResp.includes(x.baseRole)) {
                        rolesList.push(x.name.toUpperCase());
                      }
                  });
              }
          }

          // Generate rolesOptions
          if(rbacAccess){
            rolesList = ["DEVELOPER", "GUEST", ...rolesList]
          }

          rolesList = ["ADMIN", "SECURITY ENGINEER",...rolesList]

          rolesList = [...new Set(rolesList)];

          const roleOptions = rolesList.map(opt => ({
            key: opt,
            value: opt,
            label: opt
          }));

          setRolesOptions(roleOptions);
      } catch (error) {
          setErrorLoadingList(true);
          console.error("Error fetching roles data:", error);
      }
      finally {
        setCustomRolesLoading(false);
      }
  };

  useEffect(() => {
    fetchRolesData();
  }, [rbacAccess, rbacAccessAdvanced]);

  const updateSelectedRoles = (selectedRole) => {
    dispatch({type:"add", condition: selectedRole });
    setSelectedRoles(selectedRole);
  };


  return (
    <DropdownSearch
      id={"scope-roles-multi-select"}
      label="Scope Roles"
      disabled={customRolesLoading || errorLoadingList}
      placeholder="Select Scope Roles"
      optionsList={rolesOptions}
      preSelected={selectedRoles}
      setSelected={updateSelectedRoles}
      itemName={"scope role"}
      value={`${selectedRoles.length} scope role${
        selectedRoles.length == 1 ? "" : "s"
      } selected`}
      allowMultiple
    />
  );
};

export default ScopeRoleDropdown;
