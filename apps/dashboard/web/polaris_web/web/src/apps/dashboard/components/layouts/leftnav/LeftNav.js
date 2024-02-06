import {Navigation, Text} from "@shopify/polaris"
import {SettingsFilledMinor,AppsFilledMajor, InventoryFilledMajor, MarketingFilledMinor, FileFilledMinor, AnalyticsFilledMinor, OrdersFilledMinor} from "@shopify/polaris-icons"
import {useLocation, useNavigate} from "react-router-dom"

import './LeftNav.css'
import PersistStore from "../../../../main/PersistStore"
import { useState } from "react"
import func from "@/util/func"


export default function LeftNav(){

  const navigate = useNavigate();
  const location = useLocation();
  const currPathString = func.transformString(location.pathname)
  
  const[leftNavSelected, setLeftNavSelected] = useState(currPathString)

  const active = PersistStore((state) => state.active)
  const setActive = PersistStore((state) => state.setActive)

  const handleSelect = (selectedId) => {
    setLeftNavSelected(selectedId);
  };
  
    const navigationMarkup = (
      <div className={active}>
        <Navigation location="/"> 
          <Navigation.Section
            items={[
              {
                label: <Text variant="bodyMd" fontWeight="medium">Quick Start</Text>,
                icon: AppsFilledMajor,
                onClick: ()=>{
                  handleSelect("dashboard_quick_start")
                  setActive("normal")
                  navigate("/dashboard/quick-start")
                },
                selected: leftNavSelected === 'dashboard_quick_start',
                key: '1',
              },
              {
                label: 'Dashboard',
                icon: OrdersFilledMinor,
                onClick: ()=>{
                  handleSelect("dashboard_home")
                  navigate("/dashboard/home")
                  setActive("normal")
                },
                selected: leftNavSelected === 'dashboard_home',
                key: '2',
              },
              {   
                url: '#',
                label: <Text variant="bodyMd" fontWeight="medium" color={leftNavSelected.includes("observe") ? (active === 'active' ? "subdued" : ""): ""}>API Inventory</Text>,
                icon: InventoryFilledMajor,
                onClick: ()=>{
                  handleSelect("_observe")
                  setActive("normal")
                },
                selected: leftNavSelected.includes('_observe'),
                subNavigationItems:[
                    {
                      label: 'API Collections',
                      onClick: ()=>{
                        navigate('/dashboard/observe/inventory')
                        handleSelect("dashboard_observe_inventory")
                        setActive('active')
                      },
                      selected: leftNavSelected === "dashboard_observe_inventory"
                    },
                    {
                      label: 'API Changes',
                      onClick: ()=>{
                        navigate('/dashboard/observe/changes')
                        handleSelect("dashboard_observe_changes")
                        setActive('active')
                      },
                      selected: leftNavSelected === "dashboard_observe_changes"
                    },
                    {
                      label: 'Sensitive data',
                      onClick: ()=>{
                        navigate('/dashboard/observe/sensitive')
                        handleSelect("dashboard_observe_sensitive")
                        setActive('active')
                      },
                      selected: leftNavSelected === "dashboard_observe_sensitive"
                    }
                  ],
                  key: '3',
              },
              {
                url: '#',
                label: <Text variant="bodyMd" fontWeight="medium" color={leftNavSelected.includes("testing") ? (active === 'active' ? "subdued" : ""): ""}>Testing</Text>,
                icon: MarketingFilledMinor,
                onClick: ()=>{
                    handleSelect('_testing')
                    setActive("normal")
                },
                selected: leftNavSelected.includes('_testing'),
                subNavigationItems:[
                  {
                    label: 'Results',
                    onClick: ()=>{
                      navigate('/dashboard/testing')
                      handleSelect('dashboard_testing')
                      setActive('active')
                    },
                    selected: leftNavSelected === 'dashboard_testing'
                  },
                  {
                    label: 'Test roles',
                    onClick: ()=>{
                      navigate('/dashboard/testing/roles')
                      handleSelect('dashboard_testing_roles')
                      setActive('active')
                    },
                    selected: leftNavSelected === 'dashboard_testing_roles'
                  },
                  {
                    label: 'User config',
                    onClick: ()=>{
                      navigate('/dashboard/testing/user-config')
                      handleSelect('dashboard_testing_user_config')
                      setActive('active')
                    },
                    selected: leftNavSelected === 'dashboard_testing_user_config'
                  }
                ],
                key: '4',
              },
              {
                label: <Text variant="bodyMd" fontWeight="medium">Test Editor</Text>,
                icon: FileFilledMinor,
                onClick: ()=>{ 
                  handleSelect("dashboard_test_editor")
                  navigate("/dashboard/test-editor/REMOVE_TOKENS")
                  setActive("normal")
                },
                selected: leftNavSelected.includes("dashboard_test_editor"),
                key: '5',
              },
              {
                label: <Text variant="bodyMd" fontWeight="medium">Issues</Text>,
                icon: AnalyticsFilledMinor,
                onClick: ()=>{ 
                    handleSelect("dashboard_issues")
                    navigate("/dashboard/issues")
                    setActive("normal")
                  },
                  selected: leftNavSelected === 'dashboard_issues',
                  key: '6',
              },
            ]}
          />
          <Navigation.Section 
               items={[
                {
                  label:<Text variant="bodyMd" fontWeight="medium">Settings</Text>,
                  icon: SettingsFilledMinor,
                  onClick: ()=>{
                    navigate("/dashboard/settings/users")
                    setActive("normal")
                  },
                  selected: currPathString === 'settings',
                  key: '7',
                }
              ]}
          />
        </Navigation>
        </div>
      );

    return(
        navigationMarkup
    )
}

    