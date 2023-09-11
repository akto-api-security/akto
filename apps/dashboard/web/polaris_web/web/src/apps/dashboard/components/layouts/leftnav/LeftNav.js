import {Navigation, Text} from "@shopify/polaris"
import {HomeMinor,OrdersMinor, CustomersMinor,AnalyticsMinor,DiscountsMinor,ProductsMinor,SettingsMinor} from "@shopify/polaris-icons"
import {useLocation, useNavigate} from "react-router-dom"

import './LeftNav.css'
import PersistStore from "../../../../main/PersistStore"
import { useState } from "react"

export default function LeftNav(){

  const navigate = useNavigate();
  
  const leftNavSelected = PersistStore((state) => state.leftNavSelected)
  const setLeftNavSelected = PersistStore((state) => state.setLeftNavSelected)

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
                  icon: HomeMinor,
                  onClick: ()=>{
                    handleSelect("quick_start")
                    setActive("normal")
                    navigate("/dashboard/quick-start")
                  },
                  selected: leftNavSelected === 'quick_start',
                  key: '1',
                },
                // {
                //   label: 'Dashboard',
                //   icon: OrdersMinor,
                //   onClick: ()=>{
                //     handleSelect("dashboard")
                //     navigate("/dashboard")
                //   },
                //   selected: leftNavSelected === 'dashboard',
                //   key: '2',
                // },
                {   
                  url: '#',
                  label: <Text variant="bodyMd" fontWeight="medium" color={leftNavSelected.includes("inventory") ? (active === 'active' ? "subdued" : ""): ""}>API Inventory</Text>,
                  icon: ProductsMinor,
                  onClick: ()=>{
                    handleSelect("inventory")
                    setActive("normal")
                  },
                  selected: leftNavSelected.includes('inventory'),
                  subNavigationItems:[
                      {
                        label: 'API Collections',
                        onClick: ()=>{
                          navigate('/dashboard/observe/inventory')
                          handleSelect("inventory-collections")
                          setActive('active')
                        },
                        selected: leftNavSelected === "inventory-collections"
                      },
                      {
                        label: 'API Changes',
                        onClick: ()=>{
                          navigate('/dashboard/observe/changes')
                          handleSelect("inventory-changes")
                          setActive('active')
                        },
                        selected: leftNavSelected === "inventory-changes"
                      },
                      {
                        label: 'Sensitive data',
                        onClick: ()=>{
                          navigate('/dashboard/observe/sensitive')
                          handleSelect("inventory-sensitive")
                          setActive('active')
                        },
                        selected: leftNavSelected === "inventory-sensitive"
                      }
                    ],
                    key: '3',
                },
                {
                  url: '#',
                  label: <Text variant="bodyMd" fontWeight="medium" color={leftNavSelected.includes("testing") ? (active === 'active' ? "subdued" : ""): ""}>Testing</Text>,
                  icon: CustomersMinor,
                  onClick: ()=>{
                      handleSelect('testing')
                      setActive("normal")
                  },
                  selected: leftNavSelected.includes('testing'),
                  subNavigationItems:[
                    {
                      label: 'Results',
                      onClick: ()=>{
                        navigate('/dashboard/testing')
                        handleSelect('testing-results')
                        setActive('active')
                      },
                      selected: leftNavSelected === 'testing-results'
                    },
                    {
                      label: 'Test roles',
                      onClick: ()=>{
                        navigate('/dashboard/testing/roles')
                        handleSelect('testing-roles')
                        setActive('active')
                      },
                      selected: leftNavSelected === 'testing-roles'
                    },
                    {
                      label: 'User config',
                      onClick: ()=>{
                        navigate('/dashboard/testing/user-config')
                        handleSelect('testing-config')
                        setActive('active')
                      },
                      selected: leftNavSelected === 'testing-config'
                    }
                  ],
                  key: '4',
                },
                {
                  label: <Text variant="bodyMd" fontWeight="medium">Test Editor</Text>,
                  icon: DiscountsMinor,
                  onClick: ()=>{ 
                    handleSelect("test-editor")
                    navigate("/dashboard/test-editor/REMOVE_TOKENS")
                    setActive("normal")
                  },
                  selected: leftNavSelected === 'test-editor',
                  key: '5',
                },
                {
                  label: <Text variant="bodyMd" fontWeight="medium">Issues</Text>,
                  icon: AnalyticsMinor,
                  onClick: ()=>{ 
                      handleSelect("issues")
                      navigate("/dashboard/issues")
                      setActive("normal")
                    },
                    selected: leftNavSelected === 'issues',
                    key: '6',
                },
              ]}
          />
          <Navigation.Section 
               items={[
                {
                  label:<Text variant="bodyMd" fontWeight="medium">Settings</Text>,
                  icon: SettingsMinor,
                  onClick: ()=>{
                    navigate("/dashboard/settings/about")
                    setActive("normal")
                  },
                  selected: leftNavSelected === 'settings',
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