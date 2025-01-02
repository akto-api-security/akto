import {Navigation, Text} from "@shopify/polaris"
import {
  SettingsFilledIcon,
  AppsFilledIcon,
  InventoryFilledIcon,
  TargetFilledIcon,
  FileFilledIcon,
  ChartVerticalFilledIcon,
  SearchResourceIcon,
  AlertDiamondIcon,
  ChartVerticalIcon,
  AppsIcon,
  InventoryIcon,
  TargetIcon,
  FileIcon,
  SearchRecentIcon,
  CollectionIcon,
  CollectionFilledIcon,
  BlankFilledIcon,
  BlankIcon,
  LiveFilledIcon,
  LiveIcon,
  SettingsIcon
} from "@shopify/polaris-icons";
import {useLocation, useNavigate} from "react-router-dom"

import "./LeftNav.css"
import PersistStore from "../../../../main/PersistStore"
import { useState } from "react"
import func from "@/util/func"
import { useEffect } from "react";


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

  
  useEffect(() => {
    const newPathString = func.transformString(location.pathname);
  
    if (leftNavSelected !== newPathString) {
      setLeftNavSelected(newPathString);
    }
  }, [location.pathname]);
  

  useEffect(() => {
    const isActive =
      leftNavSelected.includes('_observe') || leftNavSelected.includes('_testing');
    setActive(isActive ? "active" : "normal");
  
  }, [leftNavSelected]);


  const PATH_MAPPINGS = {
    observe: {
      inventory: ['dashboard_observe_inventory', 'dashboard_observe_query_mode'],
      changes: ['dashboard_observe_changes'],
      sensitive: ['dashboard_observe_sensitive', 'dashboard_observe_data_types']
    },
    testing: {
      results: ['dashboard_testing', 'dashboard_testing_id'],
      roles: ['dashboard_testing_roles'],
      userConfig: ['dashboard_testing_user_config']
    }
  };

  const isPathSelected = (paths) => {
    return paths.some(path => leftNavSelected.includes(path));
  };
  
  
    const navigationMarkup = (
      <div className={active}>
        <Navigation location="/"> 
          <Navigation.Section
            items={[
              {
                label: <Text variant="bodyMd" fontWeight="medium">Quick Start</Text>,
                icon: (leftNavSelected === 'dashboard_quick_start')? AppsIcon : AppsFilledIcon,
                onClick: ()=>{
                  handleSelect("dashboard_quick_start")
                  setActive("normal")
                  navigate("/dashboard/quick-start")
                },
                selected: leftNavSelected === 'dashboard_quick_start',
                key: '1',
              },
              {
                label:<Text variant="bodyMd" fontWeight="medium">API Security Posture</Text>,
                icon: (leftNavSelected === 'dashboard_home')? ChartVerticalIcon : ChartVerticalFilledIcon,
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
                label: <Text variant="bodyMd" fontWeight="medium" color={leftNavSelected.includes("observe") ? (active === 'active' ? "subdued" : ""): ""}>API Discovery</Text>,
                icon: (leftNavSelected.includes('_observe'))? CollectionIcon : CollectionFilledIcon,
                onClick: ()=>{
                  handleSelect("dashboard_observe_inventory")
                  navigate('/dashboard/observe/inventory')
                  setActive("active")
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
                      selected: isPathSelected(PATH_MAPPINGS.observe.inventory)
                    },
                    {
                      label: 'API Changes',
                      onClick: ()=>{
                        navigate('/dashboard/observe/changes')
                        handleSelect("dashboard_observe_changes")
                        setActive('active')
                      },
                      selected: isPathSelected(PATH_MAPPINGS.observe.changes)
                    },
                    {
                      label: 'Sensitive Data',
                      onClick: ()=>{
                        navigate('/dashboard/observe/sensitive')
                        handleSelect("dashboard_observe_sensitive")
                        setActive('active')
                      },
                      selected: isPathSelected(PATH_MAPPINGS.observe.sensitive)
                    }
                  ],
                  key: '3',
              },
              {
                url: '#',
                label: <Text variant="bodyMd" fontWeight="medium" color={leftNavSelected.includes("testing") ? (active === 'active' ? "subdued" : ""): ""}>Testing</Text>,
                icon: (leftNavSelected.includes('_testing'))? TargetIcon : TargetFilledIcon,
                onClick: ()=>{
                  navigate('/dashboard/testing/')
                  handleSelect('dashboard_testing')
                  setActive("active")
                },
                selected: leftNavSelected.includes('_testing'),
                subNavigationItems:[
                  {
                    label: 'Results',
                    onClick: ()=>{
                      navigate('/dashboard/testing/')
                      handleSelect('dashboard_testing')
                      setActive('active')
                    },
                    selected: leftNavSelected === 'dashboard_testing' || leftNavSelected === 'dashboard_testing_id'
                  },
                  {
                    label: 'Test Roles',
                    onClick: ()=>{
                      navigate('/dashboard/testing/roles')
                      handleSelect('dashboard_testing_roles')
                      setActive('active')
                    },
                    selected: isPathSelected(PATH_MAPPINGS.testing.roles)
                  },
                  {
                    label: 'User Config',
                    onClick: ()=>{
                      navigate('/dashboard/testing/user-config')
                      handleSelect('dashboard_testing_user_config')
                      setActive('active')
                    },
                    selected: isPathSelected(PATH_MAPPINGS.testing.userConfig)
                  }
                ],
                key: '4',
              },
              {
                label: <Text variant="bodyMd" fontWeight="medium">Test Editor</Text>,
                icon: (leftNavSelected.includes("dashboard_test_editor"))? FileIcon : FileFilledIcon,
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
                icon: (leftNavSelected === 'dashboard_issues')? BlankIcon :  BlankFilledIcon,
                onClick: ()=>{ 
                    handleSelect("dashboard_issues")
                    navigate("/dashboard/issues")
                    setActive("normal")
                  },
                  selected: leftNavSelected === 'dashboard_issues',
                  key: '6',
              },
              window?.STIGG_FEATURE_WISE_ALLOWED?.THREAT_DETECTION?.isGranted ?
                {
                  label: <Text variant="bodyMd" fontWeight="medium">API Runtime Threats</Text>,
                  icon: (leftNavSelected === 'dashboard_threat_detection')? LiveIcon : LiveFilledIcon,
                  onClick: () => {
                    handleSelect("dashboard_threat_detection")
                    navigate("/dashboard/threat-detection")
                    setActive("normal")
                  },
                  selected: leftNavSelected === 'dashboard_threat_detection',
                  key: '7',
                } : null
            ].filter(Boolean)}
          />
          <Navigation.Section 
               items={[
                {
                  label:<Text variant="bodyMd" fontWeight="medium">Settings</Text>,
                  icon: (currPathString === 'settings')? SettingsIcon : SettingsFilledIcon,
                  onClick: ()=>{
                    navigate("/dashboard/settings/about")
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

    
