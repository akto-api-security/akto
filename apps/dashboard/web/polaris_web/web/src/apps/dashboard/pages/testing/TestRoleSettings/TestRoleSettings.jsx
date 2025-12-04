import React, { useEffect, useState, useReducer } from 'react'
import { LegacyCard } from '@shopify/polaris'
import { useLocation, useNavigate } from 'react-router-dom'
import TestRolesConditionsPicker from '../../../components/TestRolesConditionsPicker';
import func from "@/util/func";
import api from '../api';
import transform from '../transform';
import DetailsPage from '../../../components/DetailsPage';
import {produce} from "immer"
import { useSearchParams } from 'react-router-dom';
import TestingStore from '../testingStore';
import DescriptionCard from "./DescriptionCard";
import AuthComponent from "./AuthComponent";
import SavedParamComponent from "./SavedParamComponent";
import { HARDCODED } from "./TestRoleConstants";
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper';

const selectOptions = [
  {
    label: 'contains',
    value: 'CONTAINS'
  },
  {
    label: 'belongs to',
    value: 'BELONGS_TO',
    operators: [
      {
        label: 'OR',
        value: 'OR',
      }
    ],
    type: "MAP"
  },
  {
    label: 'does not belongs to',
    value: 'NOT_BELONGS_TO',
    operators: [{
        label: 'AND',
        value: 'AND',
      }],
    type: "MAP"
  }
]

function TestRoleSettings() {
  const location = useLocation();
  const navigate = useNavigate()
  const [searchParams] = useSearchParams();
  const systemRole = searchParams.get("system")
  

  const isDataInState = location.state && location?.state !== undefined && Object.keys(location?.state).length > 0
  const isDataInSearch = searchParams.get("name")
  const isNew = !isDataInState && !isDataInSearch
  const pageTitle = isNew ? "Add " + mapLabel("test", getDashboardCategory()) + " role" : "Configure " + mapLabel("test", getDashboardCategory()) + " role"
  const [initialItems, setInitialItems] = useState({ name: "" });
  const [conditions, dispatchConditions] = useReducer(
    produce((draft, action) => conditionsReducer(draft, action)),
    []);
  const [scopeRoles, dispatchScopeRoles] = useReducer(
    produce((draft, action) => scopeRolesReducer(draft, action)),
    []
  );
  const [roleName, setRoleName] = useState(systemRole || "");
  const [change, setChange] = useState(false);
  const [showAuthComponent, setShowAuthComponent] = useState(false)
  const [hardcodedOpen, setHardcodedOpen] = useState(true)

  const [refresh, setRefresh] = useState(false)
  const setAuthMechanism = TestingStore.getState().setAuthMechanism
  const [editableDoc, setEditableDocs] = useState(-1)
  const [openAuth, setOpenAuth] = useState(HARDCODED);
  const [advancedHeaderSettingsOpen, setAdvancedHeaderSettingsOpen] = useState(false)

  function getAuthWithCondList() {
    return  initialItems?.authWithCondList
  }

  const resetFunc = (newItems) => {
    setChange(false);
    setRoleName(newItems.name || systemRole || "");
    setAuthMechanism(null)
    dispatchConditions({type:"replace", conditions:transform.createConditions(newItems.endpoints)})
  }
  useEffect(() => {
    if (!isNew) {
      let newItems = initialItems
      if (isDataInState) {
        newItems = location.state
        setInitialItems(location.state);
        resetFunc(newItems)
      } else {
        async function fetchData(){
          await api.fetchTestRoles().then((res) => {
            let testRole = res.testRoles.find((testRole) => testRole.name === searchParams.get("name"));
            if (testRole) {
              let oo = {...testRole, endpoints: testRole.endpointLogicalGroup.testingEndpoints}
              setInitialItems(oo)
              resetFunc(oo)
            } else {
              resetFunc(newItems)
            }
          })
        }
        fetchData();

      }
    } else {
      resetFunc(initialItems)
    }
  }, [refresh])

  useEffect(() => {
    if (func.deepComparison(conditions, transform.createConditions(initialItems.endpoints))) {
      setChange(false);
    } else {
      setChange(true);
    }
  }, [conditions])

  useEffect(() => {
    if (func.deepComparison(scopeRoles, transform.createConditions(initialItems.scopeRoles))) {
      setChange(false);
    } else {
      setChange(true);
    }
  }, [scopeRoles]);

  const compareFunc = () => {
    return !change
  }

  const testRoleMetaInfo = async () => {
    if (!func.checkForFeatureSaas("TEST_ROLE_SCOPE_ROLES")) {
      return;
    }
    await api
    .saveTestRoleMeta(roleName, scopeRoles)
    .then((res) => {
      func.setToast(true, false, "Test role Meta updated successfully.");
    })
    .catch((err) => {
      func.setToast(true, true, "Unable to update test role meta");
      console.log("Error in updating test role meta", err);
    });
  }

  const saveAction = async (updatedAuth = false, authWithCondLists = null) => {
    let andConditions = transform.filterContainsConditions(conditions, "AND");
    let orConditions = transform.filterContainsConditions(conditions, "OR");
    if((roleName !== 'ATTACKER_TOKEN_ALL' && roleName !== 'MCP_AUTHENTICATION_ROLE' && !(andConditions || orConditions)) ||
      roleName.length === 0
    ) {
      func.setToast(true, true, "Please select valid values for a test role");
    } else {
      if (isNew) {
        api.addTestRoles(roleName, andConditions, orConditions).then((res) => {
            func.setToast(true, false, "Test role added")
            setChange(false);
            navigate(null, {
              state: {
                name: roleName,
                endpoints: {
                  andConditions: andConditions,
                  orConditions: orConditions,
                },
                scopeRoles: scopeRoles
              },
              replace: true,
            })
            testRoleMetaInfo();
          }
        )
          .catch((err) => {
            func.setToast(true, true, "Unable to add test role");

          });
      } else {
        await api.updateTestRoles(roleName, andConditions, orConditions).then((res) => {
            setChange(false);
            navigate(null, {
              state: {
                name: roleName,
                endpoints: {
                  andConditions: andConditions,
                  orConditions: orConditions,
                },
                authWithCondList: authWithCondLists || getAuthWithCondList(),
                scopeRoles: scopeRoles
              },
              replace: true,
            });
            testRoleMetaInfo();
          })
          .catch((err) => {
            func.setToast(true, true, "Unable to update test role");
            console.log("Error in updating test role", err);
          });
        if (!updatedAuth) {
          func.setToast(true, false, "Test role updated successfully.");
        }
      }
    }

    setTimeout(() => {
      setRefresh(!refresh)
    },200)
  }

  const handleTextChange = (val) => {
    setRoleName(val);
    if (val == initialItems.name) {
      setChange(false);
    } else {
      setChange(true);
    }
  };

  function conditionsReducer(draft, action){

    switch(action.type){
      case "replace": return action.conditions; break;
      case "add": draft.push(action.condition); break;
      case "update":
        if(action.obj.type){
          if(func.getOption(selectOptions, action.obj.type).type == "MAP"){
            if(func.getOption(selectOptions, draft[action.index].type).type==undefined){
              draft[action.index].value={}
            }
          } else {
            draft[action.index].value=""
          }
          draft[action.index].operator = func.getConditions(selectOptions, action.obj.type)[0].label
        }
        draft[action.index] = {...draft[action.index], ...action.obj}; break;
      case "delete": return draft.filter((item, index) => index !== action.index);
      default: break;
    }
  }

  function scopeRolesReducer(draft, action) {
    switch (action.type) {
      case "add":
        return action.condition;
      default:
        break;
    }
  }

  const descriptionCard = (
    <DescriptionCard
      roleName={roleName}
      systemRole={systemRole}
      isNew={isNew}
      handleTextChange={handleTextChange}
      dispatch={dispatchScopeRoles}
      initialItems={initialItems}
    />
  );

  const conditionsCard = roleName !== 'ATTACKER_TOKEN_ALL' ? (
      <LegacyCard title="Details" key="condition">
        <TestRolesConditionsPicker
          title="Role endpoint conditions"
          param="Endpoint"
          conditions={conditions}
          dispatch={dispatchConditions}
          selectOptions={selectOptions}
        />
      </LegacyCard>
    ) : (<></>)

  const authComponent = (
    <AuthComponent
      showAuthComponent={showAuthComponent}
      setShowAuthComponent={setShowAuthComponent}
      hardcodedOpen={hardcodedOpen}
      setHardcodedOpen={setHardcodedOpen}
      setAuthMechanism={setAuthMechanism}
      editableDoc={editableDoc}
      setEditableDocs={setEditableDocs}
      initialItems={initialItems}
      saveAction={saveAction}
      isNew={isNew}
      openAuth={openAuth}
      setOpenAuth={setOpenAuth}
      advancedHeaderSettingsOpen={advancedHeaderSettingsOpen}
      setAdvancedHeaderSettingsOpen={setAdvancedHeaderSettingsOpen}
    />
  );

  const savedParamComponent = (
    <SavedParamComponent
      getAuthWithCondList={getAuthWithCondList}
      setShowAuthComponent={setShowAuthComponent}
      setEditableDocs={setEditableDocs}
      saveAction={saveAction}
      initialItems={initialItems}
      setAuthMechanism={setAuthMechanism}
      setOpenAuth={setOpenAuth}
      setAdvancedHeaderSettingsOpen={setAdvancedHeaderSettingsOpen}
    />
  );

  let components = [
    descriptionCard,
    conditionsCard,
    authComponent,
    savedParamComponent,
  ];

  return (
    <DetailsPage
      pageTitle={pageTitle}
      backUrl="/dashboard/testing/roles"
      saveAction={saveAction}
      discardAction={() => resetFunc(initialItems)}
      isDisabled={compareFunc}
      components={components}
    />
  )
}

export default TestRoleSettings;